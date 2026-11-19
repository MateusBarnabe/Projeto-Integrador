package br.com.centinela.marketing.meta;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Confere o {@code X-Hub-Signature-256} do webhook do Meta: {@code sha256=} e o HMAC SHA-256, em
 * hexadecimal, do <strong>corpo bruto</strong> calculado com o App Secret do tenant. A comparação leva
 * o mesmo tempo qualquer que seja o ponto em que as assinaturas diferem.
 */
final class AssinaturaDoMeta {

    static final String CABECALHO = "X-Hub-Signature-256";
    private static final String PREFIXO = "sha256=";
    private static final String ALGORITMO = "HmacSHA256";

    private AssinaturaDoMeta() {
    }

    static boolean confere(byte[] corpo, String cabecalho, String appSecret) {
        if (corpo == null || cabecalho == null || appSecret == null || appSecret.isEmpty()
                || !cabecalho.startsWith(PREFIXO)) {
            return false;
        }
        byte[] recebida;
        try {
            recebida = HexFormat.of().parseHex(cabecalho.substring(PREFIXO.length()).strip());
        } catch (IllegalArgumentException naoEhHexadecimal) {
            return false;
        }
        return MessageDigest.isEqual(calcular(corpo, appSecret), recebida);
    }

    /** O HMAC SHA-256 do corpo; usado também pelos testes para assinar como o Meta. */
    static byte[] calcular(byte[] corpo, String appSecret) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return mac.doFinal(corpo);
        } catch (GeneralSecurityException impossivel) {
            throw new IllegalStateException("HMAC SHA-256 indisponível.", impossivel);
        }
    }

    /** O valor do cabeçalho para o corpo e o segredo. */
    static String cabecalhoPara(byte[] corpo, String appSecret) {
        return PREFIXO + HexFormat.of().formatHex(calcular(corpo, appSecret));
    }
}
