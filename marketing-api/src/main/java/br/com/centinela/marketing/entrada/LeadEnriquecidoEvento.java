package br.com.centinela.marketing.entrada;

import java.util.UUID;

/** Envelope mínimo esperado pelo consumer, compatível com os eventos do Landing. */
public record LeadEnriquecidoEvento(
        UUID id,
        String tipo,
        UUID tenantId,
        LeadEnriquecido dados) {
}