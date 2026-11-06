package br.com.centinela.marketing.compartilhado.teste;

import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.CRM;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.IDENTITY;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.LANDING;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.META_GRAPH;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Prova que a base {@link IntegracaoDeTeste} (F6) está de pé: a aplicação sobe com PostgreSQL e
 * RabbitMQ do Testcontainers, recebe as URLs dos simuladores no ambiente do Spring e o RabbitMQ da
 * plataforma entrega um evento publicado pelo próprio teste. Sem Docker, é pulado.
 */
class InfraDeTesteTest extends IntegracaoDeTeste {

    @Autowired
    Environment ambiente;

    @Autowired
    ConnectionFactory conexaoDaAplicacao;

    @Test
    void urlsDosSimuladoresEntramNoAmbienteDoSpring() {
        assertThat(ambiente.getProperty("LANDING_BASE_URL")).isEqualTo(LANDING.baseUrl());
        assertThat(ambiente.getProperty("CRM_BASE_URL")).isEqualTo(CRM.baseUrl());
        assertThat(ambiente.getProperty("IDENTITY_BASE_URL")).isEqualTo(IDENTITY.baseUrl());
        assertThat(ambiente.getProperty("META_GRAPH_BASE_URL")).isEqualTo(META_GRAPH.baseUrl());
        assertThat(ambiente.getProperty("SVC_CLIENT_ID")).isEqualTo(ModulosSimulados.CLIENT_ID);
        assertThat(ambiente.getProperty("SVC_CLIENT_SECRET")).isEqualTo(ModulosSimulados.CLIENT_SECRET);
    }

    @Test
    void simuladorRespondeNaUrlQueOSpringRecebeu() {
        LANDING.stubFor(get(urlPathEqualTo("/api/landing/health")).willReturn(Envelopes.ok(Map.of("status", "UP"))));

        String corpo = RestClient.create(ambiente.getProperty("LANDING_BASE_URL"))
                .get().uri("/api/landing/health").retrieve().body(String.class);

        assertThat(corpo).contains("\"success\":true").contains("\"status\":\"UP\"");
    }

    @Test
    void rabbitMqAceitaAAplicacaoEEntregaEventoPublicadoPeloTeste() {
        try (Connection conexao = conexaoDaAplicacao.createConnection()) {
            assertThat(conexao.isOpen()).as("aplicação conectada como mq_marketing").isTrue();
        }

        CachingConnectionFactory admin = conexaoRabbitAdmin();
        try {
            RabbitAdmin rabbitAdmin = new RabbitAdmin(admin);
            Queue fila = new AnonymousQueue();
            rabbitAdmin.declareQueue(fila);
            rabbitAdmin.declareBinding(BindingBuilder.bind(fila)
                    .to(new TopicExchange("landing.eventos")).with("landing.#"));
            RabbitTemplate template = new RabbitTemplate(admin);

            template.convertAndSend("landing.eventos", "landing.formulario.recebido", "{\"id\":\"1\"}");

            Message mensagem = template.receive(fila.getName(), 5_000);
            assertThat(mensagem).isNotNull();
            assertThat(new String(mensagem.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"1\"}");
        } finally {
            admin.destroy();
        }
    }
}
