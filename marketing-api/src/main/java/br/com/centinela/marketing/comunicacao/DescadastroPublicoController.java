package br.com.centinela.marketing.comunicacao;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.Resposta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Rota pública de descadastro (E7, norteador §6): {@code GET /public/marketing/descadastro/{token}}
 * mostra a confirmação sem alterar o lead; {@code POST /public/marketing/descadastro/{token}} efetiva
 * o opt-out e registra a revogação de consentimento com IP e User-Agent. Token inválido recebe a
 * mesma resposta neutra para não revelar se o lead existe.
 */
@RestController
@SecurityRequirements
public class DescadastroPublicoController {

    private final DescadastroDeLead descadastro;

    public DescadastroPublicoController(DescadastroDeLead descadastro) {
        this.descadastro = descadastro;
    }

    public record EstadoDescadastro(String status, boolean descadastrado, String mensagem) {
    }

    @GetMapping("/public/marketing/descadastro/{token}")
    @Operation(summary = "Confirmação de descadastro de e-mails de marketing (não efetiva no GET)")
    public ResponseEntity<Resposta<EstadoDescadastro>> exibirConfirmacao(@PathVariable String token) {
        return ResponseEntity.ok(Resposta.ok(new EstadoDescadastro(
                "confirmacao_pendente",
                false,
                "Confirme o descadastro para deixar de receber nossas comunicações de marketing.")));
    }

    @PostMapping("/public/marketing/descadastro/{token}")
    @Operation(summary = "Efetiva o descadastro (opt-out) e registra a revogação do consentimento")
    public ResponseEntity<Resposta<EstadoDescadastro>> confirmarDescadastro(@PathVariable String token,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest requisicao) {
        descadastro.efetivar(token, ip(requisicao), userAgent);
        return ResponseEntity.ok(Resposta.ok(new EstadoDescadastro(
                "descadastrado",
                true,
                "Se o endereço constava na nossa lista, o descadastro foi registrado.")));
    }

    private static String ip(HttpServletRequest requisicao) {
        String encaminhado = requisicao.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",", 2)[0].strip();
        }
        return requisicao.getRemoteAddr();
    }
}
