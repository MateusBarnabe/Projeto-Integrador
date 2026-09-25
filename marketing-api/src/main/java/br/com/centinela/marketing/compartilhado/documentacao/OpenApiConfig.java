package br.com.centinela.marketing.compartilhado.documentacao;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentação gerada dos controllers (Swagger UI em /swagger-ui.html). O botão Authorize recebe
 * o JWT do identity (§4.2); rotas sem token, como o health, respondem normalmente sem ele.
 * O contrato publicado para outros módulos continua sendo Docs/contratos/marketing.yaml.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_TOKEN = "token-identity";

    @Bean
    OpenAPI documentacao() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketing — Grupo 4")
                        .version("0.1.0")
                        .description("API do módulo de Marketing e Automações. Toda resposta usa o envelope "
                                + "{success, data, message, errors}."))
                .components(new Components().addSecuritySchemes(ESQUEMA_TOKEN, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_TOKEN));
    }
}
