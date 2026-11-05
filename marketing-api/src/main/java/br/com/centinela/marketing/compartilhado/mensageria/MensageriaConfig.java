package br.com.centinela.marketing.compartilhado.mensageria;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Mensagens em JSON, com o mesmo {@link ObjectMapper} da API (datas ISO-8601 em UTC). */
@Configuration
class MensageriaConfig {

    @Bean
    MessageConverter conversorJson(ObjectMapper json) {
        return new Jackson2JsonMessageConverter(json);
    }
}
