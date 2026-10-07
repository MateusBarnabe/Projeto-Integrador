package br.com.centinela.marketing.entrada;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.StatefulRetryOperationsInterceptor;

/** Topologia local do IOT, alinhada à fila prevista para os eventos do Landing. */
@Configuration
@ConditionalOnProperty(name = "marketing.iot.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class LeadEntradaRabbitConfig {

    static final String EXCHANGE = "landing.eventos";
    static final String DLX = "marketing.landing-leads.dlx";
    static final String DLQ = "marketing.landing-leads.dlq";

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory, DirectExchange deadLetterExchange,
            Queue leadsQueue, Queue leadsDlq, Binding leadsDlqBinding, Binding leadsBinding) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareExchange(deadLetterExchange);
        admin.declareQueue(leadsQueue);
        admin.declareQueue(leadsDlq);
        admin.declareBinding(leadsDlqBinding);
        admin.declareBinding(leadsBinding);
        return admin;
    }

    @Bean
    Queue leadsQueue() {
        return QueueBuilder.durable(LeadEntradaConsumer.FILA)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue leadsDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Binding leadsDlqBinding() {
        return new Binding(DLQ, Binding.DestinationType.QUEUE, DLX, DLQ, null);
    }

    /** Liga a fila à exchange do Landing sem tentar declarar a exchange de outro módulo. */
    @Bean
    Binding leadsBinding() {
        return new Binding(LeadEntradaConsumer.FILA, Binding.DestinationType.QUEUE, EXCHANGE, "#", null);
    }

    @Bean
    MessageConverter rabbitJsonConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
            MessageConverter rabbitJsonConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitJsonConverter);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }

    private StatefulRetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateful()
                .maxAttempts(3)
                .backOffOptions(1000, 2, 10000)
                .build();
    }
}