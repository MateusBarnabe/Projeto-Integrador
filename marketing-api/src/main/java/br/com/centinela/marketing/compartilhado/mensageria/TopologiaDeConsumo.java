package br.com.centinela.marketing.compartilhado.mensageria;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas que o Marketing consome (norteador §8, Consumo). Quem declara é a própria API, ao abrir a
 * conexão: o {@code mq_marketing} configura {@code marketing.*} e lê qualquer {@code *.eventos},
 * o que basta para ligar as filas às exchanges dos outros módulos (Contrato §9.7, §13.3).
 *
 * As exchanges de outro módulo ({@code landing.eventos}, {@code crm.eventos}) são só referenciadas
 * pelo nome, nunca declaradas: quem as cria é a plataforma.
 *
 * Cada fila tem a sua {@code <fila>.dlq}. Depois de {@code spring.rabbitmq.listener.simple.retry
 * .max-attempts} (3) falhas, a mensagem é rejeitada e o RabbitMQ a manda, pela {@link #DLX}, para a DLQ.
 */
@Configuration
public class TopologiaDeConsumo {

    /** Exchange (direct) das mensagens que esgotaram as tentativas; a routing key é o nome da DLQ. */
    public static final String DLX = "marketing.dlx";

    public static final String EXCHANGE_LANDING = "landing.eventos";
    public static final String EXCHANGE_CRM = "crm.eventos";

    /** Eventos do Landing: envio de formulário (etapa 3) e captura do chatbot (etapa 1). */
    public static final String FILA_LANDING_LEADS = "marketing.landing-leads";

    /** {@code crm.oportunidade.criada}: liga a oportunidade criada pelo Landing ao lead. */
    public static final String FILA_CRM_OPORTUNIDADES = "marketing.crm-oportunidades";

    public static final String LANDING_FORMULARIO_RECEBIDO = "landing.formulario.recebido";
    public static final String LANDING_CHATBOT_LEAD_CAPTURADO = "landing.chatbot.lead_capturado";
    public static final String CRM_OPORTUNIDADE_CRIADA = "crm.oportunidade.criada";

    /** Nome da DLQ de uma fila. */
    public static String dlq(String fila) {
        return fila + ".dlq";
    }

    @Bean
    Declarables filasDeConsumo() {
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        List<Declarable> declaracoes = new ArrayList<>();
        declaracoes.add(dlx);
        declaracoes.addAll(fila(dlx, FILA_LANDING_LEADS, EXCHANGE_LANDING,
                LANDING_FORMULARIO_RECEBIDO, LANDING_CHATBOT_LEAD_CAPTURADO));
        declaracoes.addAll(fila(dlx, FILA_CRM_OPORTUNIDADES, EXCHANGE_CRM, CRM_OPORTUNIDADE_CRIADA));
        return new Declarables(declaracoes);
    }

    /** Fila durável com a sua DLQ, ligada à exchange de quem publica pelas chaves dadas. */
    private static List<Declarable> fila(DirectExchange dlx, String nome, String exchange, String... chaves) {
        List<Declarable> declaracoes = new ArrayList<>();
        Queue fila = QueueBuilder.durable(nome)
                .deadLetterExchange(dlx.getName())
                .deadLetterRoutingKey(dlq(nome))
                .build();
        Queue dlq = QueueBuilder.durable(dlq(nome)).build();
        declaracoes.add(fila);
        declaracoes.add(dlq);
        declaracoes.add(new Binding(dlq.getName(), Binding.DestinationType.QUEUE, dlx.getName(), dlq.getName(), null));
        for (String chave : chaves) {
            declaracoes.add(new Binding(nome, Binding.DestinationType.QUEUE, exchange, chave, null));
        }
        return declaracoes;
    }
}
