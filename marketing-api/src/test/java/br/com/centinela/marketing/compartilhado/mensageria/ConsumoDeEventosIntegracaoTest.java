package br.com.centinela.marketing.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Consumo de ponta a ponta contra o RabbitMQ e o PostgreSQL da plataforma (definitions.json e
 * 01-marketing.sh): a API declara as filas como {@code mq_marketing}, o "Landing" publica como
 * {@code mq_landing} em {@code landing.eventos} e um consumidor de teste usa {@link ConsumoDeEventos}.
 * Confere idempotência, transação, três tentativas e DLQ. Sem Docker, os testes são pulados.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.retry.initial-interval=50ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=100ms" })
@DirtiesContext // o contexto tem um listener; parado o broker, ele não pode ficar no cache
@ExtendWith(OutputCaptureExtension.class)
class ConsumoDeEventosIntegracaoTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("plataforma")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01-marketing.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-marketing.sh");

    @Container
    @SuppressWarnings("resource")
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/rabbitmq/definitions.json"),
                    "/etc/rabbitmq/definitions.json")
            .withRabbitMQConfig(MountableFile.forHostPath("../infra/rabbitmq/rabbitmq.conf"));

    @DynamicPropertySource
    static void infraestrutura(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", () -> "usr_marketing");
        registro.add("spring.datasource.password", () -> "usr_marketing");
        registro.add("spring.flyway.user", () -> "own_marketing");
        registro.add("spring.flyway.password", () -> "own_marketing");
        registro.add("spring.rabbitmq.host", RABBIT::getHost);
        registro.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    }

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-00000000000a");
    private static final String EMAIL = "fulano@exemplo.com";
    private static final Duration ESPERA = Duration.ofSeconds(10);

    /** Consumidor de teste na fila real do Landing; o efeito de cada teste é trocado aqui. */
    @TestConfiguration
    static class ConsumidorDeTeste {

        static volatile Consumer<EventoRecebido> efeito = evento -> { };
        static final AtomicInteger chamadas = new AtomicInteger();
        static final List<String> contextos = new CopyOnWriteArrayList<>();

        @Bean
        ListenerDeTeste consumidorLandingDeTeste(ConsumoDeEventos consumo) {
            return new ListenerDeTeste(consumo);
        }
    }

    static class ListenerDeTeste {

        private final ConsumoDeEventos consumo;

        ListenerDeTeste(ConsumoDeEventos consumo) {
            this.consumo = consumo;
        }

        @RabbitListener(queues = TopologiaDeConsumo.FILA_LANDING_LEADS)
        public void receber(Message mensagem) {
            consumo.consumir(mensagem, "landing", evento -> {
                ConsumidorDeTeste.chamadas.incrementAndGet();
                ConsumidorDeTeste.contextos.add(TenantContexto.exigir() + "|" + MDC.get(RequestIdFilter.CHAVE_MDC));
                ConsumidorDeTeste.efeito.accept(evento);
            });
        }
    }

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitDaApi;

    private CachingConnectionFactory conexaoLanding;
    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate landing;
    private RabbitTemplate admin;

    @BeforeEach
    void preparar() {
        // Abre a conexão da API, que declara filas e ligações como mq_marketing.
        rabbitDaApi.execute(canal -> null);

        conexaoLanding = conexao("mq_landing", "mq_landing");
        landing = new RabbitTemplate(conexaoLanding);
        conexaoAdmin = conexao("admin", "admin");
        admin = new RabbitTemplate(conexaoAdmin);
        RabbitAdmin gestao = new RabbitAdmin(conexaoAdmin);
        gestao.purgeQueue(TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS), false);
        gestao.purgeQueue(TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES, false);

        ConsumidorDeTeste.efeito = evento -> { };
        ConsumidorDeTeste.chamadas.set(0);
        ConsumidorDeTeste.contextos.clear();
    }

    @AfterEach
    void fechar() {
        conexaoLanding.destroy();
        conexaoAdmin.destroy();
    }

    @Test
    void mesmaMensagemEntregueDuasVezesRodaOEfeitoUmaVezNoTenantDoEnvelope() throws Exception {
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO);
        UUID id = UUID.fromString((String) envelope.get("id"));

        publicarComoLanding(envelope);
        publicarComoLanding(envelope);

        esperarAte(() -> registrados(id) == 1 && filaVazia(TopologiaDeConsumo.FILA_LANDING_LEADS));
        Thread.sleep(300);
        assertThat(ConsumidorDeTeste.chamadas).hasValue(1);
        assertThat(ConsumidorDeTeste.contextos).containsExactly(TENANT + "|req-42");
        assertThat(jdbc.queryForObject("SELECT tipo FROM marketing.eventos_processados WHERE evento_id = ?",
                String.class, id)).isEqualTo(TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO);
    }

    @Test
    void falhaNaPrimeiraTentativaDesfazORegistroEASegundaProcessa() throws Exception {
        AtomicInteger tentativas = new AtomicInteger();
        ConsumidorDeTeste.efeito = evento -> {
            if (tentativas.incrementAndGet() == 1) {
                throw new IllegalStateException("Landing fora do ar");
            }
        };
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO);

        publicarComoLanding(envelope);

        UUID id = UUID.fromString((String) envelope.get("id"));
        esperarAte(() -> registrados(id) == 1);
        assertThat(tentativas).hasValue(2);
        assertThat(admin.receive(TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS), 500)).isNull();
    }

    @Test
    void depoisDeTresFalhasAMensagemVaiParaADlqSemDadoPessoalNoLog(CapturedOutput saida) throws Exception {
        ConsumidorDeTeste.efeito = evento -> {
            throw new IllegalStateException("sempre falha");
        };
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO);

        publicarComoLanding(envelope);

        Message morta = admin.receive(TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS),
                ESPERA.toMillis());
        assertThat(morta).as("mensagem na DLQ").isNotNull();
        assertThat(json.readTree(morta.getBody()).get("id").asText()).isEqualTo(envelope.get("id"));
        assertThat(morta.getMessageProperties().getXDeathHeader()).isNotEmpty();
        assertThat(ConsumidorDeTeste.chamadas).hasValue(3);
        assertThat(registrados(UUID.fromString((String) envelope.get("id")))).isZero();
        assertThat(saida.getAll()).contains("Mensagem rejeitada para a DLQ").doesNotContain(EMAIL);
    }

    @Test
    void remetenteQueNaoEhOLandingVaiDiretoParaADlq() throws Exception {
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO);

        // O admin pode publicar em landing.eventos, mas o user_id dele não confere com moduloOrigem.
        admin.send(TopologiaDeConsumo.EXCHANGE_LANDING, TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO,
                mensagem(envelope, "admin"));

        Message morta = admin.receive(TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_LANDING_LEADS),
                ESPERA.toMillis());
        assertThat(morta).isNotNull();
        assertThat(ConsumidorDeTeste.chamadas).as("sem efeito e sem novas tentativas").hasValue(0);
    }

    @Test
    void filaDoCrmEstaLigadaACrmEventosPelaOportunidadeCriada() throws Exception {
        Map<String, Object> envelope = envelope(TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA);
        envelope.put("moduloOrigem", "crm");

        admin.send(TopologiaDeConsumo.EXCHANGE_CRM, TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA,
                mensagem(envelope, "admin"));
        admin.send(TopologiaDeConsumo.EXCHANGE_CRM, "crm.empresa.criada", mensagem(envelope, "admin"));

        Message recebida = admin.receive(TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES, ESPERA.toMillis());
        assertThat(recebida).isNotNull();
        assertThat(recebida.getMessageProperties().getReceivedRoutingKey())
                .isEqualTo(TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA);
        assertThat(admin.receive(TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES, 500))
                .as("outras chaves não entram").isNull();
    }

    private CachingConnectionFactory conexao(String usuario, String senha) {
        CachingConnectionFactory conexao = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexao.setUsername(usuario);
        conexao.setPassword(senha);
        conexao.setVirtualHost("plataforma");
        return conexao;
    }

    private void publicarComoLanding(Map<String, Object> envelope) throws Exception {
        landing.send(TopologiaDeConsumo.EXCHANGE_LANDING, (String) envelope.get("tipo"),
                mensagem(envelope, "mq_landing"));
    }

    private Message mensagem(Map<String, Object> envelope, String userId) throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setUserId(userId);
        propriedades.setMessageId((String) envelope.get("id"));
        return new Message(json.writeValueAsBytes(envelope), propriedades);
    }

    private Map<String, Object> envelope(String tipo) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("tenantId", TENANT.toString());
        envelope.put("moduloOrigem", "landing");
        envelope.put("ocorridoEm", "2026-11-05T13:02:11Z");
        envelope.put("usuarioId", null);
        envelope.put("correlacaoId", "req-42");
        envelope.put("dados", Map.of("sessaoId", "s-1", "contato", Map.of("nome", "Fulano", "email", EMAIL)));
        return envelope;
    }

    private int registrados(UUID id) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM marketing.eventos_processados WHERE evento_id = ?",
                Integer.class, id);
        return total == null ? 0 : total;
    }

    private boolean filaVazia(String fila) {
        var info = new RabbitAdmin(conexaoAdmin).getQueueInfo(fila);
        return info != null && info.getMessageCount() == 0;
    }

    private static void esperarAte(java.util.function.BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("condição não atingida em " + ESPERA);
            }
            Thread.sleep(50);
        }
    }
}
