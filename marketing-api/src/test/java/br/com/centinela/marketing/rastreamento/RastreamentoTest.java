package br.com.centinela.marketing.rastreamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Regras do rastreamento sem banco: leitura do User-Agent, URL de destino do link, sinais e vínculo. */
class RastreamentoTest {

    @Test
    void leUserAgentsComuns() {
        assertThat(LeitorDeUserAgent.ler("Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like "
                + "Gecko) SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36"))
                .isEqualTo(new LeitorDeUserAgent.Aparelho("smartphone", "Android", "Samsung Internet"));
        assertThat(LeitorDeUserAgent.ler("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like "
                + "Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"))
                .isEqualTo(new LeitorDeUserAgent.Aparelho("desktop", "Windows", "Edge"));
        assertThat(LeitorDeUserAgent.ler("Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, "
                + "like Gecko) CriOS/130.0 Mobile/15E148 Safari/604.1"))
                .isEqualTo(new LeitorDeUserAgent.Aparelho("tablet", "iOS", "Chrome"));
        assertThat(LeitorDeUserAgent.ler("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, "
                + "like Gecko) Version/17.5 Safari/605.1.15"))
                .isEqualTo(new LeitorDeUserAgent.Aparelho("desktop", "macOS", "Safari"));
        assertThat(LeitorDeUserAgent.ler(null)).isEqualTo(new LeitorDeUserAgent.Aparelho("outro", "outro", "outro"));
    }

    @Test
    void destinoAcrescentaUtmEVisitaSemRepetirOQueAUrlJaTem() {
        LinkCampanha link = new LinkCampanha("Teste", "https://empresa-a.dev/oferta?utm_source=meta&x=1#form",
                new LinkCampanha.Utm("google", "cpc", "black friday", null, null));
        UUID visita = UUID.fromString("7a000000-0000-4000-8000-000000000001");

        assertThat(link.destinoPara(visita)).hasToString("https://empresa-a.dev/oferta?utm_source=meta&x=1"
                + "&utm_medium=cpc&utm_campaign=black+friday&mkt_visita=" + visita + "#form");
        assertThat(link.destinoPara(null)).hasToString("https://empresa-a.dev/oferta?utm_source=meta&x=1"
                + "&utm_medium=cpc&utm_campaign=black+friday#form");
        assertThat(link.getCodigo()).hasSize(LinkCampanha.TAMANHO_CODIGO).matches("[a-z2-9]+");
    }

    @Test
    void destinoSoAceitaHttpEHttps() {
        assertThatThrownBy(() -> new LinkCampanha("x", "javascript:alert(1)", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkCampanha("x", "//sem-esquema.dev", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LinkCampanha("x", "https://exemplo dev/ com espaço", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sinaisSaemLimpos() {
        SinaisDoVisitante limpos = new SinaisDoVisitante(999_999, 140,
                java.util.stream.IntStream.range(0, 50).mapToObj(i -> "botao-" + i).toList(),
                List.of("/contato?email=ana@exemplo.com", "/#precos"), " pt-BR ", null, "x".repeat(200)).limpo();

        assertThat(limpos.tempoNaPaginaSegundos()).isEqualTo(SinaisDoVisitante.TEMPO_MAXIMO);
        assertThat(limpos.profundidadeRolagem()).isEqualTo(100);
        assertThat(limpos.elementosClicados()).hasSize(SinaisDoVisitante.ITENS_MAXIMOS);
        assertThat(limpos.paginasVisitadas()).containsExactly("/contato", "/#precos");
        assertThat(limpos.idioma()).isEqualTo("pt-BR");
        assertThat(limpos.tela()).hasSize(SinaisDoVisitante.TAMANHO_TEXTO);
    }

    @Test
    void achaAVisitaNaPaginaDeEntrada() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(VinculoDeVisitas.idsDeVisita(java.util.Arrays.asList(
                "https://empresa-a.dev/?utm_source=google&mkt_visita=" + a + "#form",
                null,
                "https://empresa-a.dev/?mkt_visita=" + b,
                "https://empresa-a.dev/?mkt_visita=" + a,
                "https://empresa-a.dev/?mkt_visita=adulterado")))
                .containsExactly(a, b);
    }
}
