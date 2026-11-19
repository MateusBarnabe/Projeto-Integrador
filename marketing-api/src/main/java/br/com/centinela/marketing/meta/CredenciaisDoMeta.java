package br.com.centinela.marketing.meta;

/**
 * Segredos do Meta Ads de um tenant, já decifrados, só em memória. Nunca vão para log nem para
 * resposta: o {@code toString} só diz o que está configurado.
 *
 * @param appSecret       assina o corpo do webhook ({@code X-Hub-Signature-256})
 * @param verifyToken     confere o handshake do webhook ({@code hub.verify_token})
 * @param pageAccessToken lê o lead na Graph API
 */
public record CredenciaisDoMeta(String appSecret, String verifyToken, String pageAccessToken) {

    public CredenciaisDoMeta {
        appSecret = vazioParaNulo(appSecret);
        verifyToken = vazioParaNulo(verifyToken);
        pageAccessToken = vazioParaNulo(pageAccessToken);
    }

    public static final CredenciaisDoMeta NENHUMA = new CredenciaisDoMeta(null, null, null);

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    @Override
    public String toString() {
        return "CredenciaisDoMeta[appSecret=" + marca(appSecret) + ", verifyToken=" + marca(verifyToken)
                + ", pageAccessToken=" + marca(pageAccessToken) + "]";
    }

    private static String marca(String valor) {
        return valor == null ? "(vazio)" : "***";
    }
}
