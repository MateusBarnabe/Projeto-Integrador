package br.com.centinela.marketing.compartilhado.teste;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;

/**
 * Servidores WireMock no lugar dos módulos e serviços externos que o Marketing chama (checklist
 * §15): Landing, CRM, identity e Graph API do Meta. Um servidor por destino, em porta livre do
 * {@code 127.0.0.1}, criado uma vez por JVM e zerado antes de cada teste por
 * {@link IntegracaoDeTeste}.
 *
 * <p>As URLs entram no Spring com os nomes de variável de ambiente da plataforma
 * ({@code LANDING_BASE_URL}, {@code CRM_BASE_URL}, {@code IDENTITY_BASE_URL}) e
 * {@code META_GRAPH_BASE_URL}; os placeholders {@code ${...}} do {@code application.yml} as
 * resolvem. O token de serviço do identity já vem simulado ({@link #stubTokenDeServico()}).
 */
public final class ModulosSimulados {

    /** Credencial do token de serviço usada nos testes (Contrato §9.2). */
    public static final String CLIENT_ID = "marketing";
    public static final String CLIENT_SECRET = "segredo-de-teste";

    /** O que o identity simulado devolve em {@code POST /api/identity/auth/token-servico}. */
    public static final String TOKEN_SERVICO = "token-de-servico-de-teste";
    public static final int TOKEN_SERVICO_EXPIRA_EM_SEGUNDOS = 900;

    public static final String CAMINHO_TOKEN_SERVICO = "/api/identity/auth/token-servico";
    public static final String CAMINHO_RESOLVER_TENANT = "/api/identity/tenants/resolver";

    public static final WireMockServer LANDING = servidor();
    public static final WireMockServer CRM = servidor();
    public static final WireMockServer IDENTITY = servidor();
    public static final WireMockServer META_GRAPH = servidor();

    private static final List<WireMockServer> TODOS = List.of(LANDING, CRM, IDENTITY, META_GRAPH);

    static {
        TODOS.forEach(WireMockServer::start);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> TODOS.forEach(WireMockServer::stop)));
        stubTokenDeServico();
    }

    private ModulosSimulados() {
    }

    private static WireMockServer servidor() {
        return new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
    }

    /** Coloca as URLs dos simuladores e a credencial de serviço no ambiente do Spring. */
    public static void registrar(DynamicPropertyRegistry registro) {
        registro.add("LANDING_BASE_URL", LANDING::baseUrl);
        registro.add("CRM_BASE_URL", CRM::baseUrl);
        registro.add("IDENTITY_BASE_URL", IDENTITY::baseUrl);
        registro.add("META_GRAPH_BASE_URL", META_GRAPH::baseUrl);
        registro.add("SVC_CLIENT_ID", () -> CLIENT_ID);
        registro.add("SVC_CLIENT_SECRET", () -> CLIENT_SECRET);
    }

    /** Apaga stubs e requisições gravadas de todos os servidores e volta o stub padrão do identity. */
    public static void resetar() {
        TODOS.forEach(WireMockServer::resetAll);
        stubTokenDeServico();
    }

    /**
     * Stub padrão: o identity emite {@link #TOKEN_SERVICO} para {@code clientId = marketing}
     * com o {@link #CLIENT_SECRET}. Outra credencial cai sem stub e o WireMock responde {@code 404}.
     */
    public static void stubTokenDeServico() {
        IDENTITY.stubFor(post(urlPathEqualTo(CAMINHO_TOKEN_SERVICO))
                .withRequestBody(matchingJsonPath("$.clientId", equalTo(CLIENT_ID)))
                .withRequestBody(matchingJsonPath("$.clientSecret", equalTo(CLIENT_SECRET)))
                .willReturn(Envelopes.ok(Map.of(
                        "accessToken", TOKEN_SERVICO,
                        "expiresIn", TOKEN_SERVICO_EXPIRA_EM_SEGUNDOS))));
    }

    /** {@code GET /api/identity/tenants/resolver?subdominio=} devolve o tenant (webhook do Meta). */
    public static void stubTenantResolvido(String subdominio, UUID tenantId, String nome) {
        IDENTITY.stubFor(comTokenDeServico(get(urlPathEqualTo(CAMINHO_RESOLVER_TENANT)))
                .withQueryParam("subdominio", equalTo(subdominio))
                .willReturn(Envelopes.ok(Map.of("id", tenantId, "nome", nome))));
    }

    /**
     * {@code GET {versão}/{leadgen_id}} da Graph API, em qualquer versão ({@code /v21.0/...}).
     * A Graph API não usa o envelope da plataforma: devolve {@code {id, created_time, field_data}}.
     */
    public static void stubLeadDoMeta(String leadgenId, Map<String, List<String>> campos) {
        List<Map<String, Object>> fieldData = campos.entrySet().stream()
                .map(campo -> Map.<String, Object>of("name", campo.getKey(), "values", campo.getValue()))
                .toList();
        Map<String, Object> lead = new LinkedHashMap<>();
        lead.put("id", leadgenId);
        lead.put("created_time", "2026-11-05T12:00:00+0000");
        lead.put("field_data", fieldData);
        META_GRAPH.stubFor(get(urlPathMatching("/v[0-9.]+/" + Pattern.quote(leadgenId)))
                .willReturn(okJson(Envelopes.json(lead))));
    }

    /** Exige o token de serviço no {@code Authorization}, como o Landing e o CRM conferem (§9.2). */
    public static MappingBuilder comTokenDeServico(MappingBuilder requisicao) {
        return requisicao.withHeader("Authorization", equalTo("Bearer " + TOKEN_SERVICO));
    }

    /** Exige o token de serviço e o {@code X-Tenant-Id} do tenant da rotina (§9.2). */
    public static MappingBuilder comTokenDeServico(MappingBuilder requisicao, UUID tenantId) {
        return comTokenDeServico(requisicao).withHeader("X-Tenant-Id", equalTo(tenantId.toString()));
    }
}
