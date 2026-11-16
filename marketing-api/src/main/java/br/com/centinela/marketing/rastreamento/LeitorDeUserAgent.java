package br.com.centinela.marketing.rastreamento;

import java.util.Locale;

/**
 * Tipo de dispositivo, sistema e navegador a partir do cabeçalho {@code User-Agent} do clique no
 * anúncio (R1). Só o resultado é guardado; o User-Agent bruto não (norteador §3). Cobre os aparelhos
 * comuns; o que não reconhece vira {@code outro}.
 */
public final class LeitorDeUserAgent {

    public static final String SMARTPHONE = "smartphone";
    public static final String TABLET = "tablet";
    public static final String DESKTOP = "desktop";
    public static final String OUTRO = "outro";

    private LeitorDeUserAgent() {
    }

    /** O que se sabe do aparelho; campos desconhecidos ficam {@code outro}. */
    public record Aparelho(String tipoDispositivo, String sistema, String navegador) {
    }

    public static Aparelho ler(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new Aparelho(OUTRO, OUTRO, OUTRO);
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return new Aparelho(tipo(ua), sistema(ua), navegador(ua));
    }

    private static String tipo(String ua) {
        if (ua.contains("ipad") || ua.contains("tablet") || (ua.contains("android") && !ua.contains("mobile"))) {
            return TABLET;
        }
        if (ua.contains("iphone") || ua.contains("ipod") || ua.contains("mobile") || ua.contains("android")) {
            return SMARTPHONE;
        }
        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("x11") || ua.contains("linux")
                || ua.contains("cros")) {
            return DESKTOP;
        }
        return OUTRO;
    }

    private static String sistema(String ua) {
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            return "iOS";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("macintosh") || ua.contains("mac os x")) {
            return "macOS";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("linux") || ua.contains("x11")) {
            return "Linux";
        }
        return OUTRO;
    }

    // A ordem importa: Edge, Opera e Samsung também dizem "chrome"; o Chrome também diz "safari"
    private static String navegador(String ua) {
        if (ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("samsungbrowser")) {
            return "Samsung Internet";
        }
        if (ua.contains("firefox/") || ua.contains("fxios/")) {
            return "Firefox";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "Chrome";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return OUTRO;
    }
}
