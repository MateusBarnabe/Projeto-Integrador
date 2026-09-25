package br.com.centinela.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Sobe a API contra um PostgreSQL real, com os mesmos usuários e schema da plataforma
 * (infra/postgres/init/01-marketing.sh), e confere as regras básicas do checklist §15.
 * Sem Docker disponível, os testes são pulados.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FundacaoApiTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() { };

    @Container // o @Testcontainers fecha o container no fim da classe
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("plataforma")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/postgres/init/01-marketing.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-marketing.sh");

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", () -> "usr_marketing");
        registro.add("spring.datasource.password", () -> "usr_marketing");
        registro.add("spring.flyway.user", () -> "own_marketing");
        registro.add("spring.flyway.password", () -> "own_marketing");
    }

    @Autowired
    TestRestTemplate http;

    private ResponseEntity<Map<String, Object>> get(String caminho) {
        return http.exchange(caminho, HttpMethod.GET, null, JSON);
    }

    @Test
    void healthRespondeSemTokenComEnvelope() {
        ResponseEntity<Map<String, Object>> resposta = get("/api/marketing/health");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody())
                .containsEntry("success", true)
                .containsEntry("data", Map.of("status", "UP"))
                .containsEntry("message", null)
                .containsKey("errors");
        assertThat(resposta.getHeaders().getFirst("X-Request-Id")).isNotBlank();
    }

    @Test
    void rotaAutenticadaSemTokenResponde401ComEnvelope() {
        ResponseEntity<Map<String, Object>> resposta = get("/api/marketing/leads");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getBody()).containsEntry("success", false).containsKey("errors");
    }
}
