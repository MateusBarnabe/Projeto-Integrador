package br.com.centinela.marketing.painel;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import io.swagger.v3.oas.annotations.Operation;

/**
 * API do painel (A4 e R6, norteador §6 e §9.1): o resumo do tenant e o fluxo de eventos em tempo real.
 * O tenant vem só do token; {@code tenantId} na query é ignorado.
 */
@RestController
public class PainelController {

    private static final Duration DURACAO_MINIMA = Duration.ofSeconds(5);

    private final PainelServico painel;
    private final FluxoDoPainel fluxo;
    private final PainelPropriedades propriedades;

    PainelController(PainelServico painel, FluxoDoPainel fluxo, PainelPropriedades propriedades) {
        this.painel = painel;
        this.fluxo = fluxo;
        this.propriedades = propriedades;
    }

    @GetMapping("/api/marketing/painel")
    @PreAuthorize("hasAuthority('marketing.dashboard.ver')")
    @Operation(summary = "Resumo do painel: funil, qualificação, série, campanhas, canais, distribuição e lead 0",
            description = "Filtros: periodo (7d, 30d, 90d ou AAAA-MM-DD,AAAA-MM-DD; padrão 30d), origem "
                    + "(meta_ads ou landing_page; vazio = todas) e campanha (utm_campaign). Guardado em cache por "
                    + "alguns segundos. Os custos por lead e por MQL são estimativas do orçamento mensal.")
    public Resposta<PainelResumo> resumir(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String origem,
            @RequestParam(required = false) String campanha) {
        return Resposta.ok(painel.obter(FiltroPainel.interpretar(periodo, origem, campanha)));
    }

    @GetMapping("/api/marketing/painel/eventos")
    @PreAuthorize("hasAuthority('marketing.dashboard.ver')")
    @Operation(summary = "Fluxo SSE dos fatos dos leads do tenant (text/event-stream)",
            description = "Autenticado pelo cabeçalho Authorization (o front usa fetch, não EventSource). O id de cada "
                    + "mensagem é lead_eventos.sequencia: ao reconectar, envie Last-Event-ID para receber só o que "
                    + "faltou. Eventos: lead_criado, lead_etapa_alterada, lead_qualificado, lead_atribuido e "
                    + "lead_zero_atualizado (sem id). A cada 15 s sem mensagem sai o comentário ': ping'. A conexão "
                    + "fecha quando o token expira; os parâmetros opcionais periodo e campanha valem só para o bloco "
                    + "do lead 0. Acima do limite de conexões por tenant, responde 429.")
    public SseEmitter eventos(
            @RequestHeader(name = "Last-Event-ID", required = false) String ultimoId,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String campanha,
            Authentication autenticacao) {
        FiltroPainel filtro = FiltroPainel.interpretar(periodo, null, campanha);
        return fluxo.abrir(TenantContexto.exigir(), sequenciaOuNulo(ultimoId), filtro, duracaoDaConexao(autenticacao));
    }

    // As respostas de erro saem em JSON mesmo quando o cliente pede text/event-stream

    @ExceptionHandler(FiltroInvalidoException.class)
    ResponseEntity<Resposta<Void>> filtroInvalido(FiltroInvalidoException ex) {
        return json(HttpStatus.BAD_REQUEST, Resposta.erro(ex.getMessage(), ex.erros()));
    }

    @ExceptionHandler(LimiteDeConexoesException.class)
    ResponseEntity<Resposta<Void>> limiteDeConexoes(LimiteDeConexoesException ex) {
        return json(HttpStatus.TOO_MANY_REQUESTS, Resposta.erro(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Resposta<Void>> semPermissao(AccessDeniedException ex) {
        return json(HttpStatus.FORBIDDEN, Resposta.erro("Sem permissão para esta ação."));
    }

    private static ResponseEntity<Resposta<Void>> json(HttpStatus status, Resposta<Void> corpo) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(corpo);
    }

    /** {@code Last-Event-ID} que não é um número positivo é ignorado, como se não tivesse vindo. */
    private static Long sequenciaOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            long sequencia = Long.parseLong(valor.strip());
            return sequencia < 0 ? null : sequencia;
        } catch (NumberFormatException excecao) {
            return null;
        }
    }

    /** Até a expiração do token (o front reabre com o token novo) ou o teto da configuração. */
    private Duration duracaoDaConexao(Authentication autenticacao) {
        Duration teto = propriedades.sse().duracaoMaxima();
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            Instant expira = jwt.getToken().getExpiresAt();
            if (expira != null) {
                Duration restante = Duration.between(Instant.now(), expira);
                if (restante.compareTo(DURACAO_MINIMA) < 0) {
                    return DURACAO_MINIMA;
                }
                return restante.compareTo(teto) < 0 ? restante : teto;
            }
        }
        return teto;
    }
}
