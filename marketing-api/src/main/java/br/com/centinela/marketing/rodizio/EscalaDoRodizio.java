package br.com.centinela.marketing.rodizio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.seguranca.ContextoSeguranca;
import br.com.centinela.marketing.compartilhado.seguranca.UsuarioAtual;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Travas do rodízio no PostgreSQL. Ficam em SQL (pelo {@link JdbcTemplate}, na mesma conexão da
 * transação JPA) por dois motivos: {@code FOR UPDATE SKIP LOCKED} não tem equivalente no JPA, e uma
 * consulta nativa do Hibernate descarregaria antes da hora as mudanças que quem chama ainda não
 * gravou. Toda consulta filtra pelo tenant do contexto, porque o filtro do Hibernate não vale aqui;
 * a única exceção é {@link #tenantsComPendentes()}, que serve para o job descobrir os tenants.
 */
@Component
class EscalaDoRodizio {

    /** Lead que entra no rodízio e ainda não tem vendedor: o "aguardando vendedor" (§9.3). */
    static final String AGUARDANDO_VENDEDOR = """
            vendedor_id IS NULL
            AND faixa_qualificacao IN ('mql_quente', 'mql_morno')
            AND deleted_at IS NULL
            AND anonimizado = false
            AND coalesce(nullif(btrim(email), ''), nullif(btrim(telefone), ''), nullif(btrim(whatsapp), '')) IS NOT NULL
            """;

    private static final String PROXIMO_VENDEDOR = """
            SELECT id, vendedor_id FROM marketing.vendedores_rodizio
             WHERE tenant_id = ? AND ativo AND deleted_at IS NULL
             ORDER BY ultima_atribuicao_em ASC NULLS FIRST, created_at, id
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    EscalaDoRodizio(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Trava a linha do lead até o fim da transação e devolve o vendedor que está gravado no banco.
     * Vazio quando o lead ainda não foi gravado (só existe nesta transação, ninguém mais o vê).
     */
    Optional<VendedorGravado> travarLead(UUID leadId) {
        return jdbc.query("""
                SELECT vendedor_id, vendedor_atribuido_em FROM marketing.leads
                 WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL
                   FOR UPDATE
                """,
                (linha, n) -> new VendedorGravado(linha.getObject("vendedor_id", UUID.class),
                        linha.getObject("vendedor_atribuido_em", OffsetDateTime.class)),
                leadId, TenantContexto.exigir()).stream().findFirst();
    }

    /**
     * Trava e devolve o vendedor ativo que recebeu lead há mais tempo (nunca recebeu vem primeiro).
     * Pula o que outra transação está atribuindo agora; se só sobrar ele, espera por ele.
     */
    Optional<VendedorDaEscala> travarProximoVendedor() {
        UUID tenant = TenantContexto.exigir();
        Optional<VendedorDaEscala> livre = proximo(PROXIMO_VENDEDOR + " FOR UPDATE SKIP LOCKED", tenant);
        return livre.isPresent() ? livre : proximo(PROXIMO_VENDEDOR + " FOR UPDATE", tenant);
    }

    /** O vendedor acabou de receber um lead: vai para o fim da fila. */
    void registrarAtribuicao(UUID idNaEscala, OffsetDateTime em) {
        UUID usuario = ContextoSeguranca.usuarioAtual().map(UsuarioAtual::usuarioId).orElse(null);
        jdbc.update("""
                UPDATE marketing.vendedores_rodizio
                   SET ultima_atribuicao_em = ?, updated_at = now(), updated_by = ?
                 WHERE id = ? AND tenant_id = ?
                """, em, usuario, idNaEscala, TenantContexto.exigir());
    }

    /** Trava até {@code limite} leads aguardando vendedor, os mais antigos primeiro, pulando os já travados. */
    List<UUID> travarPendentes(int limite) {
        return jdbc.queryForList("SELECT id FROM marketing.leads WHERE tenant_id = ? AND " + AGUARDANDO_VENDEDOR
                + " ORDER BY qualificado_em, id LIMIT ? FOR UPDATE SKIP LOCKED",
                UUID.class, TenantContexto.exigir(), limite);
    }

    /** Tenants com algum lead aguardando vendedor; o job percorre só estes. */
    List<UUID> tenantsComPendentes() {
        return jdbc.queryForList("SELECT DISTINCT tenant_id FROM marketing.leads WHERE " + AGUARDANDO_VENDEDOR,
                UUID.class);
    }

    private Optional<VendedorDaEscala> proximo(String sql, UUID tenant) {
        return jdbc.query(sql, (linha, n) -> new VendedorDaEscala(linha.getObject("id", UUID.class),
                linha.getObject("vendedor_id", UUID.class)), tenant).stream().findFirst();
    }

    /** Vendedor que está gravado no lead agora, no banco. */
    record VendedorGravado(UUID vendedorId, OffsetDateTime atribuidoEm) {
    }

    /**
     * @param id         linha de {@code vendedores_rodizio}
     * @param vendedorId usuário do identity
     */
    record VendedorDaEscala(UUID id, UUID vendedorId) {
    }
}
