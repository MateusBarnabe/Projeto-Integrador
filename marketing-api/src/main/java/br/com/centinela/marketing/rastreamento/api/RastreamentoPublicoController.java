package br.com.centinela.marketing.rastreamento.api;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.web.Resposta;
import br.com.centinela.marketing.rastreamento.CliqueNoAnuncio;
import br.com.centinela.marketing.rastreamento.SinaisDaVisita;
import br.com.centinela.marketing.rastreamento.SinaisDoVisitante;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Superfície pública do rastreamento do anúncio (R1, norteador §6), sem token: o tenant vem do código
 * do link ou do id da visita, gerados pelo servidor, nunca de parâmetro. Nada daqui devolve dado de
 * negócio.
 *
 * <ul>
 * <li>{@code GET /public/marketing/c/{codigo}}: o destino do anúncio. Registra a visita e redireciona
 * ({@code 302}) para a landing page. Exceção ao envelope, como o {@code hub.challenge} do Meta: um
 * redirecionamento não tem corpo;</li>
 * <li>{@code POST /public/marketing/visitas/{id}/sinais}: os sinais do script, em {@code text/plain}
 * (o {@code navigator.sendBeacon} não dispara a checagem de CORS) ou JSON;</li>
 * <li>{@code GET /public/marketing/rastreamento.js}: o script que a landing page carrega.</li>
 * </ul>
 */
@RestController
public class RastreamentoPublicoController {

    /** Os sinais são pequenos; acima disto, não vieram do script. */
    static final int TAMANHO_MAXIMO_SINAIS = 8 * 1024;

    private static final Resource SCRIPT = new ClassPathResource("rastreamento/rastreamento.js");

    private final CliqueNoAnuncio clique;
    private final SinaisDaVisita sinais;
    private final ObjectMapper json;

    public RastreamentoPublicoController(CliqueNoAnuncio clique, SinaisDaVisita sinais, ObjectMapper json) {
        this.clique = clique;
        this.sinais = sinais;
        this.json = json;
    }

    @GetMapping("/public/marketing/c/{codigo}")
    @Operation(summary = "Clique no anúncio: registra a visita e redireciona para a landing page")
    public ResponseEntity<Resposta<Void>> clicar(@PathVariable String codigo,
            @RequestHeader(value = "User-Agent", required = false) String userAgent, HttpServletRequest requisicao) {
        Optional<URI> destino = clique.registrar(codigo, userAgent, ip(requisicao));
        return destino
                .map(uri -> ResponseEntity.status(HttpStatus.FOUND).location(uri)
                        .cacheControl(CacheControl.noStore()).<Resposta<Void>>build())
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Resposta.erro("Link não encontrado.")));
    }

    @PostMapping(value = "/public/marketing/visitas/{id}/sinais",
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Sinais do script de rastreamento: tempo, rolagem, cliques e páginas da visita")
    public ResponseEntity<Resposta<Void>> registrarSinais(@PathVariable UUID id,
            @RequestBody(required = false) String corpo) {
        if (corpo == null || corpo.isBlank() || corpo.length() > TAMANHO_MAXIMO_SINAIS) {
            return ResponseEntity.badRequest().body(Resposta.erro("Sinais inválidos."));
        }
        SinaisDoVisitante recebidos;
        try {
            recebidos = json.readValue(corpo, SinaisDoVisitante.class);
        } catch (IOException ilegivel) {
            return ResponseEntity.badRequest().body(Resposta.erro("Sinais inválidos."));
        }
        if (recebidos == null) {
            return ResponseEntity.badRequest().body(Resposta.erro("Sinais inválidos."));
        }
        return switch (sinais.registrar(id, recebidos)) {
            case VISITA_DESCONHECIDA -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Resposta.erro("Visita não encontrada."));
            case REGISTRADO, IGNORADO -> ResponseEntity.ok(Resposta.ok(null));
        };
    }

    @GetMapping(value = "/public/marketing/rastreamento.js", produces = "text/javascript")
    @Operation(summary = "Script de rastreamento que a landing page carrega")
    public ResponseEntity<Resource> script() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .contentType(MediaType.parseMediaType("text/javascript;charset=UTF-8"))
                .body(SCRIPT);
    }

    /** IP de quem clicou: o primeiro do {@code X-Forwarded-For} do gateway, senão o da conexão. Não é guardado. */
    private static String ip(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",", 2)[0].strip();
        }
        return requisicao.getRemoteAddr();
    }
}
