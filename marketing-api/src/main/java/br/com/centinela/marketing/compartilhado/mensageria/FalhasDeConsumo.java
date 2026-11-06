package br.com.centinela.marketing.compartilhado.mensageria;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * O que acontece quando um consumidor falha (Contrato §9.7: DLQ depois de três falhas).
 *
 * As tentativas vêm de {@code spring.rabbitmq.listener.simple.retry} no {@code application.yml}
 * ({@code max-attempts: 3}, com espera crescente). Esgotadas, a mensagem é rejeitada sem voltar
 * à fila e o RabbitMQ a manda para a {@code <fila>.dlq} ({@link TopologiaDeConsumo}).
 *
 * Mensagem que nunca vai dar certo ({@link AmqpRejectAndDontRequeueException}, como
 * {@link EventoInvalidoException}, ou JSON que não converte) vai direto para a DLQ, sem tentar de novo.
 */
@Configuration
class FalhasDeConsumo {

    private static final Logger LOG = LoggerFactory.getLogger(FalhasDeConsumo.class);

    /** Mesma quantidade de tentativas do yml, mas sem repetir o que é falha permanente. */
    @Bean
    RabbitRetryTemplateCustomizer tentativasDoConsumo(RabbitProperties propriedades) {
        int tentativas = propriedades.getListener().getSimple().getRetry().getMaxAttempts();
        return (alvo, retryTemplate) -> {
            if (alvo == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                retryTemplate.setRetryPolicy(politica(tentativas));
            }
        };
    }

    static SimpleRetryPolicy politica(int tentativas) {
        return new SimpleRetryPolicy(tentativas, Map.of(
                AmqpRejectAndDontRequeueException.class, false,
                MessageConversionException.class, false), true, true);
    }

    /**
     * Substitui o recuperador padrão do Spring, que escreve a mensagem inteira (com o corpo) no log.
     * Aqui o log leva só fila, id e tipo; o corpo pode ter dado pessoal e fica só na DLQ.
     */
    @Bean
    MessageRecoverer rejeitarParaDlq() {
        return (mensagem, causa) -> {
            LOG.warn("Mensagem rejeitada para a DLQ (fila {}, id {}, tipo {}): {}",
                    mensagem.getMessageProperties().getConsumerQueue(),
                    mensagem.getMessageProperties().getMessageId(),
                    mensagem.getMessageProperties().getType(),
                    motivo(causa));
            throw new ListenerExecutionFailedException("Mensagem rejeitada para a DLQ",
                    new AmqpRejectAndDontRequeueException(causa), semCorpo(mensagem));
        };
    }

    /** A exceção leva a mensagem para o tratador de erros do Spring, que pode logá-la: vai sem o corpo. */
    private static Message semCorpo(Message mensagem) {
        return new Message(new byte[0], mensagem.getMessageProperties());
    }

    private static String motivo(Throwable causa) {
        Throwable raiz = causa;
        while (raiz instanceof ListenerExecutionFailedException && raiz.getCause() != null) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName() + (raiz.getMessage() == null ? "" : ": " + raiz.getMessage());
    }
}
