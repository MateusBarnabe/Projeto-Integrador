package br.com.centinela.marketing.comunicacao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * O modelo {@code boas_vindas} do tenant (D7, norteador §4.5 e §9.4): um por tenant, sempre ativo,
 * editável e sem exclusão. Quem chega primeiro, o job 4.5 ou a tela de modelos, cria o padrão.
 */
@Component
public class ModeloDeBoasVindas {

    private final JdbcTemplate jdbc;
    private final ModeloMensagemRepository modelos;

    ModeloDeBoasVindas(JdbcTemplate jdbc, ModeloMensagemRepository modelos) {
        this.jdbc = jdbc;
        this.modelos = modelos;
    }

    /** O modelo ativo do tenant atual, criando o padrão quando ainda não existe. */
    @Transactional
    public ModeloMensagem garantir() {
        Optional<ModeloMensagem> ativo = modelos.findFirstByTipoAndAtivoTrue(TipoModeloMensagem.BOAS_VINDAS);
        if (ativo.isPresent()) {
            return ativo.get();
        }
        // O job e a tela podem chegar juntos no primeiro uso do tenant
        UUID tenant = TenantContexto.exigir();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultado -> null,
                "marketing.boas_vindas:" + tenant);
        return modelos.findFirstByTipoAndAtivoTrue(TipoModeloMensagem.BOAS_VINDAS)
                .orElseGet(() -> modelos.save(ModeloMensagem.boasVindasPadrao()));
    }
}
