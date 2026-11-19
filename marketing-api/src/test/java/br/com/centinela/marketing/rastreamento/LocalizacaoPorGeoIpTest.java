package br.com.centinela.marketing.rastreamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import br.com.centinela.marketing.rastreamento.LocalizacaoPorIp.Localizacao;

/**
 * A localização por base GeoIP local (R4), sem banco nem Docker. Usa a base de teste da MaxMind
 * ({@code src/test/resources/geoip}, ver o LEIA-ME de lá), não a GeoLite2 de produção.
 */
class LocalizacaoPorGeoIpTest {

    private static final String PROPRIEDADE = "marketing.rastreamento.geoip-arquivo";

    private LocalizacaoPorGeoIp geoip;

    @BeforeEach
    void abreABaseDeTeste() throws Exception {
        geoip = new LocalizacaoPorGeoIp(baseDeTeste().toString());
    }

    @AfterEach
    void fechaABase() throws IOException {
        geoip.fechar();
    }

    @Test
    void localizaACidadeDeUmIpConhecido() {
        // pt-BR primeiro, en de reserva: a base de teste traz "Londres" em português
        assertThat(geoip.localizar("81.2.69.142"))
                .contains(new Localizacao("Londres", "ENG", "GB", false));
        assertThat(geoip.localizar("216.160.83.56"))
                .contains(new Localizacao("Milton", "WA", "US", false));
        assertThat(geoip.localizar("2001:480::1"))
                .contains(new Localizacao("San Diego", "CA", "US", false));
    }

    @Test
    void ipSemCidadeNaBaseDevolveSoOQueTem() {
        assertThat(geoip.localizar("2001:218::1")).contains(new Localizacao(null, null, "JP", false));
    }

    @Test
    void ipForaDaBaseDevolveVazio() {
        assertThat(geoip.localizar("8.8.8.8")).isEmpty();
    }

    @Test
    void ipPrivadoOuLocalDevolveVazio() {
        for (String ip : new String[] { "10.0.0.5", "172.16.4.9", "192.168.1.20", "127.0.0.1", "169.254.1.1",
                "0.0.0.0", "100.64.0.1", "224.0.0.1", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1" }) {
            assertThat(geoip.localizar(ip)).as(ip).isEmpty();
        }
    }

    @Test
    void textoQueNaoELiteralDeIpDevolveVazioSemResolverNome() {
        // "dead.beef" e "localhost" virariam consulta de DNS em InetAddress.getByName: não podem chegar lá
        for (String ip : new String[] { null, "", "   ", "localhost", "exemplo.com", "dead.beef", "999.1.1.1",
                "81.2.69", "81.2.69.142.7", "nao-e-ip:::x", "'; DROP TABLE" }) {
            assertThat(geoip.localizar(ip)).as(String.valueOf(ip)).isEmpty();
        }
    }

    @Test
    void ignoraEspacosEZonaDoIpv6() {
        assertThat(geoip.localizar(" 81.2.69.142 ")).isPresent();
        assertThat(geoip.localizar("2001:480::1%eth0")).isPresent();
    }

    @Test
    void semArquivoConfiguradoVaiASimulada() {
        new ApplicationContextRunner().withUserConfiguration(LocalizacaoSimulada.class, LocalizacaoPorGeoIp.class)
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(LocalizacaoSimulada.class).doesNotHaveBean(LocalizacaoPorGeoIp.class);
                    assertThat(contexto.getBean(LocalizacaoPorIp.class).localizar("81.2.69.142"))
                            .contains(new Localizacao("Goiânia", "GO", "BR", true));
                });
    }

    @Test
    void propriedadeVaziaVaiASimulada() {
        new ApplicationContextRunner().withUserConfiguration(LocalizacaoSimulada.class, LocalizacaoPorGeoIp.class)
                .withPropertyValues(PROPRIEDADE + "=")
                .run(contexto -> assertThat(contexto.getBean(LocalizacaoPorIp.class))
                        .isInstanceOf(LocalizacaoSimulada.class));
    }

    @Test
    void arquivoQueNaoExisteVaiASimuladaESobeNormalmente(@TempDir Path pasta) {
        new ApplicationContextRunner().withUserConfiguration(LocalizacaoSimulada.class, LocalizacaoPorGeoIp.class)
                .withPropertyValues(PROPRIEDADE + "=" + pasta.resolve("nao-existe.mmdb"))
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed().doesNotHaveBean(LocalizacaoPorGeoIp.class);
                    assertThat(contexto.getBean(LocalizacaoPorIp.class)).isInstanceOf(LocalizacaoSimulada.class);
                });
    }

    @Test
    void comArquivoAGeoIpPassaAFrenteDaSimulada() throws Exception {
        new ApplicationContextRunner().withUserConfiguration(LocalizacaoSimulada.class, LocalizacaoPorGeoIp.class)
                .withPropertyValues(PROPRIEDADE + "=" + baseDeTeste())
                .run(contexto -> {
                    assertThat(contexto).hasNotFailed().hasSingleBean(LocalizacaoPorGeoIp.class);
                    // as duas existem, mas a injeção por tipo escolhe a GeoIP (@Primary)
                    assertThat(contexto.getBean(LocalizacaoPorIp.class)).isInstanceOf(LocalizacaoPorGeoIp.class);
                    assertThat(contexto.getBean(LocalizacaoPorIp.class).localizar("81.2.69.142"))
                            .contains(new Localizacao("Londres", "ENG", "GB", false));
                    // IP interno: a simulada devolveria Goiânia, a GeoIP não localiza
                    assertThat(contexto.getBean(LocalizacaoPorIp.class).localizar("192.168.0.10")).isEmpty();
                });
    }

    @Test
    void arquivoQueNaoEUmaBaseDerrubaASubidaComMensagemClara() throws IOException {
        // fora do @TempDir: no Windows a biblioteca segura o mapeamento do arquivo inválido até o GC
        Path lixo = Files.createTempFile("lixo-geoip", ".mmdb");
        lixo.toFile().deleteOnExit();
        Files.writeString(lixo, "isto não é uma base mmdb");

        assertThatThrownBy(() -> new LocalizacaoPorGeoIp(lixo.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RASTREAMENTO_GEOIP_ARQUIVO");
        new ApplicationContextRunner().withUserConfiguration(LocalizacaoSimulada.class, LocalizacaoPorGeoIp.class)
                .withPropertyValues(PROPRIEDADE + "=" + lixo)
                .run(contexto -> assertThat(contexto).hasFailed());
    }

    private static Path baseDeTeste() throws URISyntaxException {
        return Path.of(LocalizacaoPorGeoIpTest.class.getResource("/geoip/GeoLite2-City-Test.mmdb").toURI());
    }
}
