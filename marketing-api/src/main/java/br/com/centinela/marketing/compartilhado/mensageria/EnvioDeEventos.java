package br.com.centinela.marketing.compartilhado.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos.EventoPendente;

/**
 * Envia ao RabbitMQ as mensagens de {@link PublicadorDeEventos} só depois do commit
 * (Contrato §9.7). Toda mensagem leva {@code user_id} igual ao usuário da conexão
 * ({@code mq_marketing}); sem isso, a plataforma manda o pedido para a {@code .dlq}.
 */
@Component
class EnvioDeEventos {

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDeEventos.class);

    private final RabbitTemplate rabbit;
    private final String usuarioRabbit;

    EnvioDeEventos(RabbitTemplate rabbit, RabbitProperties propriedades) {
        this.rabbit = rabbit;
        this.usuarioRabbit = propriedades.determineUsername();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void enviar(EventoPendente pendente) {
        Evento evento = pendente.evento();
        try {
            rabbit.convertAndSend(pendente.exchange(), evento.tipo(), evento, mensagem -> {
                var propriedades = mensagem.getMessageProperties();
                propriedades.setUserId(usuarioRabbit);
                propriedades.setMessageId(evento.id().toString());
                propriedades.setType(evento.tipo());
                propriedades.setCorrelationId(evento.correlacaoId());
                propriedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensagem;
            });
            LOG.info("Evento {} publicado em {} (id {})", evento.tipo(), pendente.exchange(), evento.id());
        } catch (RuntimeException falha) {
            // A transação já foi confirmada: o evento se perde. O log leva só o envelope, sem os dados.
            LOG.error("Falha ao publicar o evento {} em {} (id {}, tenant {})", evento.tipo(),
                    pendente.exchange(), evento.id(), evento.tenantId(), falha);
        }
    }
}
