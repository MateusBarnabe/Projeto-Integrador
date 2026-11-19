package br.com.centinela.marketing.meta;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Corpo do webhook do Meta (E3, norteador §5 passo 1 e §6): para cada mudança {@code leadgen} grava
 * uma linha no buffer, só com {@code leadgen_id}, {@code page_id}, {@code form_id} e {@code ad_id}, e
 * nada mais. Não chama a Graph API (o job 4.1 chama) e não guarda o corpo, que pode trazer dado do
 * anúncio. A reentrega do mesmo {@code leadgen_id} não duplica a linha.
 *
 * <pre>{@code
 * {"object": "page", "entry": [{"id": "<page_id>", "time": 1, "changes": [
 *   {"field": "leadgen", "value": {"leadgen_id": "...", "page_id": "...", "form_id": "...", "ad_id": "..."}}]}]}
 * }</pre>
 */
@Service
class RecebimentoDoWebhook {

    private static final Logger LOG = LoggerFactory.getLogger(RecebimentoDoWebhook.class);

    /** Ids do Meta são numéricos; o padrão largo só barra o que não é id. */
    private static final Pattern ID = Pattern.compile("^[0-9A-Za-z_-]{1,100}$");

    private final BufferDoMeta buffer;
    private final ObjectMapper json;

    RecebimentoDoWebhook(BufferDoMeta buffer, ObjectMapper json) {
        this.buffer = buffer;
        this.json = json;
    }

    /**
     * @param novos       linhas gravadas
     * @param reentregues {@code leadgen_id} que o buffer já tinha
     * @param ignorados   mudanças que não são lead ({@code field} diferente de {@code leadgen}) ou sem id válido
     */
    record Resultado(int novos, int reentregues, int ignorados) {
    }

    /** O corpo não é um JSON de webhook; a rota responde {@code 400}. */
    static class CorpoInvalidoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CorpoInvalidoException(Throwable causa) {
            super("Corpo do webhook do Meta ilegível.", causa);
        }
    }

    Resultado receber(UUID tenant, byte[] corpo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(corpo);
        } catch (IOException ilegivel) {
            throw new CorpoInvalidoException(ilegivel);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new CorpoInvalidoException(null);
        }
        return TenantContexto.calcular(tenant, () -> gravar(raiz));
    }

    private Resultado gravar(JsonNode raiz) {
        int novos = 0;
        int reentregues = 0;
        int ignorados = 0;
        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode mudanca : entrada.path("changes")) {
                JsonNode valor = mudanca.path("value");
                String leadgenId = id(valor, "leadgen_id");
                if (!"leadgen".equals(mudanca.path("field").asText()) || leadgenId == null) {
                    ignorados++;
                    continue;
                }
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put(LeadEntradaBuffer.CHAVE_LEADGEN_ID, leadgenId);
                String pagina = id(valor, "page_id");
                payload.put(LeadEntradaBuffer.CHAVE_PAGE_ID, pagina != null ? pagina : id(entrada, "id"));
                payload.put(LeadEntradaBuffer.CHAVE_FORM_ID, id(valor, "form_id"));
                payload.put(LeadEntradaBuffer.CHAVE_AD_ID, id(valor, "ad_id"));
                if (buffer.gravar(leadgenId, payload)) {
                    novos++;
                } else {
                    reentregues++;
                }
            }
        }
        LOG.info("Webhook do Meta: {} lead(s) novo(s) no buffer, {} reentrega(s), {} ignorado(s)", novos,
                reentregues, ignorados);
        return new Resultado(novos, reentregues, ignorados);
    }

    private static String id(JsonNode no, String campo) {
        JsonNode valor = no.path(campo);
        if (!valor.isTextual() && !valor.isIntegralNumber()) {
            return null;
        }
        String texto = valor.asText().strip();
        return ID.matcher(texto).matches() ? texto : null;
    }
}
