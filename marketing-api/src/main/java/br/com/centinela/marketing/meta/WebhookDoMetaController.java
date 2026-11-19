package br.com.centinela.marketing.meta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Webhook público do Meta Ads (E3, norteador §6): {@code /public/marketing/{subdominio}/meta/leads}. O
 * subdomínio é o do tenant, resolvido no identity; trocá-lo não dá acesso a outro tenant, porque a
 * assinatura só confere com o App Secret daquele.
 *
 * <ul>
 * <li>{@code GET}: handshake de configuração do webhook. {@code hub.mode=subscribe} e
 * {@code hub.verify_token} igual ao do tenant devolvem o {@code hub.challenge} em <strong>texto
 * puro</strong>, exceção ao envelope como o {@code 302} do link de campanha (pendente com o Grupo 2);</li>
 * <li>{@code POST}: confere o {@code X-Hub-Signature-256} sobre o corpo bruto, grava os ids no buffer e
 * responde {@code 200} na hora. A Graph API só é chamada depois, pelo job 4.1.</li>
 * </ul>
 *
 * <p>Subdomínio desconhecido, ou tenant sem o segredo necessário, responde o mesmo {@code 404}, sem dizer
 * qual dos dois. Nada de segredo, token ou corpo vai para log.
 */
@RestController
public class WebhookDoMetaController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDoMetaController.class);

    /** O desafio do Meta é numérico; o padrão largo só impede refletir texto livre. */
    private static final Pattern DESAFIO = Pattern.compile("^[0-9A-Za-z_-]{1,200}$");

    private final TenantsDoWebhook tenants;
    private final CredenciaisDoTenant credenciais;
    private final RecebimentoDoWebhook recebimento;
    private final MetaPropriedades propriedades;

    WebhookDoMetaController(TenantsDoWebhook tenants, CredenciaisDoTenant credenciais,
            RecebimentoDoWebhook recebimento, MetaPropriedades propriedades) {
        this.tenants = tenants;
        this.credenciais = credenciais;
        this.recebimento = recebimento;
        this.propriedades = propriedades;
    }

    @GetMapping("/public/marketing/{subdominio}/meta/leads")
    @Operation(summary = "Handshake do webhook do Meta: devolve o hub.challenge em texto puro")
    public ResponseEntity<?> verificar(@PathVariable String subdominio,
            @RequestParam(name = "hub.mode", required = false) String modo,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        Optional<UUID> tenant;
        try {
            tenant = tenants.resolver(subdominio);
        } catch (FalhaNaChamadaDeModulo falha) {
            return identityIndisponivel(falha);
        }
        if (tenant.isEmpty()) {
            return naoEncontrado();
        }
        String esperado = credenciais.doTenant(tenant.get()).verifyToken();
        if (esperado == null) {
            return naoEncontrado();
        }
        if (!"subscribe".equals(modo) || token == null || !iguais(esperado, token)) {
            LOG.warn("Handshake do webhook do Meta recusado no tenant {}", tenant.get());
            return erro(HttpStatus.FORBIDDEN, "Verificação recusada.");
        }
        if (desafio == null || !DESAFIO.matcher(desafio).matches()) {
            return erro(HttpStatus.BAD_REQUEST, "hub.challenge inválido.");
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).cacheControl(CacheControl.noStore())
                .body(desafio);
    }

    @PostMapping("/public/marketing/{subdominio}/meta/leads")
    @Operation(summary = "Webhook do Meta: grava o leadgen_id no buffer, depois de conferir a assinatura HMAC")
    public ResponseEntity<Resposta<Void>> receber(@PathVariable String subdominio,
            @RequestHeader(value = AssinaturaDoMeta.CABECALHO, required = false) String assinatura,
            HttpServletRequest requisicao) throws IOException {
        Optional<UUID> tenant;
        try {
            tenant = tenants.resolver(subdominio);
        } catch (FalhaNaChamadaDeModulo falha) {
            return identityIndisponivel(falha);
        }
        if (tenant.isEmpty()) {
            return naoEncontrado();
        }
        String appSecret = credenciais.doTenant(tenant.get()).appSecret();
        if (appSecret == null) {
            return naoEncontrado();
        }

        // O corpo bruto é o que o Meta assinou: lê os bytes, sem passar por conversão de JSON
        int teto = propriedades.webhook().tamanhoMaximoCorpo();
        byte[] corpo = requisicao.getInputStream().readNBytes(teto + 1);
        if (corpo.length > teto) {
            return erro(HttpStatus.PAYLOAD_TOO_LARGE, "Corpo grande demais.");
        }
        if (!AssinaturaDoMeta.confere(corpo, assinatura, appSecret)) {
            LOG.warn("Webhook do Meta com assinatura inválida no tenant {}", tenant.get());
            return erro(HttpStatus.UNAUTHORIZED, "Assinatura inválida.");
        }
        try {
            recebimento.receber(tenant.get(), corpo);
        } catch (RecebimentoDoWebhook.CorpoInvalidoException ilegivel) {
            return erro(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido.");
        }
        return ResponseEntity.ok(Resposta.ok(null));
    }

    /** Comparação em tempo constante, para o token não ser descoberto byte a byte. */
    private static boolean iguais(String esperado, String recebido) {
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseEntity<Resposta<Void>> naoEncontrado() {
        return erro(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
    }

    /** O Meta reentrega o que não recebeu {@code 2xx}: com o identity fora do ar, melhor {@code 503} que perder o lead. */
    private static ResponseEntity<Resposta<Void>> identityIndisponivel(FalhaNaChamadaDeModulo falha) {
        LOG.warn("Webhook do Meta sem resolver o tenant: {}", falha.motivo());
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "Serviço temporariamente indisponível.");
    }

    // Tipo JSON fixo: o Meta pode pedir text/plain, e o erro não pode cair na negociação de conteúdo
    private static ResponseEntity<Resposta<Void>> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(Resposta.erro(mensagem));
    }
}
