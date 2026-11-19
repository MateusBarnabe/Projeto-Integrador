package br.com.centinela.marketing.painel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades {@code marketing.painel}. */
@Configuration
@EnableConfigurationProperties(PainelPropriedades.class)
class PainelConfig {
}
