package br.com.centinela.marketing.compartilhado.http;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Registra os clientes de outros módulos com o mesmo {@link ObjectMapper} da API. */
@Configuration
@EnableConfigurationProperties(IntegracaoPropriedades.class)
class IntegracaoConfig {

    @Bean
    ClientesDeModulos clientesDeModulos(IntegracaoPropriedades propriedades, ObjectMapper json) {
        return new ClientesDeModulos(propriedades, json);
    }
}
