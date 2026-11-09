package br.com.centinela.marketing.qualificacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.qualificacao.EntradaPontuacao.Utm;

class MotorDePontuacaoTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FORMULARIO = UUID.randomUUID();
    private static final UUID CAMPO_CARGO = UUID.randomUUID();
    private static final UUID CAMPO_PRAZO = UUID.randomUUID();
    private static final QualificacaoConfiguracao PADRAO = QualificacaoConfiguracao.padrao();

    @Test
    void criterioRepetidoContaUmaVezPelaRegraDeMaisPontos() {
        List<RegraPontuacao> regras = List.of(
                doTenant(RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO, List.of("Decisor"), 10)),
                doTenant(RegraPontuacao.porCampo("cargo ", FORMULARIO, CAMPO_CARGO, List.of("decisor", "Sócio"), 30)),
                doTenant(RegraPontuacao.porCampo("Prazo", FORMULARIO, CAMPO_PRAZO, List.of("Imediato"), 25)));

        ResultadoPontuacao resultado = calcular(envio(Map.of(CAMPO_CARGO, "Decisor", CAMPO_PRAZO, "Imediato")), regras);

        assertThat(resultado.pontuacao()).isEqualTo(55);
        assertThat(resultado.itens()).extracting(ResultadoPontuacao.ItemPontuacao::pontos).containsExactly(30, 25);
    }

    @Test
    void regraDeOutroFormularioEhIgnorada() {
        RegraPontuacao doOutro = doTenant(RegraPontuacao.porCampo("Cargo", UUID.randomUUID(), CAMPO_CARGO,
                List.of("Decisor"), 30));

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Decisor")), List.of(doOutro)).pontuacao()).isZero();
        assertThat(calcular(semFormulario(Utm.NENHUMA, false), List.of(doOutro)).itens())
                .as("lead sem formulário").isEmpty();
    }

    @Test
    void regraInativaEhIgnorada() {
        RegraPontuacao inativa = doTenant(RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO,
                List.of("Decisor"), 30));
        inativa.desativar();

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Decisor")), List.of(inativa)).pontuacao()).isZero();
    }

    @Test
    void regraDeOutroTenantEhIgnorada() {
        RegraPontuacao alheia = RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO, List.of("Decisor"), 30);
        ReflectionTestUtils.setField(alheia, "tenantId", UUID.randomUUID());
        RegraPontuacao semTenant = RegraPontuacao.porUtm("Origem", List.of(Map.of("direto", true)), 15);

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Decisor")), List.of(alheia, semTenant)).pontuacao()).isZero();
    }

    @Test
    void regraExcluidaEhIgnorada() {
        RegraPontuacao excluida = doTenant(RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO,
                List.of("Decisor"), 30));
        excluida.marcarExcluida();

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Decisor")), List.of(excluida)).pontuacao()).isZero();
    }

    @Test
    void multiplaEscolhaBateSeUmaOpcaoBater() {
        RegraPontuacao regra = doTenant(RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO,
                List.of("Gerente"), 30));

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, List.of("Analista", " gerente "))), List.of(regra)).pontuacao())
                .isEqualTo(30);
        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Analista")), List.of(regra)).pontuacao()).isZero();
    }

    @Test
    void perguntaDoMetaSoValeParaLeadDoMeta() {
        RegraPontuacao regra = doTenant(RegraPontuacao.porCampoMeta("Cargo", "qual_o_seu_cargo",
                List.of("Decisor"), 30));
        Map<String, Object> respostas = Map.of("Qual_o_seu_cargo", List.of("Decisor"));

        EntradaPontuacao doMeta = new EntradaPontuacao(TENANT, null, Map.of(), true, respostas, Utm.NENHUMA);
        EntradaPontuacao daLanding = new EntradaPontuacao(TENANT, null, Map.of(), false, respostas, Utm.NENHUMA);

        assertThat(calcular(doMeta, List.of(regra)).pontuacao()).isEqualTo(30);
        assertThat(calcular(daLanding, List.of(regra)).pontuacao()).isZero();
    }

    @Test
    void utmBateQuandoTodasAsChavesBatem() {
        RegraPontuacao fundo = doTenant(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 15));
        RegraPontuacao topo = doTenant(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("utm_medium", List.of("display", "social"))), 5));
        List<RegraPontuacao> regras = List.of(fundo, topo);

        assertThat(calcular(semFormulario(utm("Google", "CPC"), false), regras).pontuacao()).isEqualTo(15);
        assertThat(calcular(semFormulario(utm("google", "organic"), false), regras).pontuacao()).isZero();
        assertThat(calcular(semFormulario(utm("facebook", "social"), false), regras).pontuacao()).isEqualTo(5);
        assertThat(calcular(semFormulario(Utm.NENHUMA, false), regras).pontuacao()).as("direto").isEqualTo(15);
        assertThat(calcular(semFormulario(Utm.NENHUMA, true), regras).pontuacao())
                .as("lead do Meta sem UTM não é tráfego direto").isZero();
    }

    @Test
    void condicaoDeUtmVaziaOuComChaveDesconhecidaNaoPontua() {
        RegraPontuacao regra = doTenant(RegraPontuacao.porUtm("Origem",
                List.of(Map.of(), Map.of("fonte", "google"), "google"), 15));

        assertThat(calcular(semFormulario(utm("google", "cpc"), false), List.of(regra)).pontuacao()).isZero();
    }

    @Test
    void faixaSegueOsLimitesDoTenant() {
        assertThat(faixaCom(70)).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(faixaCom(69)).isEqualTo(FaixaQualificacao.MQL_MORNO);
        assertThat(faixaCom(40)).isEqualTo(FaixaQualificacao.MQL_MORNO);
        assertThat(faixaCom(39)).isEqualTo(FaixaQualificacao.NUTRICAO);

        QualificacaoConfiguracao exigente = QualificacaoConfiguracao.padrao();
        exigente.definirLimites(90, 60);
        RegraPontuacao regra = doTenant(RegraPontuacao.porUtm("Origem", List.of(Map.of("direto", true)), 70));
        assertThat(MotorDePontuacao.calcular(semFormulario(Utm.NENHUMA, false), List.of(regra), exigente).faixa())
                .isEqualTo(FaixaQualificacao.MQL_MORNO);
    }

    @Test
    void semRegraQueBataFicaComZeroEmNutricao() {
        ResultadoPontuacao resultado = calcular(envio(Map.of()), List.of());

        assertThat(resultado.pontuacao()).isZero();
        assertThat(resultado.faixa()).isEqualTo(FaixaQualificacao.NUTRICAO);
        assertThat(resultado.detalhe()).isEmpty();
    }

    @Test
    void detalheTrazARegraEOAlvoSemAResposta() {
        RegraPontuacao regra = doTenant(RegraPontuacao.porCampo("Cargo", FORMULARIO, CAMPO_CARGO,
                List.of("Decisor"), 30));

        assertThat(calcular(envio(Map.of(CAMPO_CARGO, "Decisor")), List.of(regra)).detalhe()).singleElement()
                .satisfies(item -> {
                    assertThat(item).containsEntry("criterio", "Cargo").containsEntry("fonte", "campo_formulario")
                            .containsEntry("pontos", 30).containsEntry("campoId", CAMPO_CARGO.toString());
                    assertThat(item.values()).doesNotContain("Decisor");
                });
    }

    @Test
    void regrasDeComportamentoComparamMinimoTrechoEIgualdade() {
        List<RegraPontuacao> regras = List.of(
                doTenant(RegraPontuacao.porComportamento("Engajamento", MetricaComportamento.TEMPO_NA_PAGINA,
                        List.of(Map.of("minimo", 60)), 20)),
                doTenant(RegraPontuacao.porComportamento("Rolagem", MetricaComportamento.PROFUNDIDADE_ROLAGEM,
                        List.of(Map.of("minimo", 90)), 5)),
                doTenant(RegraPontuacao.porComportamento("Interesse em preço", MetricaComportamento.PAGINA_VISITADA,
                        List.of("Preços", "precos"), 10)),
                doTenant(RegraPontuacao.porComportamento("Celular", MetricaComportamento.TIPO_DISPOSITIVO,
                        List.of("smartphone"), 5)));
        Map<String, Object> interacao = Map.of("tempoNaPaginaSegundos", 60, "profundidadeRolagem", 75,
                "paginasVisitadas", List.of("/", "/#precos"), "tipoDispositivo", "smartphone");

        ResultadoPontuacao resultado = calcular(new EntradaPontuacao(TENANT, null, Map.of(), false, Map.of(),
                Utm.NENHUMA, interacao), regras);

        assertThat(resultado.itens()).extracting(ResultadoPontuacao.ItemPontuacao::criterio)
                .containsExactly("Engajamento", "Interesse em preço", "Celular");
        assertThat(resultado.pontuacao()).isEqualTo(35);
        assertThat(resultado.detalhe().getFirst()).containsEntry("fonte", "comportamento")
                .containsEntry("campoComportamento", "tempo_na_pagina");
        assertThat(calcular(semFormulario(Utm.NENHUMA, false), regras).itens()).as("lead sem visita").isEmpty();
    }

    private static FaixaQualificacao faixaCom(int pontos) {
        RegraPontuacao regra = doTenant(RegraPontuacao.porUtm("Origem", List.of(Map.of("direto", true)), pontos));
        return calcular(semFormulario(Utm.NENHUMA, false), List.of(regra)).faixa();
    }

    private static ResultadoPontuacao calcular(EntradaPontuacao entrada, List<RegraPontuacao> regras) {
        return MotorDePontuacao.calcular(entrada, regras, PADRAO);
    }

    private static EntradaPontuacao envio(Map<UUID, Object> respostas) {
        return new EntradaPontuacao(TENANT, FORMULARIO, respostas, false, Map.of(), Utm.NENHUMA);
    }

    private static EntradaPontuacao semFormulario(Utm utm, boolean doMeta) {
        return new EntradaPontuacao(TENANT, null, Map.of(), doMeta, Map.of(), utm);
    }

    private static Utm utm(String source, String medium) {
        return new Utm(source, medium, null, null, null);
    }

    // O tenant é gravado pelo @PrePersist; aqui as regras não passam pelo banco
    private static RegraPontuacao doTenant(RegraPontuacao regra) {
        ReflectionTestUtils.setField(regra, "tenantId", TENANT);
        return regra;
    }
}
