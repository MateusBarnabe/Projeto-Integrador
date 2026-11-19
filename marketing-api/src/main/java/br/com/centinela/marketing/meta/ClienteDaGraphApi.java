package br.com.centinela.marketing.meta;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.http.IntegracaoPropriedades;
import br.com.centinela.marketing.meta.FalhaNaGraphApi.Motivo;
import jakarta.annotation.PreDestroy;

/**
 * Cliente da Graph API do Meta (E4, norteador §4.1): {@code GET {base}/{versão}/{leadgen_id}} com o
 * token da página do tenant. A Graph API não é um módulo da plataforma: não usa o envelope, o token de
 * serviço nem o {@code X-Tenant-Id}, por isso não passa por {@code ClientesDeModulos}. Fica com o mesmo
 * timeout dele (3 s, conexão e resposta).
 *
 * <p>O token vai no cabeçalho {@code Authorization: Bearer}, nunca na URL, para não aparecer em log de
 * proxy ou de acesso. O log traz só o motivo, o status e o código de erro do Meta: nem o token, nem o
 * corpo (o lead), nem a mensagem de erro (que pode citá-lo).
 *
 * <p>O endereço vem de {@code marketing.integracao.meta-graph-base-url} ({@code META_GRAPH_BASE_URL},
 * padrão {@code https://graph.facebook.com}); a versão, de {@code marketing.meta.graph-versao}.
 */
@Component
public class ClienteDaGraphApi {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteDaGraphApi.class);

    private final HttpClient http;
    private final RestClient cliente;
    private final String versao;
    private final ObjectMapper json;

    ClienteDaGraphApi(IntegracaoPropriedades integracao, MetaPropriedades meta, ObjectMapper json,
            @Value("${marketing.integracao.meta-graph-base-url:https://graph.facebook.com}") String baseUrl) {
        this.json = json;
        this.versao = meta.graphVersaoNoCaminho();
        this.http = HttpClient.newBuilder()
                .connectTimeout(integracao.timeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(integracao.timeout());
        String base = baseUrl.strip();
        this.cliente = RestClient.builder()
                .baseUrl(base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                .requestFactory(fabrica)
                .build();
    }

    @PreDestroy
    void fechar() {
        http.close();
    }

    /**
     * Busca o lead do formulário instantâneo.
     *
     * @param leadgenId       id que o webhook trouxe
     * @param tokenDaPagina   {@code meta_page_access_token} do tenant, decifrado
     * @throws FalhaNaGraphApi timeout, Meta fora do ar, token recusado, lead inexistente ou resposta inválida
     */
    public LeadDoMeta buscarLead(String leadgenId, String tokenDaPagina) {
        Objects.requireNonNull(leadgenId, "leadgenId");
        Objects.requireNonNull(tokenDaPagina, "tokenDaPagina");
        Recebido recebido;
        try {
            recebido = cliente.method(HttpMethod.GET)
                    .uri("/{versao}/{leadgenId}", versao, leadgenId)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(tokenDaPagina))
                    .exchange((req, res) -> new Recebido(res.getStatusCode().value(), res.getBody().readAllBytes()));
        } catch (RestClientException semResposta) {
            LOG.warn("Graph API {} {} sem resposta: {}", versao, "/{leadgenId}", semResposta.getClass().getSimpleName());
            throw new FalhaNaGraphApi(Motivo.INDISPONIVEL, null, null, semResposta);
        }
        if (recebido == null) {
            throw new FalhaNaGraphApi(Motivo.INDISPONIVEL, null, null, null);
        }
        return interpretar(recebido);
    }

    private LeadDoMeta interpretar(Recebido recebido) {
        int status = recebido.status();
        JsonNode corpo = ler(recebido.corpo());
        if (status >= 200 && status < 300) {
            if (corpo == null || !corpo.isObject()) {
                throw falha(Motivo.RESPOSTA_INVALIDA, status, null);
            }
            return LeadDoMeta.de(corpo);
        }
        Integer codigo = corpo == null || !corpo.path("error").path("code").isInt() ? null
                : corpo.path("error").path("code").asInt();
        Motivo motivo = status >= 500 ? Motivo.INDISPONIVEL
                : status == 401 || status == 403 || Integer.valueOf(190).equals(codigo) ? Motivo.NAO_AUTORIZADO
                : Motivo.REJEITADA;
        throw falha(motivo, status, codigo);
    }

    private FalhaNaGraphApi falha(Motivo motivo, int status, Integer codigo) {
        LOG.warn("Graph API {} respondeu {}: {}{}", versao, status, motivo, codigo == null ? "" : " (código " + codigo + ")");
        return new FalhaNaGraphApi(motivo, status, codigo, null);
    }

    private JsonNode ler(byte[] corpo) {
        if (corpo == null || corpo.length == 0) {
            return null;
        }
        try {
            return json.readTree(corpo);
        } catch (IOException naoEhJson) {
            return null;
        }
    }

    private record Recebido(int status, byte[] corpo) {
    }
}
