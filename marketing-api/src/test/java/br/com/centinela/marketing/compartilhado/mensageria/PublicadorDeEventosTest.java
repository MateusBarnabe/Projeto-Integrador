package br.com.centinela.marketing.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Publica contra um RabbitMQ com os mesmos usuários, permissões e exchanges da plataforma
 * (infra/rabbitmq/definitions.json). O broker confere o {@code user_id} de verdade: se ele não
 * bater com o usuário da conexão, a publicação é recusada. Sem Docker, os testes são pulados.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PublicadorDeEventosTest {

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
    private static final UUID USUARIO = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    PublicadorDeEventos publicador;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    ObjectMapper json;

    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate observador;
    private Queue filaEventos;
    private Queue filaPlataforma;

    /** O teste observa as exchanges como admin, com filas temporárias ligadas a tudo. */
    @BeforeEach
    void observarExchanges() {
        conexaoAdmin = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexaoAdmin.setUsername("admin");
        conexaoAdmin.setPassword("admin");
        conexaoAdmin.setVirtualHost("plataforma");
        RabbitAdmin admin = new RabbitAdmin(conexaoAdmin);
        filaEventos = new AnonymousQueue();
        filaPlataforma = new AnonymousQueue();
        admin.declareQueue(filaEventos);
        admin.declareQueue(filaPlataforma);
        admin.declareBinding(BindingBuilder.bind(filaEventos)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_EVENTOS)).with("#"));
        admin.declareBinding(BindingBuilder.bind(filaPlataforma)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_PLATAFORMA)).with("#"));
        observador = new RabbitTemplate(conexaoAdmin);
    }

    @AfterEach
    void limpar() {
        conexaoAdmin.destroy();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicaOEnvelopeEmMarketingEventosDepoisDoCommit() throws Exception {
        UUID formulario = UUID.randomUUID();
        MDC.put(RequestIdFilter.CHAVE_MDC, "req-123");

        Evento publicado = emTransacao(() -> publicador.publicar("marketing.formulario.publicado", 1,
                Map.of("formularioId", formulario, "versao", 2)));

        Message mensagem = observador.receive(filaEventos.getName(), 5_000);
        assertThat(mensagem).as("mensagem em marketing.eventos").isNotNull();
        var propriedades = mensagem.getMessageProperties();
        assertThat(propriedades.getReceivedUserId()).isEqualTo("mq_marketing");
        assertThat(propriedades.getReceivedRoutingKey()).isEqualTo("marketing.formulario.publicado");
        assertThat(propriedades.getMessageId()).isEqualTo(publicado.id().toString());
        assertThat(propriedades.getContentType()).isEqualTo("application/json");

        JsonNode envelope = json.readTree(mensagem.getBody());
        assertThat(envelope.get("id").asText()).isEqualTo(publicado.id().toString());
        assertThat(envelope.get("tipo").asText()).isEqualTo("marketing.formulario.publicado");
        assertThat(envelope.get("versao").asInt()).isEqualTo(1);
        assertThat(envelope.get("tenantId").asText()).isEqualTo(TENANT.toString());
        assertThat(envelope.get("moduloOrigem").asText()).isEqualTo("marketing");
        assertThat(envelope.get("ocorridoEm").asText()).endsWith("Z");
        assertThat(envelope.get("usuarioId").isNull()).as("rotina, sem usuário").isTrue();
        assertThat(envelope.get("correlacaoId").asText()).isEqualTo("req-123");
        assertThat(envelope.get("dados").get("formularioId").asText()).isEqualTo(formulario.toString());
        assertThat(envelope.get("dados").get("versao").asInt()).isEqualTo(2);
    }

    @Test
    void nadaSaiQuandoATransacaoFazRollback() {
        TenantContexto.executar(TENANT, () -> transacao.executeWithoutResult(status -> {
            publicador.publicar("marketing.formulario.publicado", 1, Map.of("versao", 1));
            status.setRollbackOnly();
        }));

        assertThat(observador.receive(filaEventos.getName(), 1_500)).isNull();
    }

    @Test
    void pedidoAPlataformaVaiParaIdentityEntradaComOUsuarioDoToken() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(new Jwt("token",
                Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", USUARIO.toString(), "tenant_id", TENANT.toString()))));

        emTransacao(() -> publicador.pedirAPlataforma("identity.notificacao.criar", 1,
                Map.of("categoria", "NOVO_LEAD")));

        Message mensagem = observador.receive(filaPlataforma.getName(), 5_000);
        assertThat(mensagem).as("mensagem em identity.entrada").isNotNull();
        assertThat(mensagem.getMessageProperties().getReceivedUserId()).isEqualTo("mq_marketing");
        assertThat(json.readTree(mensagem.getBody()).get("usuarioId").asText()).isEqualTo(USUARIO.toString());
        assertThat(observador.receive(filaEventos.getName(), 500)).isNull();
    }

    @Test
    void publicarForaDeTransacaoEhRecusado() {
        assertThatThrownBy(() -> TenantContexto.executar(TENANT,
                () -> publicador.publicar("marketing.formulario.publicado", 1, Map.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tipoForaDoPadraoEhRecusado() {
        assertThatThrownBy(() -> emTransacao(() -> publicador.publicar("FormularioPublicado", 1, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> emTransacao(() -> publicador.publicar("marketing.formulario", 1, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Evento emTransacao(java.util.function.Supplier<Evento> acao) {
        return TenantContexto.calcular(TENANT, () -> transacao.execute(status -> acao.get()));
    }
}
