package br.com.centinela.marketing.comunicacao;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Consultas em SQL e travas de linha ({@code FOR UPDATE} / {@code FOR UPDATE SKIP LOCKED}) para o
 * descadastro público (E7), a confirmação de boas-vindas (E8, job 4.5), o aquecimento (E9, job 4.2) e
 * a anonimização por retenção (E10, job 4.3).
 */
@Component
class TravasDeComunicacao {

    private final JdbcTemplate jdbc;

    TravasDeComunicacao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record LeadDoToken(UUID tenantId, UUID leadId) {
    }

    /**
     * Acha o tenant e o id do lead pelo {@code token_descadastro}, fora do filtro de tenant do
     * Hibernate (rota pública sem JWT, norteador §6).
     */
    Optional<LeadDoToken> leadPeloTokenDescadastro(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return Optional.empty();
        }
        List<LeadDoToken> encontrados = jdbc.query(
                "SELECT tenant_id, id FROM marketing.leads "
                        + "WHERE token_descadastro = ? AND NOT anonimizado AND deleted_at IS NULL",
                (rs, rowNum) -> new LeadDoToken(rs.getObject("tenant_id", UUID.class), rs.getObject("id", UUID.class)),
                token.strip());
        return encontrados.stream().findFirst();
    }

    /** Trava o lead do tenant atual para o descadastro. */
    boolean travarParaDescadastro(UUID leadId) {
        return !jdbc.queryForList(
                "SELECT id FROM marketing.leads "
                        + "WHERE id = ? AND tenant_id = ? AND NOT anonimizado AND deleted_at IS NULL FOR UPDATE",
                UUID.class, leadId, TenantContexto.exigir()).isEmpty();
    }

    // --- Job 4.5: confirmação de boas-vindas (E8) ------------------------------------------------

    /** Tenants que têm pelo menos um lead na etapa 3 aguardando o e-mail de boas-vindas. */
    List<UUID> tenantsComBoasVindasPendente(int limiteFalhas) {
        return jdbc.queryForList("""
                SELECT DISTINCT l.tenant_id
                  FROM marketing.leads l
                 WHERE l.deleted_at IS NULL
                   AND NOT l.anonimizado
                   AND l.etapa = 3
                   AND l.boas_vindas_enviada_em IS NULL
                   AND l.status_aquecimento <> 'opt_out'
                   AND l.email IS NOT NULL AND l.email <> ''
                   AND (SELECT count(*)
                          FROM marketing.lead_comunicacoes c
                          JOIN marketing.modelos_mensagem m ON m.id = c.modelo_id
                         WHERE c.lead_id = l.id
                           AND c.tenant_id = l.tenant_id
                           AND c.deleted_at IS NULL
                           AND c.status = 'falhou'
                           AND m.tipo = 'boas_vindas') < ?
                """, UUID.class, limiteFalhas);
    }

    /** Leads do tenant atual elegíveis para a confirmação de boas-vindas. */
    List<UUID> leadsComBoasVindasPendente(int limiteFalhas, int lote) {
        return jdbc.queryForList("""
                SELECT l.id
                  FROM marketing.leads l
                 WHERE l.tenant_id = ?
                   AND l.deleted_at IS NULL
                   AND NOT l.anonimizado
                   AND l.etapa = 3
                   AND l.boas_vindas_enviada_em IS NULL
                   AND l.status_aquecimento <> 'opt_out'
                   AND l.email IS NOT NULL AND l.email <> ''
                   AND (SELECT count(*)
                          FROM marketing.lead_comunicacoes c
                          JOIN marketing.modelos_mensagem m ON m.id = c.modelo_id
                         WHERE c.lead_id = l.id
                           AND c.tenant_id = l.tenant_id
                           AND c.deleted_at IS NULL
                           AND c.status = 'falhou'
                           AND m.tipo = 'boas_vindas') < ?
                 ORDER BY l.created_at ASC, l.id ASC
                 LIMIT ?
                """, UUID.class, TenantContexto.exigir(), limiteFalhas, lote);
    }

    boolean travarParaBoasVindas(UUID leadId, int limiteFalhas) {
        return !jdbc.queryForList("""
                SELECT l.id
                  FROM marketing.leads l
                 WHERE l.id = ?
                   AND l.tenant_id = ?
                   AND l.deleted_at IS NULL
                   AND NOT l.anonimizado
                   AND l.etapa = 3
                   AND l.boas_vindas_enviada_em IS NULL
                   AND l.status_aquecimento <> 'opt_out'
                   AND l.email IS NOT NULL AND l.email <> ''
                   AND (SELECT count(*)
                          FROM marketing.lead_comunicacoes c
                          JOIN marketing.modelos_mensagem m ON m.id = c.modelo_id
                         WHERE c.lead_id = l.id
                           AND c.tenant_id = l.tenant_id
                           AND c.deleted_at IS NULL
                           AND c.status = 'falhou'
                           AND m.tipo = 'boas_vindas') < ?
                   FOR UPDATE SKIP LOCKED
                """, UUID.class, leadId, TenantContexto.exigir(), limiteFalhas).isEmpty();
    }

    // --- Job 4.2: comunicação de aquecimento (E9) ------------------------------------------------

    /** Tenants com leads frios (etapa 0 ou 1) ativos, com consentimento e e-mail. */
    List<UUID> tenantsComAquecimentoAtivo() {
        return jdbc.queryForList("""
                SELECT DISTINCT tenant_id
                  FROM marketing.leads
                 WHERE deleted_at IS NULL
                   AND NOT anonimizado
                   AND etapa IN (0, 1)
                   AND status_aquecimento = 'ativo'
                   AND consentimento_marketing = true
                   AND email IS NOT NULL AND email <> ''
                """, UUID.class);
    }

    List<UUID> leadsElegiveisParaAquecimento(int lote) {
        return jdbc.queryForList("""
                SELECT id
                  FROM marketing.leads
                 WHERE tenant_id = ?
                   AND deleted_at IS NULL
                   AND NOT anonimizado
                   AND etapa IN (0, 1)
                   AND status_aquecimento = 'ativo'
                   AND consentimento_marketing = true
                   AND email IS NOT NULL AND email <> ''
                 ORDER BY COALESCE(ultima_comunicacao_em, created_at) ASC, id ASC
                 LIMIT ?
                """, UUID.class, TenantContexto.exigir(), lote);
    }

    boolean travarParaAquecimento(UUID leadId) {
        return !jdbc.queryForList("""
                SELECT id
                  FROM marketing.leads
                 WHERE id = ?
                   AND tenant_id = ?
                   AND deleted_at IS NULL
                   AND NOT anonimizado
                   AND etapa IN (0, 1)
                   AND status_aquecimento = 'ativo'
                   AND consentimento_marketing = true
                   AND email IS NOT NULL AND email <> ''
                   FOR UPDATE SKIP LOCKED
                """, UUID.class, leadId, TenantContexto.exigir()).isEmpty();
    }

    // --- Job 4.3: anonimização por retenção LGPD (E10) -------------------------------------------

    List<UUID> tenantsComLeadsParaRetencao() {
        return jdbc.queryForList("""
                SELECT DISTINCT tenant_id
                  FROM marketing.leads
                 WHERE deleted_at IS NULL
                   AND NOT anonimizado
                   AND status_aquecimento IN ('esgotado', 'opt_out')
                """, UUID.class);
    }

    List<UUID> leadsComPrazoDeRetencaoVencido(OffsetDateTime corteEsgotamento, OffsetDateTime corteOptOut, int lote) {
        return jdbc.queryForList("""
                SELECT id
                  FROM marketing.leads
                 WHERE tenant_id = ?
                   AND deleted_at IS NULL
                   AND NOT anonimizado
                   AND ((status_aquecimento = 'esgotado' AND esgotado_em IS NOT NULL AND esgotado_em <= ?)
                        OR (status_aquecimento = 'opt_out' AND opt_out_em IS NOT NULL AND opt_out_em <= ?))
                 ORDER BY created_at ASC, id ASC
                 LIMIT ?
                """, UUID.class, TenantContexto.exigir(), corteEsgotamento, corteOptOut, lote);
    }

    boolean travarParaAnonimizacao(UUID leadId, OffsetDateTime corteEsgotamento, OffsetDateTime corteOptOut) {
        return !jdbc.queryForList("""
                SELECT id
                  FROM marketing.leads
                 WHERE id = ?
                   AND tenant_id = ?
                   AND deleted_at IS NULL
                   AND NOT anonimizado
                   AND ((status_aquecimento = 'esgotado' AND esgotado_em IS NOT NULL AND esgotado_em <= ?)
                        OR (status_aquecimento = 'opt_out' AND opt_out_em IS NOT NULL AND opt_out_em <= ?))
                   FOR UPDATE SKIP LOCKED
                """, UUID.class, leadId, TenantContexto.exigir(), corteEsgotamento, corteOptOut).isEmpty();
    }
}
