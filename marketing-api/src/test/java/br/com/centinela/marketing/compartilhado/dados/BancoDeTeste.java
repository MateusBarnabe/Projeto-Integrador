package br.com.centinela.marketing.compartilhado.dados;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import br.com.centinela.marketing.compartilhado.tenant.ResolvedorDeTenant;

/**
 * Base dos testes de repositório: PostgreSQL do Testcontainers com os usuários e o schema da
 * plataforma, migrations do Flyway e o tenant do Hibernate ligados.
 *
 * Os testes não rodam dentro de uma transação única: o Hibernate fixa o tenant ao abrir a sessão,
 * então cada chamada a repositório deve rodar dentro de {@code TenantContexto.executar}.
 * Sem Docker disponível, os testes são pulados.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ResolvedorDeTenant.class)
public abstract class BancoDeTeste {

    // Um container para todas as subclasses, sem @Container: o Spring reaproveita o mesmo contexto
    // entre elas, e um banco parado no fim de cada classe deixaria o contexto em cache sem conexão.
    // O Testcontainers remove o container quando a JVM dos testes termina.
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("plataforma")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01-marketing.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-marketing.sh");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        POSTGRES.start();
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", () -> "usr_marketing");
        registro.add("spring.datasource.password", () -> "usr_marketing");
        registro.add("spring.flyway.user", () -> "own_marketing");
        registro.add("spring.flyway.password", () -> "own_marketing");
    }
}
