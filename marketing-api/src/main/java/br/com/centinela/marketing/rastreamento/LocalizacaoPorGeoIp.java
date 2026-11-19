package br.com.centinela.marketing.rastreamento;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import jakarta.annotation.PreDestroy;

/**
 * Localização real do IP do clique (R4): lê uma base GeoIP2/GeoLite2 City <b>local</b>, em formato
 * {@code .mmdb}, sem chamar serviço externo. O IP não é guardado nem escrito em log: só a cidade, a
 * UF e o país que saem dele, e só na visita.
 *
 * <p>Só existe quando {@code marketing.rastreamento.geoip-arquivo} (variável
 * {@code RASTREAMENTO_GEOIP_ARQUIVO}) aponta para um arquivo que existe; nesse caso ela é a
 * {@code @Primary} e a {@link LocalizacaoSimulada} deixa de ser usada. Sem o arquivo, o módulo sobe
 * como antes, com a simulada. Um arquivo que existe mas não é uma base válida derruba a subida, com
 * a mensagem dizendo qual propriedade corrigir (melhor do que localizar tudo errado em silêncio).
 *
 * <p>A base GeoLite2 não vai para o repositório (licença da MaxMind): cada ambiente baixa a sua e a
 * mantém atualizada. IP privado, de loopback, local ou que a base não conheça devolve vazio.
 */
@Component
@Primary
@Conditional(LocalizacaoPorGeoIp.ArquivoConfigurado.class)
class LocalizacaoPorGeoIp implements LocalizacaoPorIp {

    private static final Logger log = LoggerFactory.getLogger(LocalizacaoPorGeoIp.class);

    /** IPv4 em notação decimal pontuada: evita que um texto qualquer vire consulta de DNS. */
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    /** IPv6 (inclusive com IPv4 embutido): só hexadecimais, dois-pontos e ponto, com ao menos um ":". */
    private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:.]*:[0-9a-fA-F:.]*");

    private final DatabaseReader leitor;

    LocalizacaoPorGeoIp(@Value("${marketing.rastreamento.geoip-arquivo}") String arquivo) {
        try {
            // pt-BR primeiro, porque o nome da cidade vai para a tela do vendedor; en como reserva
            this.leitor = new DatabaseReader.Builder(new File(arquivo.trim()))
                    .locales(List.of("pt-BR", "en")).build();
        } catch (IOException e) {
            throw new IllegalStateException("A base GeoIP de marketing.rastreamento.geoip-arquivo "
                    + "(RASTREAMENTO_GEOIP_ARQUIVO) não pôde ser aberta; use um arquivo .mmdb City válido ou "
                    + "deixe a propriedade vazia para usar a localização simulada", e);
        }
        log.info("Localização por IP: base GeoIP local carregada ({})", this.leitor.metadata().databaseType());
    }

    @Override
    public Optional<Localizacao> localizar(String ip) {
        Optional<InetAddress> endereco = enderecoPublico(ip);
        if (endereco.isEmpty()) {
            return Optional.empty();
        }
        try {
            return leitor.tryCity(endereco.get()).flatMap(LocalizacaoPorGeoIp::converter);
        } catch (IOException | GeoIp2Exception | RuntimeException e) {
            // nunca o IP no log: só o tipo da falha
            log.warn("Localização por IP: falha ao consultar a base GeoIP ({})", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @PreDestroy
    void fechar() throws IOException {
        leitor.close();
    }

    private static Optional<Localizacao> converter(CityResponse resposta) {
        String cidade = vazioParaNulo(resposta.city().name());
        String uf = vazioParaNulo(resposta.mostSpecificSubdivision().isoCode());
        String pais = vazioParaNulo(resposta.country().isoCode());
        if (cidade == null && uf == null && pais == null) {
            return Optional.empty();
        }
        return Optional.of(new Localizacao(cidade, uf, pais, false));
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    /**
     * Interpreta o texto como IP literal (sem nunca resolver nome) e descarta o que a base não
     * localizaria: privado, loopback, link-local, multicast, "qualquer", CGNAT e ULA do IPv6.
     */
    static Optional<InetAddress> enderecoPublico(String ip) {
        if (ip == null) {
            return Optional.empty();
        }
        String texto = ip.trim();
        int zona = texto.indexOf('%');
        if (zona >= 0) {
            texto = texto.substring(0, zona);
        }
        if (!IPV4.matcher(texto).matches() && !IPV6.matcher(texto).matches()) {
            return Optional.empty();
        }
        InetAddress endereco;
        try {
            endereco = InetAddress.getByName(texto);
        } catch (UnknownHostException e) {
            return Optional.empty();
        }
        if (endereco.isAnyLocalAddress() || endereco.isLoopbackAddress() || endereco.isLinkLocalAddress()
                || endereco.isSiteLocalAddress() || endereco.isMulticastAddress() || faixaReservada(endereco)) {
            return Optional.empty();
        }
        return Optional.of(endereco);
    }

    /** CGNAT (100.64.0.0/10) e ULA do IPv6 (fc00::/7), que o JDK não classifica como privados. */
    private static boolean faixaReservada(InetAddress endereco) {
        byte[] bytes = endereco.getAddress();
        if (bytes.length == 4) {
            return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 0x40;
        }
        return (bytes[0] & 0xFE) == 0xFC;
    }

    /** Liga a classe só quando o arquivo da base está configurado e existe. */
    static class ArquivoConfigurado implements Condition {

        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadados) {
            String arquivo = contexto.getEnvironment().getProperty("marketing.rastreamento.geoip-arquivo");
            if (arquivo == null || arquivo.isBlank()) {
                return false;
            }
            boolean existe = Files.isRegularFile(Path.of(arquivo.trim()));
            if (!existe) {
                log.warn("Localização por IP: marketing.rastreamento.geoip-arquivo aponta para um arquivo que "
                        + "não existe; seguindo com a localização simulada");
            }
            return existe;
        }
    }
}
