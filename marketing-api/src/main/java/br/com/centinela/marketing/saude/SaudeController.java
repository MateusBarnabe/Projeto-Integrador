package br.com.centinela.marketing.saude;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.Resposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/**
 * {@code GET /api/marketing/health}: sem token, 200 com o serviço de pé e conectado ao banco (§8.5).
 */
@RestController
public class SaudeController {

    private final JdbcTemplate jdbc;

    public SaudeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/marketing/health")
    @Operation(summary = "Saúde do serviço e da conexão com o banco")
    @SecurityRequirements // rota pública: sem token
    public ResponseEntity<Resposta<Map<String, String>>> saude() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Resposta.ok(Map.of("status", "UP")));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new Resposta<>(false, Map.of("status", "DOWN"), "Banco indisponível.", List.of()));
        }
    }
}
