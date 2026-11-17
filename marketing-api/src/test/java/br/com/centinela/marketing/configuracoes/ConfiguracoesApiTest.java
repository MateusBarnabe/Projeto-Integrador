package br.com.centinela.marketing.configuracoes;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.teste.Envelopes;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.teste.ModulosSimulados;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * Testes das APIs de configuração (A3), canais de anúncio com segredos cifrados (D6) e valores
 * iniciais por tenant com regras de UTM e comportamento (D7 e R5).
 */
@AutoConfigureMockMvc
class ConfiguracoesApiTest extends IntegracaoDeTeste {

    private static final String TOKEN_COMPLETO = "token-completo";
    private static final String TOKEN_LEITURA = "token-leitura";
    private static final String TOKEN_OUTRO_TENANT = "token-outro-tenant";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LeadRepository leads;

    @Autowired
    CifradorDeSegredo cifrador;

    @MockitoBean
    JwtDecoder decoder;

    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    @BeforeEach
    void configurarTokens() {
        Map<String, Jwt> tokens = new HashMap<>();
        tokens.put(TOKEN_COMPLETO, usuario(tenant,
                "marketing.configuracao.ver", "marketing.configuracao.editar",
                "marketing.template.ver", "marketing.template.criar", "marketing.template.editar", "marketing.template.excluir",
                "marketing.automacao.ver", "marketing.automacao.editar",
                "marketing.qualificacao.ver", "marketing.qualificacao.editar"));
        tokens.put(TOKEN_LEITURA, usuario(tenant,
                "marketing.configuracao.ver", "marketing.template.ver",
                "marketing.automacao.ver", "marketing.qualificacao.ver"));
        tokens.put(TOKEN_OUTRO_TENANT, usuario(outroTenant,
                "marketing.configuracao.ver", "marketing.configuracao.editar",
                "marketing.template.ver", "marketing.template.editar", "marketing.template.excluir",
                "marketing.qualificacao.ver", "marketing.qualificacao.editar"));
        when(decoder.decode(anyString())).thenAnswer(chamada -> {
            Jwt jwt = tokens.get(chamada.<String>getArgument(0));
            if (jwt == null) {
                throw new BadJwtException("token desconhecido");
            }
            return jwt;
        });
    }

    @Test
    void canaisDeAnuncioCiframSegredosDoMetaENuncaOsDevolvemNaApi() throws Exception {
        pedir(get("/api/marketing/canais-anuncio"), TOKEN_LEITURA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].canal").value("google_ads"))
                .andExpect(jsonPath("$.data[1].canal").value("meta_ads"))
                .andExpect(jsonPath("$.data[1].metaAppSecretConfigurado").value(false));

        String corpo = """
                {
                  "canal": "meta_ads",
                  "orcamentoMensal": 2500.50,
                  "publicoAlvo": "Empresas B2B em Goiás",
                  "objetivo": "Geração de leads",
                  "ativo": true,
                  "metaPageId": "123456789",
                  "metaAppSecret": "segredo-super-confidencial",
                  "metaVerifyToken": "verify-token-secreto",
                  "metaPageAccessToken": "EAA-token-pagina"
                }
                """;
        ResultActions resposta = pedir(put("/api/marketing/canais-anuncio")
                .contentType(MediaType.APPLICATION_JSON).content(corpo), TOKEN_COMPLETO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canal").value("meta_ads"))
                .andExpect(jsonPath("$.data.orcamentoMensal").value(2500.50))
                .andExpect(jsonPath("$.data.metaPageId").value("123456789"))
                .andExpect(jsonPath("$.data.metaAppSecretConfigurado").value(true))
                .andExpect(jsonPath("$.data.metaVerifyTokenConfigurado").value(true))
                .andExpect(jsonPath("$.data.metaPageAccessTokenConfigurado").value(true))
                .andExpect(jsonPath("$.data.webhookUrl").isNotEmpty());

        String textoResposta = resposta.andReturn().getResponse().getContentAsString();
        assertThat(textoResposta)
                .doesNotContain("segredo-super-confidencial")
                .doesNotContain("verify-token-secreto")
                .doesNotContain("EAA-token-pagina");

        // No banco o valor está cifrado (não em texto aberto) e pode ser decifrado pelo CifradorDeSegredo
        String noBanco = jdbc.queryForObject(
                "SELECT meta_app_secret FROM marketing.canais_anuncio WHERE tenant_id = ? AND canal = 'meta_ads'",
                String.class, tenant);
        assertThat(noBanco).isNotBlank().doesNotContain("segredo-super-confidencial");
        assertThat(cifrador.decifrar(noBanco)).isEqualTo("segredo-super-confidencial");

        // Mais de 2 casas decimais no orçamento responde 400 PRECISAO_EXCEDIDA
        pedir(put("/api/marketing/canais-anuncio").contentType(MediaType.APPLICATION_JSON)
                .content("{\"canal\":\"google_ads\",\"orcamentoMensal\":100.123}"), TOKEN_COMPLETO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("PRECISAO_EXCEDIDA"));
    }

    @Test
    void modelosDeMensagemCriamBoasVindasPadraoEProtegemContraExclusao() throws Exception {
        JsonNode listaInicial = corpo(pedir(get("/api/marketing/modelos-mensagem"), TOKEN_LEITURA)
                .andExpect(status().isOk())).at("/data/itens");
        assertThat(listaInicial).hasSize(1);
        UUID boasVindasId = UUID.fromString(listaInicial.get(0).get("id").asText());
        assertThat(listaInicial.get(0).get("tipo").asText()).isEqualTo("boas_vindas");

        // Não pode excluir o modelo de boas-vindas
        pedir(delete("/api/marketing/modelos-mensagem/" + boasVindasId), TOKEN_COMPLETO)
                .andExpect(status().isUnprocessableEntity());

        // Criar modelo de aquecimento
        JsonNode criado = corpo(pedir(post("/api/marketing/modelos-mensagem")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "tipo": "aquecimento",
                          "nome": "Reengajamento 3 dias",
                          "etapaAlvo": 1,
                          "ordemSequencia": 1,
                          "diasAposAnterior": 3,
                          "assunto": "Olá {{nome}}, podemos conversar?",
                          "corpo": "Vimos seu interesse via {{origem}}.",
                          "ativo": true
                        }
                        """), TOKEN_COMPLETO)
                .andExpect(status().isCreated())).get("data");
        UUID aquecimentoId = UUID.fromString(criado.get("id").asText());

        // Outro tenant recebe 404
        pedir(delete("/api/marketing/modelos-mensagem/" + aquecimentoId), TOKEN_OUTRO_TENANT)
                .andExpect(status().isNotFound());

        // Excluir modelo de aquecimento faz soft delete
        pedir(delete("/api/marketing/modelos-mensagem/" + aquecimentoId), TOKEN_COMPLETO)
                .andExpect(status().isOk());
    }

    @Test
    void cicloDeVidaEQualificacaoComValoresIniciaisRegrasEFormulariosDoLanding() throws Exception {
        // Ciclo de vida inicial (D7) e alteração
        pedir(get("/api/marketing/ciclo-vida"), TOKEN_LEITURA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxTentativasAquecimento").value(3))
                .andExpect(jsonPath("$.data.mesesRetencaoPosEsgotamento").value(6))
                .andExpect(jsonPath("$.data.mesesRetencaoPosOptout").value(12));

        pedir(put("/api/marketing/ciclo-vida").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxTentativasAquecimento\":5,\"mesesRetencaoPosEsgotamento\":12,\"mesesRetencaoPosOptout\":24}"),
                TOKEN_COMPLETO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxTentativasAquecimento").value(5));

        // Regras de pontuação iniciais (D7: 2 de utm + R5: 4 de comportamento = 6)
        pedir(get("/api/marketing/regras-pontuacao"), TOKEN_LEITURA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(6))));

        // Criar regra de comportamento (R5)
        JsonNode novaRegra = corpo(pedir(post("/api/marketing/regras-pontuacao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "criterio": "Acesso via celular",
                          "fonte": "comportamento",
                          "campoComportamento": "tipo_dispositivo",
                          "valores": ["smartphone"],
                          "pontos": 10,
                          "ativo": true
                        }
                        """), TOKEN_COMPLETO)
                .andExpect(status().isCreated())).get("data");
        assertThat(novaRegra.get("fonte").asText()).isEqualTo("comportamento");
        assertThat(novaRegra.get("campoComportamento").asText()).isEqualTo("tipo_dispositivo");

        // Formulários do Landing lidos via resumo (A3)
        UUID formularioId = UUID.randomUUID();
        UUID campoCargoId = UUID.randomUUID();
        TenantContexto.executar(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.atualizarContato("Maria", "maria@exemplo.dev", null, null);
            lead.vincularEnvio(UUID.randomUUID(), formularioId, CanalLead.LANDING_PAGE);
            lead.registrarPontuacao(80, FaixaQualificacao.MQL_QUENTE, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
            leads.save(lead);
        });

        ModulosSimulados.LANDING.stubFor(ModulosSimulados.comTokenDeServico(
                WireMock.get(urlPathEqualTo("/api/landing/formularios/" + formularioId + "/resumo")), tenant)
                .willReturn(Envelopes.ok(Map.of(
                        "id", formularioId.toString(),
                        "nome", "Formulário Comercial",
                        "status", "ATIVO",
                        "campos", List.of(Map.of(
                                "id", campoCargoId.toString(),
                                "ordem", 1,
                                "rotulo", "Cargo",
                                "tipo", "LISTA_OPCOES",
                                "obrigatorio", true,
                                "opcoes", List.of("Decisor", "Analista")))))));

        pedir(get("/api/marketing/qualificacao/formularios"), TOKEN_LEITURA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(formularioId.toString()))
                .andExpect(jsonPath("$.data[0].nome").value("Formulário Comercial"))
                .andExpect(jsonPath("$.data[0].campos[0].rotulo").value("Cargo"))
                .andExpect(jsonPath("$.data[0].campos[0].opcoes[0]").value("Decisor"));

        // Qualificação com prévia de faixas e escala do rodízio
        pedir(get("/api/marketing/qualificacao"), TOKEN_LEITURA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limiteMqlQuente").value(70))
                .andExpect(jsonPath("$.data.previaFaixas.mqlQuente").value(1));

        UUID vendedor1 = UUID.randomUUID();
        UUID vendedor2 = UUID.randomUUID();
        pedir(put("/api/marketing/rodizio/vendedores").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedores\":[{\"vendedorId\":\"" + vendedor1 + "\",\"ativo\":true},"
                        + "{\"vendedorId\":\"" + vendedor2 + "\",\"ativo\":false}]}"),
                TOKEN_COMPLETO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    private ResultActions pedir(MockHttpServletRequestBuilder requisicao, String token) throws Exception {
        return mvc.perform(requisicao.header("Authorization", "Bearer " + token));
    }

    private JsonNode corpo(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsByteArray());
    }

    private Jwt usuario(UUID tenantDoToken, String... permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", usuario.toString(), "tenant_id", tenantDoToken.toString(), "perms", List.of(permissoes)));
    }
}
