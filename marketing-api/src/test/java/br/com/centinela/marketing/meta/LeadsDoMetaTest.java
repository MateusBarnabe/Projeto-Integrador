package br.com.centinela.marketing.meta;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.teste.ModulosSimulados;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.configuracoes.CanalAnuncio;
import br.com.centinela.marketing.configuracoes.CanalAnuncioRepository;
import br.com.centinela.marketing.configuracoes.CanalAnuncioTipo;
import br.com.centinela.marketing.configuracoes.CifradorDeSegredo;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.PontuacaoDeLeads;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.RegraPontuacaoRepository;

/**
 * E4, job 4.1 de ponta a ponta contra o Postgres e a Graph API simulada: o buffer que o webhook encheu
 * vira lead na etapa 0, pontuado com as perguntas do formulário instantâneo.
 */
@ExtendWith(OutputCaptureExtension.class)
class LeadsDoMetaTest extends IntegracaoDeTeste {

    private static final String TOKEN_DA_PAGINA = "EAAtoken-da-pagina";

    @Autowired
    LeadsDoMeta job;

    @Autowired
    LeadEntradaBufferRepository buffer;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    RegraPontuacaoRepository regras;

    @Autowired
    PontuacaoDeLeads pontuacao;

    @Autowired
    CanalAnuncioRepository canais;

    @Autowired
    CifradorDeSegredo cifrador;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void esvaziarAFila() {
        // O banco é único por JVM: linhas pendentes de outras classes (o webhook) entrariam na contagem do job
        jdbc.update("UPDATE marketing.leads_entrada_buffer SET processado = true WHERE processado = false");
    }

    @Test
    void criaOLeadNaEtapa0ComContatoNormalizadoPontuaECompletaALinha(CapturedOutput saida) {
        prepararTenant(TOKEN_DA_PAGINA);
        regraDeCargo("Decisor", 50);
        stubLead("leadgen-1", TOKEN_DA_PAGINA, Map.of(
                "full_name", List.of("  Ana Souza "),
                "email", List.of(" Ana.Souza@Empresa.COM.br "),
                "phone_number", List.of("+55 (62) 99999-8888"),
                "qual_o_seu_cargo", List.of("Decisor"),
                "interesses", List.of("crm", "automação")));
        linhaNoBuffer("leadgen-1");

        LeadsDoMeta.Resultado resultado = executar();

        assertThat(resultado).isEqualTo(new LeadsDoMeta.Resultado(1, 0, 0, 0));
        Lead lead = lead("leadgen-1");
        assertThat(lead.getEtapa()).isZero();
        assertThat(lead.getOrigem()).isEqualTo(OrigemLead.META_ADS);
        assertThat(lead.getNome()).isEqualTo("Ana Souza");
        assertThat(lead.getEmail()).isEqualTo("ana.souza@empresa.com.br");
        assertThat(lead.getTelefone()).isEqualTo("5562999998888");
        assertThat(lead.getCanal()).isNull();
        assertThat(lead.isConsentimentoMarketing()).as("o Meta não traz o aceite (X11)").isFalse();
        assertThat(lead.getCriadoPor()).as("rotina grava sem usuário").isNull();
        assertThat(lead.getPontuacao()).isEqualTo(50);
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_MORNO);
        // As respostas ficam com o lead, sem os campos de contato
        assertThat(lead.getRespostasMeta()).containsOnlyKeys("qual_o_seu_cargo", "interesses")
                .containsEntry("qual_o_seu_cargo", "Decisor")
                .containsEntry("interesses", List.of("crm", "automação"));

        LeadEntradaBuffer linha = linha("leadgen-1");
        assertThat(linha.isProcessado()).isTrue();
        assertThat(linha.getProcessadoEm()).isNotNull();
        assertThat(linha.getTentativas()).isZero();

        List<LeadEvento> timeline = timeline(lead);
        assertThat(timeline).extracting(LeadEvento::getTipoEvento).containsExactly(
                TipoEventoLead.ETAPA_ALTERADA, TipoEventoLead.RESPOSTAS_LIDAS, TipoEventoLead.QUALIFICACAO_ALTERADA);
        assertThat(timeline.get(0).getEtapaAnterior()).isNull();
        assertThat(timeline.get(0).getEtapaNova()).isZero();
        assertThat(timeline.get(1).getPayload()).containsEntry("fonte", LeadsDoMeta.FONTE)
                .containsEntry("respostas", 2).containsEntry("metaLeadId", "leadgen-1");
        // Sem dado pessoal na timeline nem no log
        assertThat(timeline.toString() + saida.getAll())
                .doesNotContain("Ana", "ana.souza", "5562999998888", "Decisor", TOKEN_DA_PAGINA);

        // O token da página vai no cabeçalho, nunca na URL
        ModulosSimulados.META_GRAPH.verify(getRequestedFor(urlPathEqualTo("/v23.0/leadgen-1"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_DA_PAGINA))
                .withoutQueryParam("access_token"));
    }

    @Test
    void novaPontuacaoContinuaSomandoAsRespostasDoMeta() {
        prepararTenant(TOKEN_DA_PAGINA);
        regraDeCargo("Decisor", 50);
        stubLead("leadgen-2", TOKEN_DA_PAGINA, Map.of("email", List.of("bia@empresa.com"),
                "qual_o_seu_cargo", List.of("Decisor")));
        linhaNoBuffer("leadgen-2");
        executar();
        Lead lead = lead("leadgen-2");
        assertThat(lead.getPontuacao()).isEqualTo(50);

        // Quem pontua depois (envio do Landing, job 4.6, tela de qualificação) não passa as respostas
        TenantContexto.executar(tenant, () -> pontuacao.pontuar(leads.findById(lead.getId()).orElseThrow()));

        Lead depois = lead("leadgen-2");
        assertThat(depois.getPontuacao()).as("campo_meta segue somando sem as respostas na chamada").isEqualTo(50);
        assertThat(depois.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_MORNO);
    }

    @Test
    void reentregaDoMesmoLeadAtualizaOLeadSemDuplicar() {
        prepararTenant(TOKEN_DA_PAGINA);
        // O lead já existe com o leadgen_id (a rotina caiu depois de gravar, antes de marcar a linha)
        Lead existente = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.META_ADS, 0);
            lead.vincularMetaLead("leadgen-3");
            return leads.save(lead);
        });
        stubLead("leadgen-3", TOKEN_DA_PAGINA, Map.of("full_name", List.of("Carlos Dias"),
                "email", List.of("carlos@empresa.com")));
        linhaNoBuffer("leadgen-3");

        executar();

        assertThat(contarLeads()).isEqualTo(1);
        Lead lead = lead("leadgen-3");
        assertThat(lead.getId()).isEqualTo(existente.getId());
        assertThat(lead.getNome()).isEqualTo("Carlos Dias");
        assertThat(lead.getEmail()).isEqualTo("carlos@empresa.com");
        assertThat(linha("leadgen-3").isProcessado()).isTrue();
    }

    @Test
    void leadQueJaExistePeloEmailEhCompletadoSemRebaixarEtapaNemSobrescreverDados() {
        prepararTenant(TOKEN_DA_PAGINA);
        regraDeCargo("Decisor", 50);
        Lead doLanding = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.FORMULARIO);
            lead.atualizarContato("Diana Prado", "diana@empresa.com", null, null);
            lead.atualizarUtm("google", "cpc", "black-friday", null, null);
            return leads.save(lead);
        });
        stubLead("leadgen-4", TOKEN_DA_PAGINA, Map.of(
                "full_name", List.of("D. Prado"),
                "email", List.of("DIANA@empresa.com"),
                "phone_number", List.of("62988887777"),
                "qual_o_seu_cargo", List.of("Decisor")));
        linhaNoBuffer("leadgen-4");

        executar();

        assertThat(contarLeads()).as("juntou ao lead que já existia, sem criar outro").isEqualTo(1);
        Lead lead = lead("leadgen-4");
        assertThat(lead.getId()).isEqualTo(doLanding.getId());
        assertThat(lead.getEtapa()).as("a etapa nunca recua").isEqualTo(3);
        assertThat(lead.getOrigem()).isEqualTo(OrigemLead.LANDING_PAGE);
        assertThat(lead.getNome()).as("dado preenchido não é sobrescrito").isEqualTo("Diana Prado");
        assertThat(lead.getTelefone()).as("dado que faltava é completado").isEqualTo("5562988887777");
        assertThat(lead.getMetaLeadId()).isEqualTo("leadgen-4");
        assertThat(lead.getUtmSource()).isEqualTo("google");
        assertThat(lead.getPontuacao()).as("campo_meta soma também no lead do Landing").isEqualTo(50);
        assertThat(timeline(lead)).extracting(LeadEvento::getTipoEvento)
                .contains(TipoEventoLead.RESPOSTAS_LIDAS, TipoEventoLead.QUALIFICACAO_ALTERADA)
                .doesNotContain(TipoEventoLead.LEADS_UNIDOS);
        assertThat(timeline(lead).stream().filter(e -> e.getTipoEvento() == TipoEventoLead.ETAPA_ALTERADA).count())
                .as("nenhuma mudança de etapa registrada pelo Meta").isZero();
    }

    @Test
    void falhaNaGraphApiSomaTentativasAteFalhaPermanenteSemTravarOLote(CapturedOutput saida) {
        prepararTenant(TOKEN_DA_PAGINA);
        stubFalha("leadgen-5", 500);
        stubLead("leadgen-6", TOKEN_DA_PAGINA, Map.of("email", List.of("edu@empresa.com")));
        linhaNoBuffer("leadgen-5");
        linhaNoBuffer("leadgen-6");

        LeadsDoMeta.Resultado primeira = executar();

        assertThat(primeira).isEqualTo(new LeadsDoMeta.Resultado(1, 1, 0, 0));
        assertThat(linha("leadgen-5").getTentativas()).isEqualTo(1);
        assertThat(linha("leadgen-5").isFalhaPermanente()).isFalse();
        assertThat(linha("leadgen-6").isProcessado()).as("o resto do lote seguiu").isTrue();
        assertThat(contarLeads()).isEqualTo(1);

        for (int i = 2; i <= 4; i++) {
            executar();
            assertThat(linha("leadgen-5").getTentativas()).isEqualTo(i);
            assertThat(linha("leadgen-5").isFalhaPermanente()).isFalse();
        }
        LeadsDoMeta.Resultado quinta = executar();
        assertThat(quinta).isEqualTo(new LeadsDoMeta.Resultado(0, 1, 1, 0));
        assertThat(linha("leadgen-5").getTentativas()).isEqualTo(5);
        assertThat(linha("leadgen-5").isFalhaPermanente()).isTrue();
        assertThat(linha("leadgen-5").isProcessado()).isFalse();

        // A mensagem de erro do Meta pode citar o lead: não vai para o log
        assertThat(saida.getAll()).doesNotContain("Ana Souza", TOKEN_DA_PAGINA);

        // Falha permanente sai da fila: a próxima execução nem chama a Graph API
        ModulosSimulados.META_GRAPH.resetRequests();
        assertThat(executar()).isEqualTo(new LeadsDoMeta.Resultado(0, 0, 0, 0));
        assertThat(ModulosSimulados.META_GRAPH.getAllServeEvents()).isEmpty();
    }

    @Test
    void tokenRecusadoELeadInexistenteTambemContamTentativa() {
        prepararTenant(TOKEN_DA_PAGINA);
        stubFalha("leadgen-7", 401);
        stubFalha("leadgen-8", 400);
        linhaNoBuffer("leadgen-7");
        linhaNoBuffer("leadgen-8");

        assertThat(executar()).isEqualTo(new LeadsDoMeta.Resultado(0, 2, 0, 0));

        assertThat(linha("leadgen-7").getTentativas()).isEqualTo(1);
        assertThat(linha("leadgen-8").getTentativas()).isEqualTo(1);
        assertThat(contarLeads()).isZero();
    }

    @Test
    void tenantSemTokenDaPaginaEsperaSemGastarTentativa() {
        prepararTenant(null);
        stubLead("leadgen-9", TOKEN_DA_PAGINA, Map.of("email", List.of("fabio@empresa.com")));
        linhaNoBuffer("leadgen-9");

        LeadsDoMeta.Resultado resultado = executar();

        assertThat(resultado).isEqualTo(new LeadsDoMeta.Resultado(0, 0, 0, 1));
        assertThat(linha("leadgen-9").getTentativas()).isZero();
        assertThat(linha("leadgen-9").isProcessado()).isFalse();
        assertThat(ModulosSimulados.META_GRAPH.getAllServeEvents()).isEmpty();

        // Configurado o token, a mesma linha é processada na execução seguinte
        TenantContexto.executar(tenant, () -> {
            CanalAnuncio canal = canais.findByCanal(CanalAnuncioTipo.META_ADS).orElseThrow();
            canal.atualizarCredenciaisMeta(null, null, null, TOKEN_DA_PAGINA, cifrador);
            canais.save(canal);
        });
        assertThat(executar()).isEqualTo(new LeadsDoMeta.Resultado(1, 0, 0, 0));
        assertThat(lead("leadgen-9").getEmail()).isEqualTo("fabio@empresa.com");
    }

    @Test
    void cadaTenantUsaOProprioTokenEOLeadFicaNoProprioTenant() {
        UUID outroTenant = UUID.randomUUID();
        prepararTenant(TOKEN_DA_PAGINA);
        prepararTenant(outroTenant, "EAAtoken-do-outro-tenant");
        stubLead("leadgen-10", TOKEN_DA_PAGINA, Map.of("email", List.of("gabi@a.com")));
        stubLead("leadgen-11", "EAAtoken-do-outro-tenant", Map.of("email", List.of("hugo@b.com")));
        linhaNoBuffer(tenant, "leadgen-10");
        linhaNoBuffer(outroTenant, "leadgen-11");

        assertThat(executar()).isEqualTo(new LeadsDoMeta.Resultado(2, 0, 0, 0));

        assertThat(contarLeads()).isEqualTo(1);
        assertThat(TenantContexto.calcular(outroTenant, () -> leads.findByMetaLeadId("leadgen-11")).orElseThrow()
                .getEmail()).isEqualTo("hugo@b.com");
        assertThat(TenantContexto.calcular(tenant, () -> leads.findByMetaLeadId("leadgen-11"))).isEmpty();
    }

    @Test
    void leadSemEmailNemTelefoneExisteSoComOMetaLeadId() {
        prepararTenant(TOKEN_DA_PAGINA);
        stubLead("leadgen-12", TOKEN_DA_PAGINA, Map.of("email", List.of("sem-arroba"),
                "phone_number", List.of("123")));
        linhaNoBuffer("leadgen-12");

        executar();

        Lead lead = lead("leadgen-12");
        assertThat(lead.getEmail()).isNull();
        assertThat(lead.getTelefone()).isNull();
        assertThat(lead.getEtapa()).isZero();
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.NUTRICAO);
    }

    @Test
    void anonimizacaoZeraAsRespostasDoMeta() {
        prepararTenant(TOKEN_DA_PAGINA);
        stubLead("leadgen-13", TOKEN_DA_PAGINA, Map.of("email", List.of("iris@empresa.com"),
                "qual_o_seu_cargo", List.of("Decisor")));
        linhaNoBuffer("leadgen-13");
        executar();
        Lead lead = lead("leadgen-13");
        assertThat(lead.getRespostasMeta()).isNotEmpty();

        lead.anonimizar();

        assertThat(lead.getRespostasMeta()).isNull();
    }

    // --- apoio -------------------------------------------------------------------------------------

    private LeadsDoMeta.Resultado executar() {
        return job.processar();
    }

    private void prepararTenant(String tokenDaPagina) {
        prepararTenant(tenant, tokenDaPagina);
    }

    /** Canal meta_ads do tenant, com ou sem o token da página. */
    private void prepararTenant(UUID doTenant, String tokenDaPagina) {
        TenantContexto.executar(doTenant, () -> {
            CanalAnuncio canal = new CanalAnuncio(CanalAnuncioTipo.META_ADS);
            canal.atualizarCredenciaisMeta("4455", "segredo-do-app", "verify-token", tokenDaPagina, cifrador);
            canais.save(canal);
        });
    }

    private void regraDeCargo(String valor, int pontos) {
        TenantContexto.executar(tenant, () -> regras.save(
                RegraPontuacao.porCampoMeta("Cargo", "qual_o_seu_cargo", List.of(valor), pontos)));
    }

    private void linhaNoBuffer(String leadgenId) {
        linhaNoBuffer(tenant, leadgenId);
    }

    private void linhaNoBuffer(UUID doTenant, String leadgenId) {
        TenantContexto.executar(doTenant, () -> buffer.save(new LeadEntradaBuffer(leadgenId,
                Map.of("leadgen_id", leadgenId, "page_id", "4455", "form_id", "7788", "ad_id", "9900"))));
    }

    private LeadEntradaBuffer linha(String leadgenId) {
        return TenantContexto.calcular(tenant, () -> buffer.findByMetaLeadId(leadgenId)).orElseThrow();
    }

    private Lead lead(String leadgenId) {
        return TenantContexto.calcular(tenant, () -> leads.findByMetaLeadId(leadgenId)).orElseThrow();
    }

    private List<LeadEvento> timeline(Lead lead) {
        return TenantContexto.calcular(tenant, () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId()));
    }

    private long contarLeads() {
        return jdbc.queryForObject("SELECT count(*) FROM marketing.leads WHERE tenant_id = ?", Long.class, tenant);
    }

    /** A Graph API devolve o lead só para quem manda o token da página certo. */
    private static void stubLead(String leadgenId, String token, Map<String, List<String>> campos) {
        List<Map<String, Object>> fieldData = campos.entrySet().stream()
                .map(campo -> Map.<String, Object>of("name", campo.getKey(), "values", campo.getValue()))
                .toList();
        ModulosSimulados.META_GRAPH.stubFor(get(urlPathEqualTo("/v23.0/" + leadgenId))
                .withHeader("Authorization", equalTo("Bearer " + token))
                .willReturn(okJson(br.com.centinela.marketing.compartilhado.teste.Envelopes.json(Map.of(
                        "id", leadgenId, "created_time", "2026-11-05T12:00:00+0000", "field_data", fieldData)))));
    }

    private static void stubFalha(String leadgenId, int status) {
        ModulosSimulados.META_GRAPH.stubFor(get(urlPathEqualTo("/v23.0/" + leadgenId))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                        .withBody("{\"error\": {\"message\": \"Mensagem do Meta que cita Ana Souza\", "
                                + "\"type\": \"OAuthException\", \"code\": 190}}")));
    }
}
