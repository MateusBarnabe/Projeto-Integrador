package br.com.centinela.marketing.entrada;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Consumer da fila local/proposta do Landing; falhas são tratadas pela política da fila. */
@Component
@ConditionalOnProperty(name = "marketing.iot.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class LeadEntradaConsumer {

    static final String FILA = "marketing.landing-leads";

    private final LeadEntradaService service;

    public LeadEntradaConsumer(LeadEntradaService service) {
        this.service = service;
    }

    @RabbitListener(queues = FILA)
    public void receber(LeadEnriquecidoEvento evento) {
        service.processar(evento);
    }
}