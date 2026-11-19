package br.com.centinela.marketing.meta;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra as propriedades de {@code marketing.meta}. */
@Configuration
@EnableConfigurationProperties(MetaPropriedades.class)
class MetaConfig {
}
