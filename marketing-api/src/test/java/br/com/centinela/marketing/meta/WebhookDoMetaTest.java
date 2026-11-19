package br.com.centinela.marketing.meta;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.github.tomakehurst.wiremock.client.WireMock;

import br.com.centinela.marketing.compartilhado.teste.Envelopes;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.teste.ModulosSimulados;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.configuracoes.CanalAnuncio;
import br.com.centinela.marketing.configuracoes.CanalAnuncioRepository;
import br.com.centinela.marketing.configuracoes.CanalAnuncioTipo;
import br.com.centinela.marketing.configuracoes.CifradorDeSegredo;

/**
 * E3 pelo filtro de segurança real e pelo identity simulado: handshake, assinatura HMAC, buffer sem
 * reentrega duplicada e isolamento entre tenants. A Graph API nunca é chamada aqui (é o job 4.1).
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class WebhookDoMetaTest extends IntegracaoDeTeste {

    private static final String SEGREDO_DO_APP = "segredo-do-app-do-tenant-a";
    private static final String VERIFY_TOKEN = "verify-token-do-tenant-a";
    private static final String TOKEN_DA_PAGINA = "EAAtoken-da-pagina-do-tenant-a";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CanalAnuncioRepository canais;

    @Autowired
    CifradorDeSegredo cifrador;

    @Test
    void handshakeCertoDevolveOChallengeEmTextoPuroForaDoEnvelope() throws Exception {
        Cenario a = tenantComMeta();

        mvc.perform(get(caminho(a)).param("hub.mode", "subscribe").param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("1158201444"));

        // O tenant vem do identity com o token de serviço e sem X-Tenant-Id (a rota ainda não sabe o tenant)
        ModulosSimulados.IDENTITY.verify(getRequestedFor(urlPathEqualTo(ModulosSimulados.CAMINHO_RESOLVER_TENANT))
                .withQueryParam("subdominio", equalTo(a.subdominio()))
                .withHeader("Authorization", equalTo("Bearer " + ModulosSimulados.TOKEN_SERVICO))
                .withoutHeader("X-Tenant-Id"));
    }

    @Test
    void handshakeErradoResponde403ComEnvelope() throws Exception {
        Cenario a = tenantComMeta();

        mvc.perform(get(caminho(a)).param("hub.mode", "subscribe").param("hub.verify_token", "outro-token")
                        .param("hub.challenge", "123"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(get(caminho(a)).param("hub.mode", "unsubscribe").param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "123"))
                .andExpect(status().isForbidden());
        mvc.perform(get(caminho(a))).andExpect(status().isForbidden());
        // Token certo, mas desafio com texto livre: não é refletido
        mvc.perform(get(caminho(a)).param("hub.mode", "subscribe").param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "<script>alert(1)</script>"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subdominioDesconhecidoETenantSemSegredoRespondemOMesmo404() throws Exception {
        String desconhecido = subdominio();
        ModulosSimulados.IDENTITY.stubFor(ModulosSimulados.comTokenDeServico(
                WireMock.get(urlPathEqualTo(ModulosSimulados.CAMINHO_RESOLVER_TENANT)))
                .withQueryParam("subdominio", equalTo(desconhecido))
                .willReturn(Envelopes.erro(404, "Empresa não encontrada.")));
        mvc.perform(get("/public/marketing/" + desconhecido + "/meta/leads").param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN).param("hub.challenge", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        String semMeta = subdominio();
        ModulosSimulados.stubTenantResolvido(semMeta, UUID.randomUUID(), "Sem Meta");
        mvc.perform(get("/public/marketing/" + semMeta + "/meta/leads").param("hub.mode", "subscribe")
                        .param("hub.verify_token", VERIFY_TOKEN).param("hub.challenge", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        String corpo = corpoDoWebhook("lead-1");
        mvc.perform(assinado(post("/public/marketing/" + semMeta + "/meta/leads"), corpo, SEGREDO_DO_APP))
                .andExpect(status().isNotFound());

        // Nome fora do formato do contrato do identity nem chega a ser perguntado
        mvc.perform(get("/public/marketing/Nome_Invalido/meta/leads")).andExpect(status().isNotFound());
        ModulosSimulados.IDENTITY.verify(exactly(0),
                getRequestedFor(urlPathEqualTo(ModulosSimulados.CAMINHO_RESOLVER_TENANT))
                        .withQueryParam("subdominio", equalTo("Nome_Invalido")));
    }

    @Test
    void assinaturaValidaGravaSoOsIdsDoWebhookNoBufferEResponde200(CapturedOutput saida) throws Exception {
        Cenario a = tenantComMeta();
        String corpo = corpoDoWebhook("leadgen-1001");

        mvc.perform(assinado(post(caminho(a)), corpo, SEGREDO_DO_APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Map<String, Object> linha = jdbc.queryForMap("""
                SELECT meta_lead_id, payload_bruto::text AS payload, processado, tentativas, falha_permanente,
                       created_by, updated_by
                FROM marketing.leads_entrada_buffer WHERE tenant_id = ?""", a.tenant());
        assertThat(linha.get("meta_lead_id")).isEqualTo("leadgen-1001");
        assertThat(linha.get("processado")).isEqualTo(false);
        assertThat(linha.get("tentativas")).isEqualTo(0);
        assertThat(linha.get("falha_permanente")).isEqualTo(false);
        assertThat(linha.get("created_by")).as("rota pública grava sem usuário").isNull();
        // Só os quatro ids; nada do que o Meta mais mandar (nome do anúncio, hora, texto livre...)
        assertThat(linha.get("payload").toString())
                .contains("\"leadgen_id\": \"leadgen-1001\"", "\"page_id\": \"4455\"", "\"form_id\": \"7788\"",
                        "\"ad_id\": \"9900\"")
                .doesNotContain("Ana", "created_time", "adgroup_id");

        // Nada de segredo, token nem corpo em log
        assertThat(saida.getAll()).doesNotContain(SEGREDO_DO_APP, VERIFY_TOKEN, TOKEN_DA_PAGINA);
        // O webhook não chama a Graph API: quem chama é o job 4.1
        assertThat(ModulosSimulados.META_GRAPH.getAllServeEvents()).isEmpty();
    }

    @Test
    void reentregaDoMesmoLeadgenIdNaoDuplicaALinha() throws Exception {
        Cenario a = tenantComMeta();
        String corpo = corpoDoWebhook("leadgen-2002");

        for (int i = 0; i < 3; i++) {
            mvc.perform(assinado(post(caminho(a)), corpo, SEGREDO_DO_APP)).andExpect(status().isOk());
        }
        // Dois leads no mesmo corpo, um deles repetido
        String dois = """
                {"object": "page", "entry": [{"id": "4455", "time": 1, "changes": [
                  {"field": "leadgen", "value": {"leadgen_id": "leadgen-2002", "page_id": "4455"}},
                  {"field": "leadgen", "value": {"leadgen_id": "leadgen-2003", "page_id": "4455"}},
                  {"field": "feed", "value": {"leadgen_id": "nao-e-lead"}},
                  {"field": "leadgen", "value": {"leadgen_id": "id com espaço"}}]}]}""";
        mvc.perform(assinado(post(caminho(a)), dois, SEGREDO_DO_APP)).andExpect(status().isOk());

        assertThat(jdbc.queryForList("SELECT meta_lead_id FROM marketing.leads_entrada_buffer WHERE tenant_id = ? "
                + "ORDER BY meta_lead_id", String.class, a.tenant()))
                .containsExactly("leadgen-2002", "leadgen-2003");
    }

    @Test
    void assinaturaInvalidaOuAusenteResponde401ENaoGravaNada() throws Exception {
        Cenario a = tenantComMeta();
        String corpo = corpoDoWebhook("leadgen-3003");

        mvc.perform(assinado(post(caminho(a)), corpo, "segredo-errado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post(caminho(a)).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(caminho(a)).contentType(MediaType.APPLICATION_JSON).content(corpo)
                        .header("X-Hub-Signature-256", "sha256=zzzz"))
                .andExpect(status().isUnauthorized());
        // Assinatura certa de um corpo, corpo trocado no caminho
        MockHttpServletRequestBuilder adulterado = post(caminho(a)).contentType(MediaType.APPLICATION_JSON)
                .content(corpoDoWebhook("leadgen-9999"))
                .header("X-Hub-Signature-256", AssinaturaDoMeta.cabecalhoPara(
                        corpo.getBytes(StandardCharsets.UTF_8), SEGREDO_DO_APP));
        mvc.perform(adulterado).andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketing.leads_entrada_buffer WHERE tenant_id = ?",
                Long.class, a.tenant())).isZero();
    }

    @Test
    void trocarOSubdominioNaoDaAcessoAOutroTenant() throws Exception {
        Cenario a = tenantComMeta();
        Cenario b = tenantComMeta("segredo-do-app-do-tenant-b", "verify-do-tenant-b", "token-da-pagina-b");
        String corpo = corpoDoWebhook("leadgen-4004");

        // Assinado com o segredo do tenant A e entregue no endereço do B (e o contrário)
        mvc.perform(assinado(post(caminho(b)), corpo, SEGREDO_DO_APP)).andExpect(status().isUnauthorized());
        mvc.perform(assinado(post(caminho(a)), corpo, "segredo-do-app-do-tenant-b"))
                .andExpect(status().isUnauthorized());
        // O verify token de um não abre o handshake do outro
        mvc.perform(get(caminho(b)).param("hub.mode", "subscribe").param("hub.verify_token", VERIFY_TOKEN)
                        .param("hub.challenge", "1"))
                .andExpect(status().isForbidden());

        // Cada um grava só no próprio tenant
        mvc.perform(assinado(post(caminho(a)), corpo, SEGREDO_DO_APP)).andExpect(status().isOk());
        mvc.perform(assinado(post(caminho(b)), corpo, "segredo-do-app-do-tenant-b")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketing.leads_entrada_buffer WHERE tenant_id = ?",
                Long.class, a.tenant())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketing.leads_entrada_buffer WHERE tenant_id = ?",
                Long.class, b.tenant())).isEqualTo(1);
    }

    @Test
    void identityForaDoArResponde503ParaOMetaReentregar() throws Exception {
        String subdominio = subdominio();
        ModulosSimulados.IDENTITY.stubFor(ModulosSimulados.comTokenDeServico(
                WireMock.get(urlPathEqualTo(ModulosSimulados.CAMINHO_RESOLVER_TENANT)))
                .withQueryParam("subdominio", equalTo(subdominio))
                .willReturn(Envelopes.erro(503, "Fora do ar.")));

        mvc.perform(assinado(post("/public/marketing/" + subdominio + "/meta/leads"), corpoDoWebhook("x"), "s"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/public/marketing/" + subdominio + "/meta/leads").param("hub.mode", "subscribe"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void corpoIlegivelOuGrandeDemais() throws Exception {
        Cenario a = tenantComMeta();

        mvc.perform(assinado(post(caminho(a)), "isto não é json", SEGREDO_DO_APP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(assinado(post(caminho(a)), "[1, 2]", SEGREDO_DO_APP)).andExpect(status().isBadRequest());
        mvc.perform(assinado(post(caminho(a)), "x".repeat(300 * 1024), SEGREDO_DO_APP))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void oSubdominioResolvidoFicaEmCacheCurto() throws Exception {
        Cenario a = tenantComMeta();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get(caminho(a)).param("hub.mode", "subscribe").param("hub.verify_token", VERIFY_TOKEN)
                    .param("hub.challenge", "77")).andExpect(status().isOk());
        }

        ModulosSimulados.IDENTITY.verify(exactly(1),
                getRequestedFor(urlPathEqualTo(ModulosSimulados.CAMINHO_RESOLVER_TENANT))
                        .withQueryParam("subdominio", equalTo(a.subdominio())));
    }

    // --- apoio -------------------------------------------------------------------------------------

    private record Cenario(String subdominio, UUID tenant) {
    }

    private Cenario tenantComMeta() {
        return tenantComMeta(SEGREDO_DO_APP, VERIFY_TOKEN, TOKEN_DA_PAGINA);
    }

    private Cenario tenantComMeta(String appSecret, String verifyToken, String tokenDaPagina) {
        Cenario cenario = new Cenario(subdominio(), UUID.randomUUID());
        ModulosSimulados.stubTenantResolvido(cenario.subdominio(), cenario.tenant(), "Tenant de teste");
        TenantContexto.executar(cenario.tenant(), () -> {
            CanalAnuncio canal = new CanalAnuncio(CanalAnuncioTipo.META_ADS);
            canal.atualizarCredenciaisMeta("4455", appSecret, verifyToken, tokenDaPagina, cifrador);
            canais.save(canal);
        });
        return cenario;
    }

    private static String subdominio() {
        return "empresa-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String caminho(Cenario cenario) {
        return "/public/marketing/" + cenario.subdominio() + "/meta/leads";
    }

    private static MockHttpServletRequestBuilder assinado(MockHttpServletRequestBuilder pedido, String corpo,
            String segredo) {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        return pedido.contentType(MediaType.APPLICATION_JSON).content(bytes)
                .header("X-Hub-Signature-256", AssinaturaDoMeta.cabecalhoPara(bytes, segredo));
    }

    /** Corpo como o Meta manda: traz mais do que os quatro ids, que o buffer não guarda. */
    private static String corpoDoWebhook(String leadgenId) {
        return """
                {"object": "page", "entry": [{"id": "4455", "time": 1700000000, "changes": [
                  {"field": "leadgen", "value": {"leadgen_id": "%s", "page_id": "4455", "form_id": "7788",
                    "ad_id": "9900", "adgroup_id": "5566", "created_time": 1700000000, "nome": "Ana"}}]}]}"""
                .formatted(leadgenId);
    }
}
