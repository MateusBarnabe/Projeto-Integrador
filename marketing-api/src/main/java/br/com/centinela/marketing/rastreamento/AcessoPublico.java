package br.com.centinela.marketing.rastreamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Consultas em SQL pelo {@link JdbcTemplate} que o filtro de tenant do Hibernate não cobre: achar o
 * tenant pelo código do link ou pelo id da visita (rotas públicas, sem token, norteador §6), travar a
 * visita e o descarte do job 4.7. As que rodam dentro de um tenant filtram o {@code tenant_id} à mão.
 */
@Component
class AcessoPublico {

    private final JdbcTemplate jdbc;

    AcessoPublico(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tenant de um link ativo; vazio se o código não existe, foi excluído ou está desligado. */
    Optional<UUID> tenantDoLink(String codigo) {
        if (codigo == null || codigo.length() > 16) {
            return Optional.empty();
        }
        List<UUID> tenants = jdbc.queryForList("SELECT tenant_id FROM marketing.links_campanha "
                + "WHERE codigo = ? AND ativo AND deleted_at IS NULL", UUID.class, codigo);
        return tenants.stream().findFirst();
    }

    /** Tenant de uma visita; vazio se ela não existe. */
    Optional<UUID> tenantDaVisita(UUID visitaId) {
        List<UUID> tenants = jdbc.queryForList("SELECT tenant_id FROM marketing.visitas_anuncio "
                + "WHERE id = ? AND deleted_at IS NULL", UUID.class, visitaId);
        return tenants.stream().findFirst();
    }

    /** Id do link pelo código, no tenant do contexto. */
    Optional<UUID> linkPeloCodigo(String codigo) {
        return jdbc.queryForList("SELECT id FROM marketing.links_campanha WHERE codigo = ? AND tenant_id = ? "
                + "AND deleted_at IS NULL", UUID.class, codigo, TenantContexto.exigir()).stream().findFirst();
    }

    /**
     * Trava a visita ativa do tenant do contexto até o fim da transação, esperando quem estiver com ela
     * (outro sinal, o vínculo com o lead ou o descarte).
     *
     * @return {@code false} se a visita não é do tenant ou já não está ativa
     */
    boolean travarAtiva(UUID visitaId) {
        return !jdbc.queryForList("SELECT id FROM marketing.visitas_anuncio WHERE id = ? AND tenant_id = ? "
                + "AND situacao = 'ativa' AND deleted_at IS NULL FOR UPDATE", UUID.class, visitaId,
                TenantContexto.exigir()).isEmpty();
    }

    /**
     * Job 4.7: descarta até {@code lote} visitas ativas vencidas, de todos os tenants: zera o contexto
     * técnico e o comportamento e mantém a linha para as contagens. Pula as travadas (um sinal ou um
     * vínculo em andamento), que ficam para a próxima execução.
     *
     * @return quantas foram descartadas
     */
    int descartarVencidas(int lote) {
        return jdbc.update("""
                UPDATE marketing.visitas_anuncio
                   SET situacao = 'descartada', descartada_em = now(), updated_at = now(),
                       contexto_tecnico = NULL, comportamento = NULL
                 WHERE id IN (SELECT id FROM marketing.visitas_anuncio
                               WHERE situacao = 'ativa' AND deleted_at IS NULL AND expira_em < now()
                               ORDER BY expira_em
                               LIMIT ?
                               FOR UPDATE SKIP LOCKED)
                """, lote);
    }
}
