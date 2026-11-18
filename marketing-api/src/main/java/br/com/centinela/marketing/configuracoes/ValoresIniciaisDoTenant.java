package br.com.centinela.marketing.configuracoes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracao;
import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracaoRepository;
import br.com.centinela.marketing.comunicacao.ModeloMensagem;
import br.com.centinela.marketing.comunicacao.ModeloMensagemRepository;
import br.com.centinela.marketing.qualificacao.MetricaComportamento;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.RegraPontuacaoRepository;

/**
 * Valores iniciais por tenant (D7 e R5, norteador §3 e §9.3), criados na primeira vez que o tenant
 * usa o módulo:
 * <ul>
 * <li>modelo de boas-vindas padrão ({@code modelos_mensagem});</li>
 * <li>configuração de ciclo de vida ({@code ciclo_vida_configuracoes});</li>
 * <li>configuração de qualificação ({@code qualificacao_configuracoes});</li>
 * <li>regras de pontuação iniciais de {@code utm} (matriz 9.3) e de {@code comportamento} (R5);</li>
 * <li>os dois canais de anúncio ({@code google_ads} e {@code meta_ads}).</li>
 * </ul>
 *
 * <p>Confere o banco incluindo registros excluídos ({@code deleted_at IS NOT NULL}) nas tabelas em
 * que a pessoa gestora pode apagar itens (regras e modelos), para não recriar o que ela já excluiu.
 */
@Service
public class ValoresIniciaisDoTenant {

    private final JdbcTemplate jdbc;
    private final ModeloMensagemRepository modelos;
    private final CicloVidaConfiguracaoRepository ciclos;
    private final QualificacaoConfiguracaoRepository qualificacoes;
    private final RegraPontuacaoRepository regras;
    private final CanalAnuncioRepository canais;

    public ValoresIniciaisDoTenant(JdbcTemplate jdbc, ModeloMensagemRepository modelos,
            CicloVidaConfiguracaoRepository ciclos, QualificacaoConfiguracaoRepository qualificacoes,
            RegraPontuacaoRepository regras, CanalAnuncioRepository canais) {
        this.jdbc = jdbc;
        this.modelos = modelos;
        this.ciclos = ciclos;
        this.qualificacoes = qualificacoes;
        this.regras = regras;
        this.canais = canais;
    }

    @Transactional
    public void garantirCanaisAnuncio() {
        for (CanalAnuncioTipo tipo : CanalAnuncioTipo.values()) {
            if (canais.findByCanal(tipo).isEmpty()) {
                canais.save(new CanalAnuncio(tipo));
            }
        }
    }

    @Transactional
    public ModeloMensagem garantirBoasVindas() {
        UUID tenant = TenantContexto.exigir();
        Integer existentes = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.modelos_mensagem WHERE tenant_id = ? AND tipo = 'boas_vindas'",
                Integer.class, tenant);
        if (existentes != null && existentes > 0) {
            return modelos.findFirstByTipoAndAtivoTrue(
                    br.com.centinela.marketing.comunicacao.TipoModeloMensagem.BOAS_VINDAS)
                    .orElseGet(() -> modelos.save(ModeloMensagem.boasVindasPadrao()));
        }
        return modelos.save(ModeloMensagem.boasVindasPadrao());
    }

    @Transactional
    public CicloVidaConfiguracao garantirCicloVida() {
        return ciclos.findFirstBy().orElseGet(() -> ciclos.save(CicloVidaConfiguracao.padrao()));
    }

    @Transactional
    public QualificacaoConfiguracao garantirQualificacao() {
        return qualificacoes.findFirstBy().orElseGet(() -> qualificacoes.save(QualificacaoConfiguracao.padrao()));
    }

    /**
     * Na primeira vez que o tenant consulta ou usa as regras pela API, cria as regras iniciais de
     * {@code utm} (matriz da §9.3) e de {@code comportamento} (R5, lead 0). Se o tenant já teve
     * alguma regra (mesmo excluída), não recria.
     */
    @Transactional
    public void garantirRegrasIniciais() {
        UUID tenant = TenantContexto.exigir();
        Integer totalHistorico = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.regras_pontuacao WHERE tenant_id = ?",
                Integer.class, tenant);
        if (totalHistorico != null && totalHistorico > 0) {
            return;
        }
        // Matriz 9.3: Origem do tráfego (utm)
        regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 15));
        regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("medium", List.of("display", "social"))), 5));

        // R5: Comportamento da visita de anúncio (lead 0)
        regras.save(RegraPontuacao.porComportamento("Engajamento na página",
                MetricaComportamento.TEMPO_NA_PAGINA, List.of(Map.of("minimo", 30)), 20));
        regras.save(RegraPontuacao.porComportamento("Interesse em preço",
                MetricaComportamento.PAGINA_VISITADA, List.of("precos"), 15));
        regras.save(RegraPontuacao.porComportamento("Simulou o investimento",
                MetricaComportamento.ELEMENTO_CLICADO, List.of("simulador-preco"), 10));
        regras.save(RegraPontuacao.porComportamento("Leu a página inteira",
                MetricaComportamento.PROFUNDIDADE_ROLAGEM, List.of(Map.of("minimo", 90)), 5));
    }
}
