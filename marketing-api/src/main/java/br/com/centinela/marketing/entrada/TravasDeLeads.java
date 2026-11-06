package br.com.centinela.marketing.entrada;

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
}
