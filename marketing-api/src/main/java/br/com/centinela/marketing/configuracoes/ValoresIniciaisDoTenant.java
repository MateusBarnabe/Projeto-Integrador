package br.com.centinela.marketing.configuracoes;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracao;
import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracaoRepository;
import br.com.centinela.marketing.comunicacao.ModeloDeBoasVindas;
import br.com.centinela.marketing.comunicacao.ModeloMensagem;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.RegrasIniciais;

/**
 * Valores iniciais por tenant (D7 e R5, norteador §3 e §9.3), criados na primeira vez que o tenant
 * usa o módulo:
 * <ul>
 * <li>modelo de boas-vindas padrão ({@link ModeloDeBoasVindas}, também usado pelo job 4.5);</li>
 * <li>configuração de ciclo de vida ({@code ciclo_vida_configuracoes});</li>
 * <li>configuração de qualificação ({@code qualificacao_configuracoes});</li>
 * <li>regras de pontuação iniciais de {@code utm} (matriz 9.3) e de {@code comportamento} (R5)
 * ({@link RegrasIniciais}, também usado na primeira pontuação de um lead);</li>
 * <li>os dois canais de anúncio ({@code google_ads} e {@code meta_ads}).</li>
 * </ul>
 *
 * <p>Regras que a pessoa gestora excluiu não são recriadas. O modelo de boas-vindas não pode ser
 * excluído (§9.4).
 */
@Service
public class ValoresIniciaisDoTenant {

    private final ModeloDeBoasVindas boasVindas;
    private final RegrasIniciais regrasIniciais;
    private final CicloVidaConfiguracaoRepository ciclos;
    private final QualificacaoConfiguracaoRepository qualificacoes;
    private final CanalAnuncioRepository canais;

    public ValoresIniciaisDoTenant(ModeloDeBoasVindas boasVindas, RegrasIniciais regrasIniciais,
            CicloVidaConfiguracaoRepository ciclos, QualificacaoConfiguracaoRepository qualificacoes,
            CanalAnuncioRepository canais) {
        this.boasVindas = boasVindas;
        this.regrasIniciais = regrasIniciais;
        this.ciclos = ciclos;
        this.qualificacoes = qualificacoes;
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
        return boasVindas.garantir();
    }

    @Transactional
    public CicloVidaConfiguracao garantirCicloVida() {
        return ciclos.findFirstBy().orElseGet(() -> ciclos.save(CicloVidaConfiguracao.padrao()));
    }

    @Transactional
    public QualificacaoConfiguracao garantirQualificacao() {
        return qualificacoes.findFirstBy().orElseGet(() -> qualificacoes.save(QualificacaoConfiguracao.padrao()));
    }

    @Transactional
    public void garantirRegrasIniciais() {
        regrasIniciais.garantir();
    }
}
