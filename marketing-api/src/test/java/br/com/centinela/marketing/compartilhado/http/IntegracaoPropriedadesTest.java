package br.com.centinela.marketing.compartilhado.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Padrões do {@code application.yml}: nomes de container do compose da plataforma, timeout de 3 s e
 * {@code clientId = marketing}. Sobe só a configuração da integração, sem banco nem Docker.
 */
@SpringBootTest(classes = IntegracaoConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
class IntegracaoPropriedadesTest {

    @Autowired
    IntegracaoPropriedades propriedades;

    @Autowired
    ClientesDeModulos clientes;

    @Test
    void padroesApontamParaOsContainersDaPlataforma() {
        assertThat(propriedades.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(propriedades.identityBaseUrl()).isEqualTo("http://identity:8081");
        assertThat(propriedades.landingBaseUrl()).isEqualTo("http://landing:8088");
        assertThat(propriedades.crmBaseUrl()).isEqualTo("http://crm:8082");
        assertThat(propriedades.servico().clientId()).isEqualTo("marketing");
        assertThat(clientes.landing().modulo()).isEqualTo(ModuloDestino.LANDING);
        assertThat(clientes.crm().modulo()).isEqualTo(ModuloDestino.CRM);
        assertThat(clientes.identity().modulo()).isEqualTo(ModuloDestino.IDENTITY);
    }
}
