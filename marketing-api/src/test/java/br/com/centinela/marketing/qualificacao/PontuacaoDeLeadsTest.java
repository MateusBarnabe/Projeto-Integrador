package br.com.centinela.marketing.qualificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.centinela.marketing.compartilhado.dados.BancoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Pontuação com a matriz inicial das Regras de Negócio (7.1), um lead de cada faixa, só com as
 * regras do formulário do envio, contra o PostgreSQL real.
 */
@Import(PontuacaoDeLeads.class)
class PontuacaoDeLeadsTest extends BancoDeTeste {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    PontuacaoDeLeads pontuacao;

    @Autowired
    RegraPontuacaoRepository regras;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();
    private final UUID formulario = UUID.randomUUID();
    private final UUID cargo = UUID.randomUUID();
    private final UUID orcamento = UUID.randomUUID();
    private final UUID prazo = UUID.randomUUID();

    @BeforeEach
    void matrizInicial() {
        TenantContexto.executar(tenant, () -> {
            regras.save(RegraPontuacao.porCampo("Cargo", formulario, cargo, List.of("Decisor", "Sócio", "Gerente"), 30));
            regras.save(RegraPontuacao.porCampo("Cargo", formulario, cargo,
                    List.of("Analista", "Operacional", "Outros"), 10));
            regras.save(RegraPontuacao.porCampo("Orçamento", formulario, orcamento, List.of("Acima de R$ 50 mil"), 30));
            regras.save(RegraPontuacao.porCampo("Prazo de compra", formulario, prazo,
                    List.of("Imediato", "Até 30 dias"), 25));
            regras.save(RegraPontuacao.porCampo("Prazo de compra", formulario, prazo,
                    List.of("Mais de 3 meses", "Apenas pesquisando"), 5));
            regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                    List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 15));
            regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                    List.of(Map.of("medium", List.of("display", "social"))), 5));

            // Nenhuma destas pode somar
            regras.save(RegraPontuacao.porCampo("Bônus", UUID.randomUUID(), cargo, List.of("Analista", "Decisor"), 50));
            RegraPontuacao inativa = RegraPontuacao.porCampo("Bônus inativo", formulario, cargo,
                    List.of("Analista", "Decisor"), 50);
            inativa.desativar();
            regras.save(inativa);
            RegraPontuacao excluida = regras.save(RegraPontuacao.porCampo("Bônus excluído", formulario, cargo,
                    List.of("Analista", "Decisor"), 50));
            regras.delete(excluida);
        });
        TenantContexto.executar(UUID.randomUUID(), () -> regras.save(RegraPontuacao.porCampo("Bônus de outro tenant",
                formulario, cargo, List.of("Analista", "Decisor"), 50)));
    }

    @Test
    void umLeadDeCadaFaixaComAsRegrasDoFormularioDoEnvio() {
        Lead quente = enviou("Decisor", "Acima de R$ 50 mil", "Imediato", "google", "cpc");
        Lead morno = enviou("Gerente", "Até R$ 10 mil", "Mais de 3 meses", "facebook", "social");
        Lead nutricao = enviou("Analista", "Até R$ 10 mil", "Apenas pesquisando", null, null);

        assertThat(pontuar(quente)).extracting(ResultadoPontuacao::pontuacao, ResultadoPontuacao::faixa)
                .containsExactly(100, FaixaQualificacao.MQL_QUENTE);
        assertThat(pontuar(morno)).extracting(ResultadoPontuacao::pontuacao, ResultadoPontuacao::faixa)
                .containsExactly(40, FaixaQualificacao.MQL_MORNO);
        assertThat(pontuar(nutricao)).extracting(ResultadoPontuacao::pontuacao, ResultadoPontuacao::faixa)
                .as("direto soma 15").containsExactly(30, FaixaQualificacao.NUTRICAO);

        Lead lido = TenantContexto.calcular(tenant, () -> leads.findById(quente.getId())).orElseThrow();
        assertThat(lido.getPontuacao()).isEqualTo(100);
        assertThat(lido.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(lido.getQualificadoEm()).isNotNull();
        assertThat(lido.getPontuacaoDetalhe()).extracting(item -> item.get("criterio"))
                .containsExactlyInAnyOrder("Cargo", "Orçamento", "Prazo de compra", "Origem do tráfego");

        List<LeadEvento> timeline = TenantContexto.calcular(tenant,
                () -> eventos.findByLeadIdOrderBySequenciaAsc(quente.getId()));
        assertThat(timeline).singleElement().satisfies(evento -> {
            assertThat(evento.getTipoEvento()).isEqualTo(TipoEventoLead.QUALIFICACAO_ALTERADA);
            assertThat(evento.getPayload()).containsEntry("faixaNova", "mql_quente").containsEntry("pontuacao", 100);
        });
    }

    @Test
    void mudancaDeFaixaVaiParaATimelineEMesmaFaixaNao() {
        Lead lead = enviou("Decisor", "Acima de R$ 50 mil", "Imediato", "google", "cpc");
        pontuar(lead);

        assertThat(pontuar(recarregar(lead)).faixaMudou()).isFalse();
        jdbc.update("UPDATE marketing.leads SET dados_formulario = '[]'::jsonb WHERE id = ?", lead.getId());
        ResultadoPontuacao rebaixado = pontuar(recarregar(lead));

        assertThat(rebaixado.faixa()).isEqualTo(FaixaQualificacao.NUTRICAO);
        assertThat(rebaixado.faixaAnterior()).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(TenantContexto.calcular(tenant, () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId())))
                .extracting(evento -> evento.getPayload().get("faixaNova"))
                .containsExactly("mql_quente", "nutricao");
    }

    @Test
    void leadDoMetaPontuaComAsPerguntasDoFormularioInstantaneo() {
        TenantContexto.executar(tenant, () -> regras.save(
                RegraPontuacao.porCampoMeta("Cargo", "qual_o_seu_cargo", List.of("Decisor"), 30)));
        Lead doMeta = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.META_ADS, 0);
            lead.vincularMetaLead("leadgen-" + UUID.randomUUID());
            return leads.save(lead);
        });

        ResultadoPontuacao resultado = TenantContexto.calcular(tenant,
                () -> pontuacao.pontuar(doMeta, Map.of("qual_o_seu_cargo", List.of("Decisor"))));

        assertThat(resultado.pontuacao()).as("sem UTM, mas o Meta não é tráfego direto").isEqualTo(30);
    }

    @Test
    void leadDeOutroTenantNaoEhPontuado() {
        Lead lead = enviou("Decisor", "Acima de R$ 50 mil", "Imediato", "google", "cpc");

        assertThatThrownBy(() -> TenantContexto.executar(UUID.randomUUID(), () -> pontuacao.pontuar(lead)))
                .isInstanceOf(IllegalStateException.class);
    }

    private ResultadoPontuacao pontuar(Lead lead) {
        return TenantContexto.calcular(tenant, () -> pontuacao.pontuar(lead));
    }

    private Lead recarregar(Lead lead) {
        return TenantContexto.calcular(tenant, () -> leads.findById(lead.getId())).orElseThrow();
    }

    private Lead enviou(String respostaCargo, String respostaOrcamento, String respostaPrazo, String source,
            String medium) {
        Lead salvo = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvio(UUID.randomUUID(), formulario, CanalLead.FORMULARIO);
            lead.registrarRespostas(List.of(
                    resposta(cargo, "Cargo", respostaCargo),
                    resposta(orcamento, "Orçamento", respostaOrcamento),
                    resposta(prazo, "Prazo de compra", respostaPrazo)), false, AGORA);
            return leads.save(lead);
        });
        // A gravação da UTM é do consumidor do envio (E5); aqui ela vai direto no banco
        jdbc.update("UPDATE marketing.leads SET utm_source = ?, utm_medium = ? WHERE id = ?",
                source, medium, salvo.getId());
        return recarregar(salvo);
    }

    private static Map<String, Object> resposta(UUID campo, String rotulo, String valor) {
        return Map.of("campoId", campo.toString(), "rotulo", rotulo, "tipo", "LISTA_OPCOES", "valor", valor);
    }
}
