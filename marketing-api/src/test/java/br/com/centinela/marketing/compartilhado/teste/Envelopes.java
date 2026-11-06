package br.com.centinela.marketing.compartilhado.teste;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Resposta;

/**
 * Respostas do WireMock no envelope da plataforma, {@code {success, data, message, errors}}
 * (Contrato §8.2), como Landing, CRM e identity respondem. Usa os mesmos records da API
 * ({@link Resposta}, {@link ErroCampo}, {@link Pagina}), então o JSON sai igual ao nosso.
 *
 * <pre>{@code
 * LANDING.stubFor(get("/api/landing/envios/" + id).willReturn(Envelopes.ok(Map.of("id", id))));
 * CRM.stubFor(put(urlPathMatching("/api/crm/.*")).willReturn(Envelopes.erro(422, "Regra violada.")));
 * }</pre>
 */
public final class Envelopes {

    private static final ObjectMapper JSON = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Envelopes() {
    }

    /** {@code 200} com {@code success = true}; {@code data} é serializado com o Jackson (Map, record...). */
    public static ResponseDefinitionBuilder ok(Object data) {
        return ok(200, data);
    }

    /** Sucesso com outro código, como {@code 201}. */
    public static ResponseDefinitionBuilder ok(int status, Object data) {
        return resposta(status, Resposta.ok(data));
    }

    /**
     * {@code 200} com {@code data} vindo de um JSON pronto, útil para colar o exemplo de um contrato.
     * O texto precisa ser JSON válido.
     */
    public static ResponseDefinitionBuilder okJson(String dataJson) {
        try {
            return ok(JSON.readTree(dataJson));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("data não é JSON válido: " + dataJson, e);
        }
    }

    /** {@code 200} com a listagem da plataforma (§8.3): {@code data = {itens, pagina, tamanho, total}}. */
    public static ResponseDefinitionBuilder pagina(List<?> itens, int pagina, int tamanho, long total) {
        return ok(new Pagina<>(itens, pagina, tamanho, total));
    }

    /** Falha com {@code success = false}, {@code data = null} e os erros por campo, se houver. */
    public static ResponseDefinitionBuilder erro(int status, String mensagem, ErroCampo... erros) {
        return resposta(status, Resposta.erro(mensagem, List.of(erros)));
    }

    /** Serializa um objeto como o WireMock vai devolver; serve também para montar corpos esperados. */
    public static String json(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("não foi possível serializar " + valor, e);
        }
    }

    private static ResponseDefinitionBuilder resposta(int status, Resposta<?> envelope) {
        return WireMock.aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(json(envelope));
    }
}
