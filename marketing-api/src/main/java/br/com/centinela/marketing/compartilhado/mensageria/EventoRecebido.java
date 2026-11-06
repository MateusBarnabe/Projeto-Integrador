package br.com.centinela.marketing.compartilhado.mensageria;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope de uma mensagem consumida de outro módulo (Contrato §9.7), já conferido por
 * {@link ConsumoDeEventos}. O {@code tenantId} é a única fonte de tenant de uma mensagem, que não
 * carrega token. {@code dados} fica como JSON: cada consumidor converte para o próprio formato com
 * {@link ConsumoDeEventos#dados(EventoRecebido, Class)}, conforme {@code tipo} e {@code versao}.
 *
 * {@code dados} pode ter dado pessoal (o chatbot manda o contato): nunca vai para log.
 * Campo a mais no envelope é ignorado, para um acréscimo de outro módulo não mandar tudo para a DLQ.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoRecebido(
        UUID id,
        String tipo,
        int versao,
        UUID tenantId,
        String moduloOrigem,
        OffsetDateTime ocorridoEm,
        UUID usuarioId,
        String correlacaoId,
        JsonNode dados) {

    @Override
    public String toString() {
        // Sem os dados: o envelope pode aparecer em log, o conteúdo não.
        return "EventoRecebido[id=" + id + ", tipo=" + tipo + ", versao=" + versao + ", tenantId=" + tenantId
                + ", moduloOrigem=" + moduloOrigem + "]";
    }
}
