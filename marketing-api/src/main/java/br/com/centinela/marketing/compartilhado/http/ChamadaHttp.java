package br.com.centinela.marketing.compartilhado.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo.Motivo;
import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Transporte de uma chamada a um módulo: monta o pedido, aplica o timeout da fábrica, lê o envelope
 * {@code {success, data, message, errors}} (Contrato §8.2) e converte toda falha em
 * {@link FalhaNaChamadaDeModulo}. Não sabe de token: quem autentica é o {@link ClienteDeModulo}.
 *
 * <p>O log traz só módulo, método, o caminho <em>antes</em> de preencher as variáveis, status e
 * duração. Nunca cabeçalho, corpo, token ou valor de parâmetro.
 */
final class ChamadaHttp {

    private static final Logger log = LoggerFactory.getLogger(ChamadaHttp.class);
    private static final byte[] VAZIO = new byte[0];

    private final ModuloDestino modulo;
    private final RestClient http;
    private final ObjectMapper json;

    ChamadaHttp(ModuloDestino modulo, String baseUrl, ClientHttpRequestFactory fabrica, ObjectMapper json) {
        this.modulo = modulo;
        this.json = json;
        this.http = baseUrl == null || baseUrl.isBlank() ? null
                : RestClient.builder()
                        .baseUrl(semBarraNoFim(baseUrl.strip()))
                        .requestFactory(fabrica)
                        .build();
    }

    ModuloDestino modulo() {
        return modulo;
    }

    /**
     * Executa a chamada e devolve o {@code data} do envelope convertido em {@code tipo} (nulo quando
     * {@code tipo} é nulo, quando não há corpo ou quando {@code data} é nulo).
     */
    <T> T executar(HttpMethod metodo, String caminho, Object[] variaveis, Object corpo, JavaType tipo,
            Consumer<HttpHeaders> cabecalhos) {
        if (http == null) {
            throw falha(Motivo.NAO_CONFIGURADO, null, "Endereço do módulo não configurado.", List.of(), null);
        }
        byte[] conteudo = serializar(corpo);
        long inicio = System.nanoTime();
        Recebido recebido;
        try {
            RestClient.RequestBodySpec pedido = http.method(metodo)
                    .uri(caminho, variaveis == null ? new Object[0] : variaveis)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        String requestId = MDC.get(RequestIdFilter.CHAVE_MDC);
                        if (requestId != null) {
                            h.set(RequestIdFilter.CABECALHO, requestId);
                        }
                        cabecalhos.accept(h);
                    });
            if (conteudo != null) {
                pedido.contentType(MediaType.APPLICATION_JSON).body(conteudo);
            }
            recebido = pedido.exchange((req, res) -> new Recebido(res.getStatusCode().value(), ler(res.getBody())));
        } catch (RestClientException excecao) {
            log.warn("Chamada ao {} {} {} sem resposta em {} ms: {}", modulo.codigo(), metodo, caminho,
                    ms(inicio), excecao.getClass().getSimpleName());
            throw falha(Motivo.INDISPONIVEL, null, null, List.of(), excecao);
        }

        log.debug("Chamada ao {} {} {} respondeu {} em {} ms", modulo.codigo(), metodo, caminho,
                recebido.status(), ms(inicio));
        try {
            return interpretar(recebido, tipo);
        } catch (FalhaNaChamadaDeModulo falha) {
            log.warn("Chamada ao {} {} {} falhou: {} (HTTP {})", modulo.codigo(), metodo, caminho,
                    falha.motivo(), recebido.status());
            throw falha;
        }
    }

    private <T> T interpretar(Recebido recebido, JavaType tipo) {
        int status = recebido.status();
        JsonNode envelope = envelope(recebido.corpo());

        if (status >= 200 && status < 300) {
            if (recebido.corpo().length == 0) {
                return null;
            }
            if (envelope == null) {
                throw falha(Motivo.RESPOSTA_INVALIDA, status, null, List.of(), null);
            }
            if (!envelope.get("success").asBoolean()) {
                throw falha(Motivo.RESPOSTA_INVALIDA, status, mensagem(envelope), erros(envelope), null);
            }
            JsonNode data = envelope.get("data");
            if (tipo == null || data == null || data.isNull()) {
                return null;
            }
            try {
                return json.readerFor(tipo).readValue(data);
            } catch (IOException | IllegalArgumentException excecao) {
                throw falha(Motivo.RESPOSTA_INVALIDA, status, null, List.of(), excecao);
            }
        }

        Motivo motivo = switch (status) {
            case 401 -> Motivo.NAO_AUTENTICADO;
            case 403 -> Motivo.SEM_PERMISSAO;
            case 404 -> Motivo.NAO_ENCONTRADO;
            default -> status >= 500 ? Motivo.INDISPONIVEL
                    : status >= 400 ? Motivo.REJEITADA
                    : Motivo.RESPOSTA_INVALIDA;
        };
        throw falha(motivo, status, envelope == null ? null : mensagem(envelope),
                envelope == null ? List.of() : erros(envelope), null);
    }

    /** O corpo lido como envelope, ou nulo quando não é JSON com {@code success} booleano. */
    private JsonNode envelope(byte[] corpo) {
        if (corpo.length == 0) {
            return null;
        }
        try {
            JsonNode raiz = json.readTree(corpo);
            return raiz != null && raiz.isObject() && raiz.path("success").isBoolean() ? raiz : null;
        } catch (IOException excecao) {
            return null;
        }
    }

    private static String mensagem(JsonNode envelope) {
        JsonNode mensagem = envelope.get("message");
        return mensagem == null || mensagem.isNull() ? null : mensagem.asText();
    }

    private static List<ErroCampo> erros(JsonNode envelope) {
        JsonNode lista = envelope.get("errors");
        if (lista == null || !lista.isArray()) {
            return List.of();
        }
        List<ErroCampo> erros = new ArrayList<>();
        for (JsonNode item : lista) {
            if (item.isObject()) {
                erros.add(new ErroCampo(texto(item, "campo"), texto(item, "codigo"), texto(item, "detalhe")));
            }
        }
        return erros;
    }

    private static String texto(JsonNode no, String campo) {
        JsonNode valor = no.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private byte[] serializar(Object corpo) {
        if (corpo == null) {
            return null;
        }
        try {
            return json.writeValueAsBytes(corpo);
        } catch (IOException excecao) {
            throw new IllegalArgumentException("Corpo da chamada ao módulo " + modulo.codigo()
                    + " não pôde ser convertido em JSON.", excecao);
        }
    }

    private static byte[] ler(java.io.InputStream corpo) throws IOException {
        return corpo == null ? VAZIO : corpo.readAllBytes();
    }

    private FalhaNaChamadaDeModulo falha(Motivo motivo, Integer status, String mensagem, List<ErroCampo> erros,
            Throwable causa) {
        return new FalhaNaChamadaDeModulo(modulo, motivo, status, mensagem, erros, causa);
    }

    private static long ms(long inicio) {
        return (System.nanoTime() - inicio) / 1_000_000;
    }

    private static String semBarraNoFim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record Recebido(int status, byte[] corpo) {
    }
}
