package br.com.centinela.marketing.compartilhado.teste;

import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.CRM;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.IDENTITY;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.LANDING;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.META_GRAPH;
import static br.com.centinela.marketing.compartilhado.teste.ModulosSimulados.comTokenDeServico;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;

/**
 * Os simuladores do WireMock (F6) sem Spring e sem Docker: stub padrão do token de serviço,
 * helpers do envelope, exigência de token e tenant, Graph API e reset entre testes.
 */
class ModulosSimuladosTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-00000000000a");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void zerar() {
        ModulosSimulados.resetar();
    }

    @Test
    void cadaDestinoTemSeuServidor() {
        assertThat(List.of(LANDING.port(), CRM.port(), IDENTITY.port(), META_GRAPH.port())).doesNotHaveDuplicates();
        assertThat(LANDING.isRunning() && CRM.isRunning() && IDENTITY.isRunning() && META_GRAPH.isRunning()).isTrue();
    }

    @Test
    void identityEmiteOTokenDeServicoParaOMarketing() throws Exception {
        HttpResponse<String> resposta = pedirPost(IDENTITY.baseUrl() + ModulosSimulados.CAMINHO_TOKEN_SERVICO,
                "{\"clientId\":\"marketing\",\"clientSecret\":\"" + ModulosSimulados.CLIENT_SECRET + "\"}");

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode envelope = json.readTree(resposta.body());
        assertThat(envelope.get("success").asBoolean()).isTrue();
        assertThat(envelope.get("message").isNull()).isTrue();
        assertThat(envelope.get("errors")).isEmpty();
        assertThat(envelope.at("/data/accessToken").asText()).isEqualTo(ModulosSimulados.TOKEN_SERVICO);
        assertThat(envelope.at("/data/expiresIn").asInt()).isEqualTo(900);

        assertThat(pedirPost(IDENTITY.baseUrl() + ModulosSimulados.CAMINHO_TOKEN_SERVICO,
                "{\"clientId\":\"marketing\",\"clientSecret\":\"errado\"}").statusCode())
                .as("credencial errada não tem stub").isEqualTo(404);
    }

    @Test
    void landingRespondeNoEnvelopeSoComTokenDeServicoETenant() throws Exception {
        UUID envio = UUID.randomUUID();
        String caminho = "/api/landing/envios/" + envio;
        LANDING.stubFor(comTokenDeServico(get(urlPathEqualTo(caminho)), TENANT)
                .willReturn(Envelopes.ok(Map.of("id", envio, "respostas", List.of()))));

        HttpResponse<String> comToken = pedirGet(LANDING.baseUrl() + caminho, true);
        HttpResponse<String> semToken = pedirGet(LANDING.baseUrl() + caminho, false);

        assertThat(comToken.statusCode()).isEqualTo(200);
        assertThat(json.readTree(comToken.body()).at("/data/id").asText()).isEqualTo(envio.toString());
        assertThat(semToken.statusCode()).isEqualTo(404);
        LANDING.verify(2, getRequestedFor(urlPathEqualTo(caminho)));
    }

    @Test
    void crmDevolveErroEPaginaNoEnvelope() throws Exception {
        CRM.stubFor(get(urlPathEqualTo("/api/crm/erro")).willReturn(Envelopes.erro(422, "Regra violada.",
                new ErroCampo("responsavelId", "VENDEDOR_INATIVO", "Vendedor inativo."))));
        CRM.stubFor(get(urlPathEqualTo("/api/crm/lista"))
                .willReturn(Envelopes.pagina(List.of(Map.of("id", 1)), 1, 20, 1)));

        HttpResponse<String> erro = pedirGet(CRM.baseUrl() + "/api/crm/erro", false);
        JsonNode envelopeErro = json.readTree(erro.body());
        assertThat(erro.statusCode()).isEqualTo(422);
        assertThat(envelopeErro.get("success").asBoolean()).isFalse();
        assertThat(envelopeErro.get("data").isNull()).isTrue();
        assertThat(envelopeErro.get("message").asText()).isEqualTo("Regra violada.");
        assertThat(envelopeErro.at("/errors/0/codigo").asText()).isEqualTo("VENDEDOR_INATIVO");

        JsonNode pagina = json.readTree(pedirGet(CRM.baseUrl() + "/api/crm/lista", false).body()).get("data");
        assertThat(pagina.get("itens")).hasSize(1);
        assertThat(pagina.get("total").asLong()).isEqualTo(1);
    }

    @Test
    void okJsonAceitaOExemploDoContrato() throws Exception {
        LANDING.stubFor(get(urlPathEqualTo("/api/landing/formularios/1/resumo"))
                .willReturn(Envelopes.okJson("{\"nome\":\"Contato\",\"campos\":[]}")));

        JsonNode envelope = json.readTree(pedirGet(LANDING.baseUrl() + "/api/landing/formularios/1/resumo", false).body());
        assertThat(envelope.at("/data/nome").asText()).isEqualTo("Contato");
        assertThat(envelope.get("success").asBoolean()).isTrue();
    }

    @Test
    void identityResolveTenantEGraphApiDevolveOLeadDoMeta() throws Exception {
        ModulosSimulados.stubTenantResolvido("centinela", TENANT, "Centinela");
        ModulosSimulados.stubLeadDoMeta("123456", Map.of("email", List.of("lead@exemplo.dev")));

        HttpResponse<String> tenant = pedirGet(IDENTITY.baseUrl()
                + ModulosSimulados.CAMINHO_RESOLVER_TENANT + "?subdominio=centinela", true);
        HttpResponse<String> lead = pedirGet(META_GRAPH.baseUrl() + "/v21.0/123456?access_token=x", false);

        assertThat(json.readTree(tenant.body()).at("/data/id").asText()).isEqualTo(TENANT.toString());
        assertThat(lead.statusCode()).isEqualTo(200);
        assertThat(json.readTree(lead.body()).at("/field_data/0/values/0").asText()).isEqualTo("lead@exemplo.dev");
    }

    @Test
    void resetarApagaOsStubsDoTesteEVoltaOTokenDeServico() throws Exception {
        LANDING.stubFor(get(urlPathEqualTo("/temporario")).willReturn(Envelopes.ok("x")));
        assertThat(pedirGet(LANDING.baseUrl() + "/temporario", false).statusCode()).isEqualTo(200);

        ModulosSimulados.resetar();

        assertThat(pedirGet(LANDING.baseUrl() + "/temporario", false).statusCode()).isEqualTo(404);
        assertThat(LANDING.getStubMappings()).isEmpty();
        assertThat(IDENTITY.getStubMappings()).hasSize(1);
    }

    private HttpResponse<String> pedirGet(String url, boolean comTokenDeServico) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url)).GET();
        if (comTokenDeServico) {
            pedido.header("Authorization", "Bearer " + ModulosSimulados.TOKEN_SERVICO)
                    .header("X-Tenant-Id", TENANT.toString());
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> pedirPost(String url, String corpo) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
