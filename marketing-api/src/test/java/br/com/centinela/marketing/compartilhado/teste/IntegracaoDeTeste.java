package br.com.centinela.marketing.compartilhado.teste;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.MountableFile;

/**
 * Base dos testes de integração (checklist §15): a aplicação inteira contra um PostgreSQL e um
 * RabbitMQ do Testcontainers, com os mesmos usuários, permissões e exchanges da plataforma
 * ({@code infra/postgres/init} e {@code infra/rabbitmq}), e o WireMock no lugar do Landing, do CRM,
 * do identity e da Graph API do Meta ({@link ModulosSimulados}).
 *
 * <p>Os containers e os servidores WireMock são únicos por JVM, sem {@code @Container}: o Spring
 * reaproveita o mesmo contexto entre as subclasses, e um container parado no fim de uma classe
 * deixaria o contexto em cache sem conexão. Antes de cada teste os stubs são zerados e o do token de
 * serviço volta. Os testes não rodam numa transação; dados de tenant vão em
 * {@code TenantContexto.executar}. Sem Docker disponível, os testes são pulados.
 *
 * <p>Os eventos de outros módulos são publicados pelo próprio teste ({@link #conexaoRabbitAdmin()}).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public abstract class IntegracaoDeTeste {

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("plataforma")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01-marketing.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-marketing.sh");

    @SuppressWarnings("resource")
    protected static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/rabbitmq/definitions.json"),
                    "/etc/rabbitmq/definitions.json")
            .withRabbitMQConfig(MountableFile.forHostPath("../infra/rabbitmq/rabbitmq.conf"));

    @DynamicPropertySource
    static void infraestrutura(DynamicPropertyRegistry registro) {
        Startables.deepStart(POSTGRES, RABBIT).join();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", () -> "usr_marketing");
        registro.add("spring.datasource.password", () -> "usr_marketing");
        registro.add("spring.flyway.user", () -> "own_marketing");
        registro.add("spring.flyway.password", () -> "own_marketing");
        registro.add("spring.rabbitmq.host", RABBIT::getHost);
        registro.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        ModulosSimulados.registrar(registro);
    }

    @BeforeEach
    protected final void zerarModulosSimulados() {
        ModulosSimulados.resetar();
    }

    /**
     * Conexão de administrador no vhost {@code plataforma}, para declarar filas temporárias de
     * observação e publicar os eventos do Landing e do CRM. Quem abre fecha ({@code destroy()}).
     */
    protected static CachingConnectionFactory conexaoRabbitAdmin() {
        CachingConnectionFactory conexao = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexao.setUsername("admin");
        conexao.setPassword("admin");
        conexao.setVirtualHost("plataforma");
        return conexao;
    }
}
