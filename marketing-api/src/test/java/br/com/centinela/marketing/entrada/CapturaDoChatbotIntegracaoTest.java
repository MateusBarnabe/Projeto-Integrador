package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.mensageria.TopologiaDeConsumo;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.Temperatura;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.RegraPontuacaoRepository;

/**
 * E1 de ponta a ponta: o "Landing" publica {@code landing.chatbot.lead_capturado} como {@code mq_landing}
 * em {@code landing.eventos}, no formato do {@code landing.asyncapi.yaml}, e o consumidor real da fila
 * {@code marketing.landing-leads} grava o lead, o consentimento, a timeline e a pontuação.
 */
@ExtendWith(OutputCaptureExtension.class)
class CapturaDoChatbotIntegracaoTest extends IntegracaoDeTeste {

    private static final Duration ESPERA = Duration.ofSeconds(15);
    private static final OffsetDateTime ACEITO_EM = OffsetDateTime.of(2026, 10, 1, 22, 13, 40, 0, ZoneOffset.UTC);
    private static final String DLQ = TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS);

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitDaApi;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadConsentimentoRepository consentimentos;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    RegraPontuacaoRepository regras;

    private final UUID tenant = UUID.randomUUID();
    private final UUID formulario = UUID.randomUUID();

    private CachingConnectionFactory conexaoLanding;
    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate landing;

    @BeforeEach
    void preparar() {
        rabbitDaApi.execute(canal -> null); // filas declaradas pela API
        conexaoLanding = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexaoLanding.setUsername("mq_landing");
        conexaoLanding.setPassword("mq_landing");
        conexaoLanding.setVirtualHost("plataforma");
        landing = new RabbitTemplate(conexaoLanding);
        conexaoAdmin = conexaoRabbitAdmin();
        new RabbitAdmin(conexaoAdmin).purgeQueue(DLQ, false);
    }

    @AfterEach
    void fechar() {
        conexaoLanding.destroy();
        conexaoAdmin.destroy();
    }

    @Test
    void criaLeadNaEtapa1ComContatoNormalizadoUtmConsentimentoEPontuacao(CapturedOutput saida) throws Exception {
        TenantContexto.executar(tenant, () -> regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("source", "instagram", "medium", "cpc")), 50)));
        UUID sessao = UUID.randomUUID();
        Map<String, Object> envelope = captura(tenant, sessao, " Ana.Souza@Empresa.COM.br ", "(62) 99999-8888",
                Map.of("utmSource", "instagram", "utmMedium", "cpc", "utmCampaign", "black-friday-2026"));

        processar(envelope);

        Lead lead = porSessao(tenant, sessao);
        assertThat(lead.getEtapa()).isEqualTo(1);
        assertThat(lead.getTemperatura()).isEqualTo(Temperatura.FRIO);
        assertThat(lead.getOrigem()).isEqualTo(OrigemLead.LANDING_PAGE);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.CHATBOT);
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getNome()).isEqualTo("Ana Souza");
        assertThat(lead.getEmail()).isEqualTo("ana.souza@empresa.com.br");
        assertThat(lead.getTelefone()).isEqualTo("5562999998888");
        assertThat(lead.getUtmSource()).isEqualTo("instagram");
        assertThat(lead.getUtmMedium()).isEqualTo("cpc");
        assertThat(lead.getUtmCampaign()).isEqualTo("black-friday-2026");
        assertThat(lead.isConsentimentoMarketing()).isTrue();
        assertThat(lead.getConsentimentoEm()).isEqualTo(ACEITO_EM);
        assertThat(lead.getPontuacao()).isEqualTo(50);
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_MORNO);
        assertThat(lead.getCriadoPor()).as("consumidor grava sem usuário").isNull();

        assertThat(consentimentosDe(lead)).singleElement().satisfies(aceite -> {
            assertThat(aceite.getOrigem()).isEqualTo(OrigemConsentimento.CHATBOT_LEAD_CAPTURADO);
            assertThat(aceite.isAceito()).isTrue();
            assertThat(aceite.getRegistradoEm()).isEqualTo(ACEITO_EM);
            assertThat(aceite.getSessaoId()).isEqualTo(sessao);
            assertThat(aceite.getFormularioId()).isEqualTo(formulario);
            assertThat(aceite.getEventoId()).isEqualTo(UUID.fromString((String) envelope.get("id")));
            assertThat(aceite.getTextoTermo()).as("o evento não traz o texto").isNull();
            assertThat(aceite.getIp()).isNull();
        });

        List<LeadEvento> timeline = timelineDe(lead);
        assertThat(timeline).extracting(LeadEvento::getTipoEvento).containsExactly(TipoEventoLead.ETAPA_ALTERADA,
                TipoEventoLead.CONSENTIMENTO_REGISTRADO, TipoEventoLead.QUALIFICACAO_ALTERADA);
        assertThat(timeline.get(0).getEtapaAnterior()).isNull();
        assertThat(timeline.get(0).getEtapaNova()).isEqualTo(1);
        assertThat(timeline.get(0).getPayload()).containsEntry("fonte", TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO)
                .containsEntry("leadCriado", true);
        assertThat(timeline).allSatisfy(item -> assertThat(String.valueOf(item.getPayload()))
                .doesNotContain("empresa.com.br").doesNotContain("99999"));

        assertThat(saida.getAll()).doesNotContainIgnoringCase("ana.souza@").doesNotContain("99999-8888")
                .doesNotContain("62999998888");
    }

    @Test
    void mesmaMensagemDuasVezesDaUmLeadEUmConsentimento() throws Exception {
        UUID sessao = UUID.randomUUID();
        Map<String, Object> envelope = captura(tenant, sessao, "bia@exemplo.com", null, null);

        processar(envelope, envelope);

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        assertThat(consentimentosDe(porSessao(tenant, sessao))).hasSize(1);
    }

    @Test
    void segundaCapturaDaMesmaSessaoAtualizaEmVezDeDuplicar() throws Exception {
        UUID sessao = UUID.randomUUID();
        processar(captura(tenant, sessao, "caio@exemplo.com", null,
                Map.of("utmSource", "google", "utmMedium", "cpc", "utmCampaign", "natal")));

        processar(captura(tenant, sessao, null, "62 98888-7777", Map.of("utmSource", "instagram")));

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        Lead lead = porSessao(tenant, sessao);
        assertThat(lead.getEmail()).as("vazio não apaga").isEqualTo("caio@exemplo.com");
        assertThat(lead.getTelefone()).isEqualTo("5562988887777");
        assertThat(lead.getUtmSource()).isEqualTo("instagram");
        assertThat(lead.getUtmMedium()).isEqualTo("cpc");
        assertThat(consentimentosDe(lead)).as("mesmo aceite em outro evento").hasSize(1);
        assertThat(timelineDe(lead)).filteredOn(item -> item.getTipoEvento() == TipoEventoLead.ETAPA_ALTERADA)
                .hasSize(1);
    }

    @Test
    void mesmoEmailEmOutraSessaoLigaAoMesmoLead() throws Exception {
        UUID primeiraSessao = UUID.randomUUID();
        processar(captura(tenant, primeiraSessao, "davi@exemplo.com", null, null));
        UUID primeiroLead = porSessao(tenant, primeiraSessao).getId();
        UUID segundaSessao = UUID.randomUUID();

        processar(captura(tenant, segundaSessao, "  DAVI@Exemplo.com", "6233334444", null));

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        Lead lead = porSessao(tenant, segundaSessao);
        assertThat(lead.getId()).isEqualTo(primeiroLead);
        assertThat(lead.getTelefone()).isEqualTo("556233334444");
        assertThat(lead.getSessaoId()).as("vale a conversa mais recente, a que leva ao envio").isEqualTo(segundaSessao);
    }

    @Test
    void mesmoTelefoneEmOutroFormatoLigaAoMesmoLead() throws Exception {
        processar(captura(tenant, UUID.randomUUID(), null, "62999997777", null));
        UUID sessao = UUID.randomUUID();

        processar(captura(tenant, sessao, "eva@exemplo.com", "+55 (62) 99999-7777", null));

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        assertThat(porSessao(tenant, sessao).getEmail()).isEqualTo("eva@exemplo.com");
        assertThat(porSessao(tenant, sessao).getTelefone()).isEqualTo("5562999997777");
    }

    @Test
    void capturaDepoisDoEnvioNaoRebaixaAEtapaESoCompletaDados() throws Exception {
        UUID envio = UUID.randomUUID();
        Lead doEnvio = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvio(envio, UUID.randomUUID(), CanalLead.LANDING_PAGE);
            lead.atualizarContato("Fábio", "fabio@exemplo.com", null, null);
            lead.atualizarUtm("google", "cpc", null, null, null);
            return leads.save(lead);
        });
        UUID sessao = UUID.randomUUID();

        processar(captura(tenant, sessao, "Fabio@Exemplo.com", "62977776666",
                Map.of("utmSource", "instagram", "utmCampaign", "black-friday")));

        Lead lead = TenantContexto.calcular(tenant, () -> leads.findById(doEnvio.getId())).orElseThrow();
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getEnvioId()).isEqualTo(envio);
        assertThat(lead.getUtmSource()).as("só completa").isEqualTo("google");
        assertThat(lead.getUtmCampaign()).isEqualTo("black-friday");
        assertThat(lead.getTelefone()).isEqualTo("5562977776666");
        assertThat(lead.getSessaoId()).isEqualTo(sessao);
        assertThat(lead.isConsentimentoMarketing()).isTrue();
        assertThat(timelineDe(lead)).extracting(LeadEvento::getTipoEvento)
                .doesNotContain(TipoEventoLead.ETAPA_ALTERADA)
                .contains(TipoEventoLead.CONSENTIMENTO_REGISTRADO);
        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
    }

    @Test
    void leadDoMetaNaEtapa0SobeParaAEtapa1() throws Exception {
        Lead doMeta = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.META_ADS, 0);
            lead.vincularMetaLead("leadgen-" + UUID.randomUUID());
            lead.atualizarContato(null, "gil@exemplo.com", null, null);
            return leads.save(lead);
        });

        processar(captura(tenant, UUID.randomUUID(), "gil@exemplo.com", null, null));

        Lead lead = TenantContexto.calcular(tenant, () -> leads.findById(doMeta.getId())).orElseThrow();
        assertThat(lead.getEtapa()).isEqualTo(1);
        assertThat(lead.getOrigem()).as("a origem não muda").isEqualTo(OrigemLead.META_ADS);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.CHATBOT);
        assertThat(timelineDe(lead)).filteredOn(item -> item.getTipoEvento() == TipoEventoLead.ETAPA_ALTERADA)
                .singleElement().satisfies(item -> {
                    assertThat(item.getEtapaAnterior()).isEqualTo(0);
                    assertThat(item.getEtapaNova()).isEqualTo(1);
                });
    }

    @Test
    void mesmaSessaoEMesmoEmailEmOutroTenantViramOutroLead() throws Exception {
        UUID outroTenant = UUID.randomUUID();
        UUID sessao = UUID.randomUUID();
        processar(captura(tenant, sessao, "hugo@exemplo.com", null, Map.of("utmSource", "google")));

        processar(captura(outroTenant, sessao, "hugo@exemplo.com", null, Map.of("utmSource", "instagram")));

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        assertThat(leadsDoTenant(outroTenant)).isEqualTo(1);
        Lead doTenant = porSessao(tenant, sessao);
        Lead doOutro = porSessao(outroTenant, sessao);
        assertThat(doOutro.getId()).isNotEqualTo(doTenant.getId());
        assertThat(doTenant.getUtmSource()).isEqualTo("google");
        assertThat(doOutro.getTenantId()).isEqualTo(outroTenant);
        assertThat(consentimentosDe(doTenant)).hasSize(1);
    }

    @Test
    void formularioRecebidoAindaSemProcessamentoNaoVaiParaADlq() throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("envioId", UUID.randomUUID().toString());
        dados.put("formularioId", formulario.toString());
        dados.put("empresaId", null);
        dados.put("contatoId", null);
        dados.put("canal", "formulario");
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO, tenant, dados);

        processar(envelope);

        assertThat(processados(envelope)).isEqualTo(1);
        assertThat(leadsDoTenant(tenant)).isZero();
        assertThat(idsNaDlq()).doesNotContain((String) envelope.get("id"));
    }

    @Test
    void capturaSemContatoUtilizavelVaiParaADlq() throws Exception {
        Map<String, Object> envelope = captura(tenant, UUID.randomUUID(), "sem-arroba", "123", null);

        publicar(envelope);

        esperarAte(() -> idsNaDlqContem((String) envelope.get("id")));
        assertThat(processados(envelope)).isZero();
        assertThat(leadsDoTenant(tenant)).isZero();
    }

    // --- apoio ---------------------------------------------------------------------------------

    private Map<String, Object> captura(UUID tenantDoEvento, UUID sessao, String email, String telefone,
            Map<String, Object> origem) {
        Map<String, Object> contato = new LinkedHashMap<>();
        contato.put("nome", email == null ? null : " Ana Souza ");
        contato.put("email", email);
        contato.put("telefone", telefone);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("sessaoId", sessao.toString());
        dados.put("formularioId", formulario.toString());
        dados.put("paginaId", null);
        dados.put("contato", contato);
        dados.put("origem", origem == null ? Map.of() : origem);
        dados.put("consentimento", Map.of("aceito", true, "aceitoEm", ACEITO_EM.toString()));
        return envelope(TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO, tenantDoEvento, dados);
    }

    private static Map<String, Object> envelope(String tipo, UUID tenantDoEvento, Map<String, Object> dados) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("tenantId", tenantDoEvento.toString());
        envelope.put("moduloOrigem", "landing");
        envelope.put("ocorridoEm", "2026-10-01T22:14:05Z");
        envelope.put("usuarioId", null);
        envelope.put("correlacaoId", UUID.randomUUID().toString());
        envelope.put("dados", dados);
        return envelope;
    }

    /**
     * Publica as mensagens e um marcador depois delas. Com um consumidor só na fila, a ordem é a da
     * publicação: marcador processado quer dizer que as anteriores também foram.
     */
    @SafeVarargs
    private void processar(Map<String, Object>... envelopes) throws Exception {
        for (Map<String, Object> envelope : envelopes) {
            publicar(envelope);
        }
        Map<String, Object> marcador = envelope(TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO, UUID.randomUUID(),
                Map.of("envioId", UUID.randomUUID().toString(), "formularioId", UUID.randomUUID().toString(),
                        "canal", "formulario"));
        publicar(marcador);
        esperarAte(() -> processados(marcador) == 1);
    }

    private void publicar(Map<String, Object> envelope) throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setUserId("mq_landing");
        propriedades.setMessageId((String) envelope.get("id"));
        landing.send(TopologiaDeConsumo.EXCHANGE_LANDING, (String) envelope.get("tipo"),
                new Message(json.writeValueAsBytes(envelope), propriedades));
    }

    private int processados(Map<String, Object> envelope) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM marketing.eventos_processados WHERE evento_id = ?",
                Integer.class, UUID.fromString((String) envelope.get("id")));
        return total == null ? 0 : total;
    }

    private int leadsDoTenant(UUID tenantDoLead) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.leads WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class,
                tenantDoLead);
        return total == null ? 0 : total;
    }

    private Lead porSessao(UUID tenantDoLead, UUID sessao) {
        return TenantContexto.calcular(tenantDoLead, () -> leads.findBySessaoId(sessao)).orElseThrow();
    }

    private List<LeadConsentimento> consentimentosDe(Lead lead) {
        return TenantContexto.calcular(lead.getTenantId(),
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()));
    }

    private List<LeadEvento> timelineDe(Lead lead) {
        return TenantContexto.calcular(lead.getTenantId(), () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId()));
    }

    /** Esvazia a DLQ e devolve os ids das mensagens que estavam lá. */
    private List<String> idsNaDlq() throws Exception {
        RabbitTemplate admin = new RabbitTemplate(conexaoAdmin);
        List<String> ids = new ArrayList<>();
        Message morta;
        while ((morta = admin.receive(DLQ, 200)) != null) {
            ids.add(json.readTree(morta.getBody()).path("id").asText());
        }
        return ids;
    }

    private boolean idsNaDlqContem(String id) {
        try {
            return idsNaDlq().contains(id);
        } catch (Exception falha) {
            return false;
        }
    }

    private static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("condição não atingida em " + ESPERA);
            }
            Thread.sleep(100);
        }
    }
}
