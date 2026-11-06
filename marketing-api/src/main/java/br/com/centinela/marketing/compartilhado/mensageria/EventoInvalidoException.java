package br.com.centinela.marketing.compartilhado.mensageria;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Mensagem que nunca vai dar certo: envelope fora do §9.7, remetente que não confere ou
 * {@code dados} fora do formato. Vai direto para a DLQ, sem as novas tentativas.
 *
 * A mensagem de erro é escrita aqui e vai para o log: só nomes de campo, nunca valores.
 */
public class EventoInvalidoException extends AmqpRejectAndDontRequeueException {

    private static final long serialVersionUID = 1L;

    public EventoInvalidoException(String motivo) {
        super(motivo);
    }
}
