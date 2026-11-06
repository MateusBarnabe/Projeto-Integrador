package br.com.centinela.marketing.entrada;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.centinela.marketing.compartilhado.mensageria.EventoRecebido;

/**
 * De onde veio o fato que mexeu no lead, para o {@code payload} da timeline ({@code lead_eventos}).
 *
 * @param fonte    tipo do evento ({@code landing.chatbot.lead_capturado}) ou nome da rotina ({@code job.meta})
 * @param eventoId id do evento consumido; nulo numa rotina
 */
public record Procedencia(String fonte, UUID eventoId) {

    public Procedencia {
        Objects.requireNonNull(fonte, "fonte");
    }

    public static Procedencia doEvento(EventoRecebido evento) {
        return new Procedencia(evento.tipo(), evento.id());
    }

    /** Payload base da timeline, sem dado pessoal. */
    Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonte", fonte);
        if (eventoId != null) {
            payload.put("eventoId", eventoId.toString());
        }
        return payload;
    }
}
