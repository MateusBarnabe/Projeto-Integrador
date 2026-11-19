package br.com.centinela.marketing.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AssinaturaDoMetaTest {

    private static final byte[] CORPO = "{\"object\":\"page\"}".getBytes(StandardCharsets.UTF_8);
    // HMAC SHA-256 de CORPO com o segredo "segredo-do-app", calculado fora do Java
    private static final String ASSINATURA =
            "sha256=b89cf00fa3ab5723c2efd023ac5bd399c76c9149c57e16c39a325c8cae233bbb";

    @Test
    void confereAAssinaturaDoCorpoBruto() {
        assertThat(AssinaturaDoMeta.confere(CORPO, ASSINATURA, "segredo-do-app")).isTrue();
        assertThat(AssinaturaDoMeta.cabecalhoPara(CORPO, "segredo-do-app")).isEqualTo(ASSINATURA);
    }

    @Test
    void recusaSegredoErradoCorpoAdulteradoEFormatoInvalido() {
        assertThat(AssinaturaDoMeta.confere(CORPO, ASSINATURA, "outro-segredo")).isFalse();
        assertThat(AssinaturaDoMeta.confere("{\"object\":\"page\" }".getBytes(StandardCharsets.UTF_8), ASSINATURA,
                "segredo-do-app")).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, ASSINATURA.replace("sha256=", "sha1="), "segredo-do-app")).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, "sha256=nao-e-hexa", "segredo-do-app")).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, "sha256=", "segredo-do-app")).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, null, "segredo-do-app")).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, ASSINATURA, null)).isFalse();
        assertThat(AssinaturaDoMeta.confere(CORPO, ASSINATURA, "")).isFalse();
        assertThat(AssinaturaDoMeta.confere(null, ASSINATURA, "segredo-do-app")).isFalse();
    }
}
