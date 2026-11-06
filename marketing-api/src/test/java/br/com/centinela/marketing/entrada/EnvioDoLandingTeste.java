package br.com.centinela.marketing.entrada;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos;
import br.com.centinela.marketing.compartilhado.mensageria.TopologiaDeConsumo;
import br.com.centinela.marketing.compartilhado.teste.Envelopes;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.teste.ModulosSimulados;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.RegraPontuacaoRepository;
import br.com.centinela.marketing.qualificacao.VendedorRodizio;
import br.com.centinela.marketing.qualificacao.VendedorRodizioRepository;

/**
 * Apoio dos testes da E5 e da E11: o "Landing" publica {@code landing.formulario.recebido} como
 * {@code mq_landing} em {@code landing.eventos} e responde {@code GET /api/landing/envios/{id}} no
 * WireMock, no formato do {@code landing.yaml}, exigindo o token de serviço e o {@code X-Tenant-Id}.
 *
 * <p>Cada teste tem o próprio tenant, com o rodízio aberto o dia inteiro, um vendedor ativo e regras
 * da matriz inicial no formulário do teste: Cargo "Decisor" (30), Prazo "Imediato" (25) e
 * {@code google}/{@code cpc} (15). Um envio com as três respostas soma 70 (MQL quente).
 */
abstract class EnvioDoLandingTeste extends IntegracaoDeTeste {

    static final Duration ESPERA = Duration.ofSeconds(20);
    static final OffsetDateTime ACEITO_EM = OffsetDateTime.of(2026, 11, 5, 14, 30, 0, 0, ZoneOffset.UTC);
    static final OffsetDateTime PRIMEIRO_TOQUE = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    static final OffsetDateTime ULTIMO_TOQUE = OffsetDateTime.of(2026, 11, 5, 14, 20, 0, 0, ZoneOffset.UTC);
    static final String TEXTO_TERMO = "Autorizo a Empresa A a usar os meus dados para contato comercial.";
    static final String DLQ = TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS);

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitDaApi;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadConsentimentoRepository consentimentos;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    RegraPontuacaoRepository regras;

    @Autowired
    QualificacaoConfiguracaoRepository configuracoes;

    @Autowired
    VendedorRodizioRepository vendedores;

    final UUID tenant = UUID.randomUUID();
    final UUID formulario = UUID.randomUUID();
    final UUID campoNome = UUID.randomUUID();
    final UUID campoEmail = UUID.randomUUID();
    final UUID campoTelefone = UUID.randomUUID();
    final UUID campoCargo = UUID.randomUUID();
    final UUID campoPrazo = UUID.randomUUID();
    final UUID vendedor = UUID.randomUUID();

    private CachingConnectionFactory conexaoLanding;
    CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate landing;
    private RabbitTemplate observador;
    private Queue notificacoes;

    @BeforeEach
    void prepararLanding() {
        rabbitDaApi.execute(canal -> null); // filas declaradas pela API
        conexaoLanding = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexaoLanding.setUsername("mq_landing");
        conexaoLanding.setPassword("mq_landing");
        conexaoLanding.setVirtualHost("plataforma");
        landing = new RabbitTemplate(conexaoLanding);
        conexaoAdmin = conexaoRabbitAdmin();
        RabbitAdmin admin = new RabbitAdmin(conexaoAdmin);
        admin.purgeQueue(DLQ, false);
        notificacoes = new AnonymousQueue();
        admin.declareQueue(notificacoes);
        admin.declareBinding(BindingBuilder.bind(notificacoes)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_PLATAFORMA)).with("identity.notificacao.criar"));
        observador = new RabbitTemplate(conexaoAdmin);
        prepararTenant(tenant);
    }

    @AfterEach
    void fecharLanding() {
        conexaoLanding.destroy();
        conexaoAdmin.destroy();
    }

    /** Rodízio aberto o dia inteiro, um vendedor ativo e as regras da matriz no formulário do teste. */
    void prepararTenant(UUID tenantDoTeste) {
        TenantContexto.executar(tenantDoTeste, () -> transacao.executeWithoutResult(s -> {
            QualificacaoConfiguracao configuracao = QualificacaoConfiguracao.padrao();
            configuracao.definirAtendimento(LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59),
                    EnumSet.allOf(DayOfWeek.class));
            configuracao.definirRodizio(true, 15);
            configuracoes.save(configuracao);
            vendedores.save(new VendedorRodizio(vendedor));
            regras.save(RegraPontuacao.porCampo("Cargo", formulario, campoCargo, List.of("Decisor", "Sócio"), 30));
            regras.save(RegraPontuacao.porCampo("Prazo de compra", formulario, campoPrazo, List.of("Imediato"), 25));
            regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                    List.of(Map.of("source", "google", "medium", "cpc")), 15));
        }));
    }

    // --- Landing simulado --------------------------------------------------------------------

    /** Envio no formato do schema {@code Envio}, com as respostas que somam 70 pontos. */
    Map<String, Object> envio(UUID envioId, String email, String telefone) {
        Map<String, Object> envio = new LinkedHashMap<>();
        envio.put("id", envioId.toString());
        envio.put("formularioId", formulario.toString());
        envio.put("formularioNome", "Contato — Black Friday");
        envio.put("paginaId", null);
        envio.put("canal", "landing_page");
        envio.put("nome", " Ana Souza ");
        envio.put("email", email);
        envio.put("recebidoEm", ACEITO_EM.plusSeconds(10).toString());
        envio.put("anonimizado", false);
        envio.put("respostas", List.of(
                resposta(campoNome, "Seu nome", "TEXTO", "Ana Souza"),
                resposta(campoEmail, "Seu melhor e-mail", "EMAIL", email),
                resposta(campoTelefone, "Telefone (WhatsApp)", "TELEFONE", telefone),
                resposta(campoCargo, "Cargo", "LISTA_OPCOES", "Decisor"),
                resposta(campoPrazo, "Prazo de compra", "LISTA_OPCOES", "Imediato")));
        envio.put("origem", Map.of(
                "primeira", toque("instagram", "social", "lancamento", PRIMEIRO_TOQUE),
                "ultima", toque("google", "cpc", "black-friday-2026", ULTIMO_TOQUE)));
        envio.put("consentimento", Map.of("aceito", true, "texto", TEXTO_TERMO, "aceitoEm", ACEITO_EM.toString()));
        envio.put("empresaId", null);
        envio.put("contatoId", null);
        return envio;
    }

    private static Map<String, Object> resposta(UUID campoId, String rotulo, String tipo, Object valor) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("campoId", campoId.toString());
        resposta.put("rotulo", rotulo);
        resposta.put("tipo", tipo);
        resposta.put("valor", valor);
        return resposta;
    }

    private static Map<String, Object> toque(String source, String medium, String campaign, OffsetDateTime em) {
        Map<String, Object> toque = new LinkedHashMap<>();
        toque.put("utmSource", source);
        toque.put("utmMedium", medium);
        toque.put("utmCampaign", campaign);
        toque.put("utmTerm", null);
        toque.put("utmContent", null);
        toque.put("referenciador", "https://www.google.com/");
        toque.put("paginaEntrada", "https://empresa-a.dev/black-friday?utm_source=" + source);
        toque.put("ocorridoEm", em.toString());
        return toque;
    }

    /** O Landing responde o envio, só com o token de serviço e o {@code X-Tenant-Id} do tenant. */
    void stubEnvio(UUID tenantDoEnvio, UUID envioId, Map<String, Object> envio) {
        ModulosSimulados.LANDING.stubFor(ModulosSimulados.comTokenDeServico(
                get(urlPathEqualTo("/api/landing/envios/" + envioId)), tenantDoEnvio).willReturn(Envelopes.ok(envio)));
    }

    /** O Landing está fora do ar para este envio ({@code 503}). */
    void stubLandingForaDoAr(UUID envioId) {
        ModulosSimulados.LANDING.stubFor(get(urlPathEqualTo("/api/landing/envios/" + envioId))
                .willReturn(Envelopes.erro(503, "Serviço indisponível.")));
    }

    int leiturasDoEnvio(UUID envioId) {
        return ModulosSimulados.LANDING.findAll(getRequestedFor(urlPathEqualTo("/api/landing/envios/" + envioId)))
                .size();
    }

    // --- eventos ------------------------------------------------------------------------------

    Map<String, Object> formularioRecebido(UUID tenantDoEvento, UUID envioId, UUID empresaId, UUID contatoId) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("envioId", envioId.toString());
        dados.put("formularioId", formulario.toString());
        dados.put("empresaId", empresaId == null ? null : empresaId.toString());
        dados.put("contatoId", contatoId == null ? null : contatoId.toString());
        dados.put("canal", "landing_page");
        return envelope(TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO, tenantDoEvento, dados);
    }

    Map<String, Object> capturaDoChatbot(UUID tenantDoEvento, UUID sessao, String email) {
        Map<String, Object> contato = new LinkedHashMap<>();
        contato.put("nome", "Ana");
        contato.put("email", email);
        contato.put("telefone", null);
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("sessaoId", sessao.toString());
        dados.put("formularioId", formulario.toString());
        dados.put("paginaId", null);
        dados.put("contato", contato);
        dados.put("origem", Map.of("utmSource", "google", "utmMedium", "cpc"));
        dados.put("consentimento", Map.of("aceito", true, "aceitoEm", ACEITO_EM.minusMinutes(5).toString()));
        return envelope(TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO, tenantDoEvento, dados);
    }

    static Map<String, Object> envelope(String tipo, UUID tenantDoEvento, Map<String, Object> dados) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("tenantId", tenantDoEvento.toString());
        envelope.put("moduloOrigem", "landing");
        envelope.put("ocorridoEm", "2026-11-05T14:30:05Z");
        envelope.put("usuarioId", null);
        envelope.put("correlacaoId", UUID.randomUUID().toString());
        envelope.put("dados", dados);
        return envelope;
    }

    /**
     * Publica as mensagens e um marcador depois delas, e espera o marcador. Com um consumidor só na
     * fila, a ordem é a da publicação: marcador processado quer dizer que as anteriores também foram.
     */
    @SafeVarargs
    final void processar(Map<String, Object>... envelopes) throws Exception {
        for (Map<String, Object> envelope : envelopes) {
            publicar(envelope);
        }
        Map<String, Object> marcador = capturaDoChatbot(UUID.randomUUID(), UUID.randomUUID(), "marcador@exemplo.com");
        publicar(marcador);
        esperarAte(() -> processados(marcador) == 1);
    }

    void publicar(Map<String, Object> envelope) throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setUserId("mq_landing");
        propriedades.setMessageId((String) envelope.get("id"));
        landing.send(TopologiaDeConsumo.EXCHANGE_LANDING, (String) envelope.get("tipo"),
                new Message(json.writeValueAsBytes(envelope), propriedades));
    }

    int processados(Map<String, Object> envelope) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM marketing.eventos_processados WHERE evento_id = ?",
                Integer.class, UUID.fromString((String) envelope.get("id")));
        return total == null ? 0 : total;
    }

    List<String> idsNaDlq() throws Exception {
        RabbitTemplate admin = new RabbitTemplate(conexaoAdmin);
        List<String> ids = new ArrayList<>();
        Message morta;
        while ((morta = admin.receive(DLQ, 200)) != null) {
            ids.add(json.readTree(morta.getBody()).path("id").asText());
        }
        return ids;
    }

    /**
     * A primeira notificação {@code identity.notificacao.criar} que satisfaz o filtro, ou nulo. Usa
     * {@code basicGet} em laço: um consumidor cancelado apagaria a fila auto-delete.
     */
    JsonNode notificacao(Predicate<JsonNode> filtro, long esperaMs) throws Exception {
        long limite = System.currentTimeMillis() + esperaMs;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = observador.receive(notificacoes.getName());
            if (mensagem == null) {
                Thread.sleep(100);
                continue;
            }
            JsonNode envelope = json.readTree(mensagem.getBody());
            if (filtro.test(envelope)) {
                return envelope;
            }
        }
        return null;
    }

    // --- leitura do banco ---------------------------------------------------------------------

    int leadsAtivos(UUID tenantDoLead) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.leads WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class,
                tenantDoLead);
        return total == null ? 0 : total;
    }

    Lead porEnvio(UUID tenantDoLead, UUID envioId) {
        return TenantContexto.calcular(tenantDoLead, () -> leads.findByEnvioId(envioId)).orElseThrow();
    }

    Lead porId(UUID tenantDoLead, UUID id) {
        return TenantContexto.calcular(tenantDoLead, () -> leads.findById(id)).orElseThrow();
    }

    Lead gravar(UUID tenantDoLead, Supplier<Lead> novo) {
        return TenantContexto.calcular(tenantDoLead, () -> transacao.execute(s -> {
            Lead lead = leads.save(novo.get());
            eventos.save(LeadEvento.etapaAlterada(lead.getId(), null, lead.getEtapa(), new LinkedHashMap<>()));
            return lead;
        }));
    }

    List<LeadConsentimento> consentimentosDe(Lead lead) {
        return TenantContexto.calcular(lead.getTenantId(),
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()));
    }

    List<LeadEvento> timelineDe(UUID tenantDoLead, UUID leadId) {
        return TenantContexto.calcular(tenantDoLead, () -> eventos.findByLeadIdOrderBySequenciaAsc(leadId));
    }

    List<LeadEvento> doTipo(UUID tenantDoLead, UUID leadId, TipoEventoLead tipo) {
        return timelineDe(tenantDoLead, leadId).stream().filter(item -> item.getTipoEvento() == tipo).toList();
    }

    static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("condição não atingida em " + ESPERA);
            }
            Thread.sleep(100);
        }
    }
}
