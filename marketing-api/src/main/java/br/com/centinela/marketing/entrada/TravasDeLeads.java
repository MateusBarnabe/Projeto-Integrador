package br.com.centinela.marketing.entrada;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import jakarta.persistence.EntityManager;

/**
 * Travas de linha dos leads na entrada do envio (E5), em SQL pelo {@link JdbcTemplate} na mesma
 * conexão da transação JPA, como o rodízio ({@code EscalaDoRodizio}). Toda consulta filtra pelo
 * tenant do contexto, porque o filtro do Hibernate não vale aqui.
 *
 * <p>O consumo do envio e o job 4.6 podem pegar o mesmo lead (o lead que a E6 criou antes do envio
 * fica com a leitura pendente): quem chega depois espera a trava e relê o lead, para não gravar por
 * cima o que o outro acabou de gravar.
 */
@Component
class TravasDeLeads {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    TravasDeLeads(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    /**
     * Trava a linha do lead até o fim da transação (esperando quem estiver com ela) e relê o lead do
     * banco. Chame antes de mudar o lead: o que ele tiver de não gravado se perde na releitura.
     */
    void travar(Lead lead) {
        jdbc.query("SELECT id FROM marketing.leads WHERE id = ? AND tenant_id = ? FOR UPDATE",
                (linha, n) -> linha.getObject("id", UUID.class), lead.getId(), TenantContexto.exigir());
        entityManager.refresh(lead);
    }

    // --- job 4.6 (E11) ---------------------------------------------------------------------------

    /** Leitura do envio pendente e ainda não abandonada (§4.6); o índice parcial da V10 cobre. */
    static final String LEITURA_PENDENTE = """
            envio_id IS NOT NULL
            AND envio_lido_em IS NULL
            AND leitura_envio_desistida_em IS NULL
            AND deleted_at IS NULL
            AND anonimizado = false
            """;

    /** Tenants com algum lead com a leitura do envio pendente; o job percorre só estes. */
    List<UUID> tenantsComLeituraPendente() {
        return jdbc.queryForList("SELECT DISTINCT tenant_id FROM marketing.leads WHERE " + LEITURA_PENDENTE,
                UUID.class);
    }

    /**
     * Até {@code limite} leads do tenant com a leitura pendente, sem travar: os com menos falhas
     * primeiro, para um envio que não responde não tomar a vez dos que chegaram depois.
     */
    List<UUID> comLeituraPendente(int limite) {
        return jdbc.queryForList("SELECT id FROM marketing.leads WHERE tenant_id = ? AND " + LEITURA_PENDENTE
                + " ORDER BY leitura_envio_falhas, created_at, id LIMIT ?", UUID.class, TenantContexto.exigir(),
                limite);
    }

    /**
     * Trava o lead se ele ainda está com a leitura pendente e ninguém está com ele (outra execução do
     * job, outra instância ou o consumo do envio): {@code FOR UPDATE SKIP LOCKED}.
     *
     * @return {@code false} se outra transação está com o lead ou se a leitura já não está pendente
     */
    boolean travarComLeituraPendente(UUID leadId) {
        return !jdbc.queryForList("SELECT id FROM marketing.leads WHERE id = ? AND tenant_id = ? AND "
                + LEITURA_PENDENTE + " FOR UPDATE SKIP LOCKED", UUID.class, leadId, TenantContexto.exigir())
                .isEmpty();
    }
}
