package br.com.centinela.marketing.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;

/**
 * Resumo do painel (A4) e o bloco do lead 0 (R6): os números de cada tenant, o isolamento, os filtros, o
 * custo estimado, o cache e as permissões. O fluxo SSE tem o teste próprio, {@link PainelEventosTest}.
 */
@AutoConfigureMockMvc
class PainelApiTest extends IntegracaoDeTeste {

    private static final String TOKEN_PAINEL = "token-painel";
    private static final String TOKEN_SEM_PERMISSAO = "token-sem-permissao";
    private static final String TOKEN_OUTRO_TENANT = "token-outro-tenant";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JwtDecoder decoder;

    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();
    private final OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void configurarTokens() {
        Map<String, Jwt> tokens = new HashMap<>();
        tokens.put(TOKEN_PAINEL, jwt(tenant, "marketing.dashboard.ver"));
        tokens.put(TOKEN_SEM_PERMISSAO, jwt(tenant, "marketing.lead.ver"));
        tokens.put(TOKEN_OUTRO_TENANT, jwt(outroTenant, "marketing.dashboard.ver"));
        when(decoder.decode(anyString())).thenAnswer(chamada -> {
            Jwt token = tokens.get(chamada.<String>getArgument(0));
            if (token == null) {
                throw new BadJwtException("token desconhecido");
            }
            return token;
        });
    }

    @Test
    void resumoTrazOsNumerosDoTenantSemMisturarOsDeOutro() throws Exception {
        UUID vendedor = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        OffsetDateTime anteontem = agora.minusDays(2);
        OffsetDateTime ontem = agora.minusDays(1);

        // Meta, etapa 0: dois da campanha (nutrição e MQL morno com vendedor) e um anonimizado
        lead().origem("meta_ads").etapa(0).nome("Ana").utm("facebook", "paid", "black-friday-2026")
                .pontuacao("nutricao", 20).criadoEm(anteontem).gravar(tenant);
        lead().origem("meta_ads").etapa(0).nome("Bia").utm("facebook", "paid", "black-friday-2026")
                .pontuacao("mql_morno", 50).vendedor(vendedor).criadoEm(anteontem).gravar(tenant);
        lead().origem("meta_ads").etapa(0).nome("Anonimizado").anonimizado().criadoEm(anteontem).gravar(tenant);
        // Chatbot (etapa 1) vindo do Google Ads, e um sem pontuação
        lead().origem("landing_page").etapa(1).canal("chatbot").nome("Caio").utm("google", "cpc", "search-2026")
                .pontuacao("nutricao", 10).criadoEm(ontem).gravar(tenant);
        lead().origem("landing_page").etapa(1).canal("chatbot").nome("Duda").criadoEm(ontem).gravar(tenant);
        // Passou da etapa 1 para a 3: entra na conversão
        UUID maria = lead().origem("landing_page").etapa(3).canal("embed").nome("Maria")
                .utm("google", "cpc", "search-2026").pontuacao("mql_quente", 85).vendedor(vendedor)
                .oportunidade(oportunidade).criadoEm(ontem).gravar(tenant);
        evento(tenant, maria, "etapa_alterada", null, 1, "{}");
        evento(tenant, maria, "etapa_alterada", 1, 3, "{}");
        // Entrou direto na etapa 3 e aguarda vendedor: não passou pela 1
        lead().origem("landing_page").etapa(3).canal("formulario").nome("João").email("joao@exemplo.dev")
                .pontuacao("mql_quente", 90).criadoEm(ontem).gravar(tenant);
        // Excluído e fora do período não entram no padrão de 30 dias
        lead().origem("landing_page").etapa(3).nome("Excluído").excluido().criadoEm(ontem).gravar(tenant);
        lead().origem("landing_page").etapa(2).nome("Antigo").criadoEm(agora.minusDays(100)).gravar(tenant);
        // Outro tenant
        lead().origem("meta_ads").etapa(0).nome("Estranho").criadoEm(ontem).gravar(outroTenant);
        canal(tenant, "meta_ads", new BigDecimal("1500.00"));

        JsonNode dados = dados(pedir("/api/marketing/painel", TOKEN_PAINEL).andExpect(status().isOk()));

        assertThat(dados.at("/funil/etapa0").asInt()).isEqualTo(3);
        assertThat(dados.at("/funil/etapa1").asInt()).isEqualTo(2);
        assertThat(dados.at("/funil/etapa2").asInt()).isZero();
        assertThat(dados.at("/funil/etapa3").asInt()).isEqualTo(2);
        assertThat(dados.at("/qualificacao/mqlQuente").asInt()).isEqualTo(2);
        assertThat(dados.at("/qualificacao/mqlMorno").asInt()).isEqualTo(1);
        assertThat(dados.at("/qualificacao/nutricao").asInt()).isEqualTo(2);
        assertThat(dados.at("/qualificacao/semPontuacao").asInt()).isEqualTo(2);
        // 3 MQL em 7 leads
        assertThat(dados.at("/taxaConversao/leadsParaMql").asDouble()).isEqualTo(42.8571);
        // Passaram pela etapa 1: Caio, Duda e Maria; chegou à 3: Maria
        assertThat(dados.at("/taxaConversao/etapa1ParaEtapa3").asDouble()).isEqualTo(33.3333);

        JsonNode serie = dados.get("serieTemporal");
        assertThat(serie).hasSize(2);
        assertThat(serie.get(0).get("data").asText()).isEqualTo(anteontem.toLocalDate().toString());
        assertThat(serie.get(0).get("origem").asText()).isEqualTo("meta_ads");
        assertThat(serie.get(0).get("novosLeads").asInt()).isEqualTo(3);
        assertThat(serie.get(1).get("data").asText()).isEqualTo(ontem.toLocalDate().toString());
        assertThat(serie.get(1).get("origem").asText()).isEqualTo("landing_page");
        assertThat(serie.get(1).get("novosLeads").asInt()).isEqualTo(4);

        JsonNode campanhas = dados.get("campanhas");
        assertThat(campanhas).hasSize(2);
        assertCampanha(campanhas.get(0), "black-friday-2026", "meta_ads", 2, 1, 0);
        assertCampanha(campanhas.get(1), "search-2026", "google_ads", 2, 1, 1);

        JsonNode canais = dados.get("canais");
        assertThat(canais).hasSize(2);
        assertThat(canais.get(0).get("canal").asText()).isEqualTo("meta_ads");
        assertThat(canais.get(0).get("orcamentoMensal").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(canais.get(0).get("leads").asInt()).isEqualTo(3);
        assertThat(canais.get(0).get("mql").asInt()).isEqualTo(1);
        assertThat(canais.get(0).get("custoPorLead").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(canais.get(0).get("custoPorMql").decimalValue()).isEqualByComparingTo("1500.00");
        // Google Ads tem leads, mas nenhum orçamento: sem custo
        assertThat(canais.get(1).get("canal").asText()).isEqualTo("google_ads");
        assertThat(canais.get(1).get("leads").asInt()).isEqualTo(2);
        assertThat(canais.get(1).get("mql").asInt()).isEqualTo(1);
        assertThat(canais.get(1).get("orcamentoMensal").isNull()).isTrue();
        assertThat(canais.get(1).get("custoPorLead").isNull()).isTrue();
        assertThat(canais.get(1).get("custoPorMql").isNull()).isTrue();

        JsonNode distribuicao = dados.get("distribuicao");
        assertThat(distribuicao.at("/porVendedor")).hasSize(1);
        assertThat(distribuicao.at("/porVendedor/0/vendedorId").asText()).isEqualTo(vendedor.toString());
        assertThat(distribuicao.at("/porVendedor/0/nome").isNull()).isTrue();
        assertThat(distribuicao.at("/porVendedor/0/atribuidos").asInt()).isEqualTo(2);
        assertThat(distribuicao.get("aguardandoVendedor").asInt()).isEqualTo(1);

        JsonNode recentes = dados.get("leadsRecentes");
        assertThat(recentes).hasSize(7);
        assertThat(nomes(recentes)).doesNotContain("Estranho", "Excluído", "Antigo", "Anonimizado");
        JsonNode noCrm = recentes.findParents("crmOportunidadeId").stream()
                .filter(lead -> "Maria".equals(lead.get("nome").asText(null))).findFirst().orElseThrow();
        assertThat(noCrm.get("crmOportunidadeId").asText()).isEqualTo(oportunidade.toString());
        assertThat(noCrm.get("etapa").asInt()).isEqualTo(3);
        assertThat(noCrm.get("faixa").asText()).isEqualTo("mql_quente");
        assertThat(noCrm.get("canal").asText()).isEqualTo("embed");
        // Anonimizado conta nos números e aparece sem nome
        long semNome = java.util.stream.StreamSupport.stream(recentes.spliterator(), false)
                .filter(lead -> lead.get("nome").isNull()).count();
        assertThat(semNome).isEqualTo(1);

        // Sem visitas, o lead 0 vem zerado
        assertThat(dados.at("/leadZero/cliques").asInt()).isZero();
        assertThat(dados.at("/leadZero/taxaConversao").asDouble()).isZero();
        assertThat(dados.at("/leadZero/porCampanha")).isEmpty();

        // O outro tenant só vê o dele
        JsonNode doOutro = dados(pedir("/api/marketing/painel", TOKEN_OUTRO_TENANT).andExpect(status().isOk()));
        assertThat(doOutro.at("/funil/etapa0").asInt()).isEqualTo(1);
        assertThat(doOutro.at("/funil/etapa3").asInt()).isZero();
        // O orçamento do primeiro tenant não vaza: o canal aparece só com o lead dele e sem custo
        assertThat(doOutro.at("/canais")).hasSize(1);
        assertThat(doOutro.at("/canais/0/leads").asInt()).isEqualTo(1);
        assertThat(doOutro.at("/canais/0/orcamentoMensal").isNull()).isTrue();
        assertThat(doOutro.at("/canais/0/custoPorLead").isNull()).isTrue();
        assertThat(doOutro.at("/distribuicao/porVendedor")).isEmpty();
        assertThat(nomes(doOutro.get("leadsRecentes"))).containsExactly("Estranho");
    }

    @Test
    void filtrosDePeriodoOrigemECampanhaSomamEntreSi() throws Exception {
        lead().origem("meta_ads").etapa(0).utm("facebook", "paid", "Black-Friday-2026").criadoEm(agora.minusDays(3))
                .gravar(tenant);
        lead().origem("landing_page").etapa(3).utm("google", "cpc", "black-friday-2026").criadoEm(agora.minusDays(3))
                .gravar(tenant);
        lead().origem("landing_page").etapa(2).criadoEm(agora.minusDays(3)).gravar(tenant);
        lead().origem("landing_page").etapa(2).criadoEm(agora.minusDays(100)).gravar(tenant);

        JsonNode padrao = dados(pedir("/api/marketing/painel", TOKEN_PAINEL).andExpect(status().isOk()));
        assertThat(padrao.at("/funil/etapa2").asInt()).isEqualTo(1);

        // 7d, 90d e 120d: o antigo só entra no último
        assertThat(dados(pedir("/api/marketing/painel?periodo=7d", TOKEN_PAINEL)).at("/funil/etapa2").asInt()).isEqualTo(1);
        assertThat(dados(pedir("/api/marketing/painel?periodo=90d", TOKEN_PAINEL)).at("/funil/etapa2").asInt()).isEqualTo(1);
        assertThat(dados(pedir("/api/marketing/painel?periodo=120d", TOKEN_PAINEL)).at("/funil/etapa2").asInt()).isEqualTo(2);

        // Intervalo de datas: só o dia dos três recentes
        LocalDate dia = agora.minusDays(3).toLocalDate();
        JsonNode intervalo = dados(pedir("/api/marketing/painel?periodo=" + dia + "," + dia, TOKEN_PAINEL));
        assertThat(intervalo.at("/funil/etapa0").asInt()).isEqualTo(1);
        assertThat(intervalo.at("/funil/etapa2").asInt()).isEqualTo(1);
        assertThat(intervalo.at("/funil/etapa3").asInt()).isEqualTo(1);

        JsonNode origem = dados(pedir("/api/marketing/painel?origem=meta_ads", TOKEN_PAINEL));
        assertThat(origem.at("/funil/etapa0").asInt()).isEqualTo(1);
        assertThat(origem.at("/funil/etapa3").asInt()).isZero();
        assertThat(dados(pedir("/api/marketing/painel?origem=", TOKEN_PAINEL)).at("/funil/etapa3").asInt()).isEqualTo(1);

        // A campanha vale sem diferenciar maiúsculas, como na lista de leads
        JsonNode campanha = dados(pedir("/api/marketing/painel?campanha=BLACK-friday-2026", TOKEN_PAINEL));
        assertThat(campanha.at("/funil/etapa0").asInt()).isEqualTo(1);
        assertThat(campanha.at("/funil/etapa3").asInt()).isEqualTo(1);
        assertThat(campanha.at("/funil/etapa2").asInt()).isZero();

        JsonNode combinado = dados(pedir("/api/marketing/painel?origem=landing_page&campanha=black-friday-2026", TOKEN_PAINEL));
        assertThat(combinado.at("/funil/etapa0").asInt()).isZero();
        assertThat(combinado.at("/funil/etapa3").asInt()).isEqualTo(1);

        // tenantId na query é ignorado
        JsonNode ignorado = dados(pedir("/api/marketing/painel?tenantId=" + outroTenant, TOKEN_PAINEL));
        assertThat(ignorado.at("/funil/etapa3").asInt()).isEqualTo(1);
    }

    @Test
    void filtroInvalidoResponde400ComOErroPorCampo() throws Exception {
        pedir("/api/marketing/painel?periodo=ontem&origem=tiktok", TOKEN_PAINEL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].campo").value("periodo"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_INVALIDO"))
                .andExpect(jsonPath("$.errors[1].campo").value("origem"));
        pedir("/api/marketing/painel?periodo=2026-12-01,2026-11-01", TOKEN_PAINEL).andExpect(status().isBadRequest());
        pedir("/api/marketing/painel?periodo=999d", TOKEN_PAINEL).andExpect(status().isBadRequest());
        pedir("/api/marketing/painel?campanha=" + "x".repeat(256), TOKEN_PAINEL)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("campanha"));
    }

    @Test
    void semTokenResponde401ESemPermissaoResponde403() throws Exception {
        mvc.perform(get("/api/marketing/painel")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        pedir("/api/marketing/painel", TOKEN_SEM_PERMISSAO).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void leadZeroContaOsCliquesDoPeriodoEDaCampanha() throws Exception {
        UUID link = link(tenant, "bf-2026");
        UUID leadConvertido = lead().origem("landing_page").etapa(3).nome("Convertida").criadoEm(agora.minusDays(1))
                .gravar(tenant);
        String smartphone = "{\"tipoDispositivo\":\"smartphone\"}";
        visita(tenant, link, "ativa", agora.minusHours(1), "bf-2026", smartphone, null, false);
        visita(tenant, link, "convertida", agora.minusDays(1), "bf-2026", smartphone, leadConvertido, false);
        visita(tenant, link, "descartada", agora.minusDays(2), "bf-2026", null, null, false);
        visita(tenant, link, "descartada", agora.minusDays(3), "outra", null, null, false);
        // Fora do período e excluída: não contam
        visita(tenant, link, "descartada", agora.minusDays(40), "bf-2026", null, null, false);
        visita(tenant, link, "ativa", agora.minusHours(2), "bf-2026", smartphone, null, true);
        // Outro tenant
        visita(outroTenant, link(outroTenant, "bf-outro"), "ativa", agora.minusHours(1), "bf-2026", smartphone, null, false);

        JsonNode zero = dados(pedir("/api/marketing/painel", TOKEN_PAINEL).andExpect(status().isOk())).get("leadZero");
        assertThat(zero.get("cliques").asInt()).isEqualTo(4);
        assertThat(zero.get("ativas").asInt()).isEqualTo(1);
        assertThat(zero.get("convertidas").asInt()).isEqualTo(1);
        assertThat(zero.get("descartadas").asInt()).isEqualTo(2);
        assertThat(zero.get("taxaConversao").asDouble()).isEqualTo(25.0);
        JsonNode porCampanha = zero.get("porCampanha");
        assertThat(porCampanha).hasSize(2);
        assertThat(porCampanha.get(0).get("utmCampaign").asText()).isEqualTo("bf-2026");
        assertThat(porCampanha.get(0).get("cliques").asInt()).isEqualTo(3);
        assertThat(porCampanha.get(0).get("convertidas").asInt()).isEqualTo(1);
        assertThat(porCampanha.get(0).get("descartadas").asInt()).isEqualTo(1);
        assertThat(porCampanha.get(1).get("utmCampaign").asText()).isEqualTo("outra");
        assertThat(porCampanha.get(1).get("cliques").asInt()).isEqualTo(1);
        assertThat(porCampanha.get(1).get("descartadas").asInt()).isEqualTo(1);
        JsonNode porDispositivo = zero.get("porDispositivo");
        assertThat(porDispositivo).hasSize(2);
        assertThat(porDispositivo.get(0).get("tipoDispositivo").asText()).isEqualTo("desconhecido");
        assertThat(porDispositivo.get(0).get("cliques").asInt()).isEqualTo(2);
        assertThat(porDispositivo.get(1).get("tipoDispositivo").asText()).isEqualTo("smartphone");
        assertThat(porDispositivo.get(1).get("cliques").asInt()).isEqualTo(2);
        assertThat(porDispositivo.get(1).get("convertidas").asInt()).isEqualTo(1);

        // O filtro de campanha vale para o lead 0; a origem não se aplica a ele
        JsonNode daCampanha = dados(pedir("/api/marketing/painel?campanha=BF-2026&origem=meta_ads", TOKEN_PAINEL))
                .get("leadZero");
        assertThat(daCampanha.get("cliques").asInt()).isEqualTo(3);
        assertThat(daCampanha.get("porCampanha")).hasSize(1);
        // Período: as visitas de 40 dias atrás entram
        assertThat(dados(pedir("/api/marketing/painel?periodo=60d", TOKEN_PAINEL)).at("/leadZero/cliques").asInt())
                .isEqualTo(5);
    }

    @Test
    void resumoFicaEmCachePorTenantEFiltros() throws Exception {
        lead().origem("meta_ads").etapa(0).criadoEm(agora.minusDays(1)).gravar(tenant);
        assertThat(dados(pedir("/api/marketing/painel", TOKEN_PAINEL)).at("/funil/etapa0").asInt()).isEqualTo(1);

        lead().origem("meta_ads").etapa(0).criadoEm(agora.minusDays(1)).gravar(tenant);
        // Mesmo tenant e mesmos filtros: o resumo guardado, ainda sem o segundo lead
        assertThat(dados(pedir("/api/marketing/painel", TOKEN_PAINEL)).at("/funil/etapa0").asInt()).isEqualTo(1);
        // Outros filtros: outra chave, resumo novo
        assertThat(dados(pedir("/api/marketing/painel?periodo=29d", TOKEN_PAINEL)).at("/funil/etapa0").asInt())
                .isEqualTo(2);
        // Outro tenant com os mesmos filtros: nunca recebe o resumo do primeiro
        assertThat(dados(pedir("/api/marketing/painel", TOKEN_OUTRO_TENANT)).at("/funil/etapa0").asInt()).isZero();
    }

    // ---- apoio ----

    private ResultActions pedir(String caminho, String token) throws Exception {
        MockHttpServletRequestBuilder requisicao = get(caminho);
        return mvc.perform(requisicao.header("Authorization", "Bearer " + token));
    }

    private JsonNode dados(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsByteArray()).get("data");
    }

    private static void assertCampanha(JsonNode campanha, String nome, String canal, int leads, int mql, int etapa3) {
        assertThat(campanha.get("utmCampaign").asText()).isEqualTo(nome);
        assertThat(campanha.get("canal").asText()).isEqualTo(canal);
        assertThat(campanha.get("leads").asInt()).isEqualTo(leads);
        assertThat(campanha.get("mql").asInt()).isEqualTo(mql);
        assertThat(campanha.get("etapa3").asInt()).isEqualTo(etapa3);
    }

    private static List<String> nomes(JsonNode leads) {
        return java.util.stream.StreamSupport.stream(leads.spliterator(), false)
                .map(lead -> lead.get("nome").asText(null)).filter(java.util.Objects::nonNull).toList();
    }

    private Jwt jwt(UUID tenantDoToken, String... permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", usuario.toString(), "tenant_id", tenantDoToken.toString(), "perms", List.of(permissoes)));
    }

    private NovoLead lead() {
        return new NovoLead();
    }

    private void evento(UUID tenantDoEvento, UUID lead, String tipo, Integer etapaAnterior, Integer etapaNova,
            String payload) {
        jdbc.update("INSERT INTO marketing.lead_eventos (id, tenant_id, lead_id, tipo_evento, etapa_anterior, "
                + "etapa_nova, payload, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())",
                UUID.randomUUID(), tenantDoEvento, lead, tipo, etapaAnterior, etapaNova, payload);
    }

    private void canal(UUID tenantDoCanal, String canal, BigDecimal orcamento) {
        jdbc.update("INSERT INTO marketing.canais_anuncio (id, tenant_id, canal, orcamento_mensal, ativo, "
                + "created_at, updated_at) VALUES (?, ?, ?, ?, true, now(), now())",
                UUID.randomUUID(), tenantDoCanal, canal, orcamento);
    }

    private UUID link(UUID tenantDoLink, String campanha) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketing.links_campanha (id, tenant_id, codigo, nome, url_destino, utm_campaign, "
                + "ativo, created_at, updated_at) VALUES (?, ?, ?, ?, 'https://exemplo.dev/lp', ?, true, now(), now())",
                id, tenantDoLink, id.toString().replace("-", "").substring(0, 12), "Link " + campanha, campanha);
        return id;
    }

    private void visita(UUID tenantDaVisita, UUID link, String situacao, OffsetDateTime clicadoEm, String campanha,
            String contexto, UUID lead, boolean excluida) {
        jdbc.update("INSERT INTO marketing.visitas_anuncio (id, tenant_id, link_id, situacao, clicado_em, expira_em, "
                + "utm_campaign, contexto_tecnico, lead_id, convertida_em, created_at, updated_at, deleted_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, now(), now(), ?)",
                UUID.randomUUID(), tenantDaVisita, link, situacao, clicadoEm, clicadoEm.plusMinutes(30), campanha,
                contexto, lead, lead == null ? null : clicadoEm, excluida ? agora : null);
    }

    /** Lead gravado direto no banco, para controlar etapa, datas e UTM. */
    private final class NovoLead {

        private String origem = "landing_page";
        private int etapa;
        private String nome;
        private String email;
        private String canal;
        private String utmSource;
        private String utmMedium;
        private String utmCampaign;
        private String faixa;
        private Integer pontuacao;
        private UUID vendedor;
        private UUID oportunidade;
        private boolean anonimizado;
        private boolean excluido;
        private OffsetDateTime criadoEm = agora;

        NovoLead origem(String valor) {
            origem = valor;
            return this;
        }

        NovoLead etapa(int valor) {
            etapa = valor;
            return this;
        }

        NovoLead nome(String valor) {
            nome = valor;
            return this;
        }

        NovoLead email(String valor) {
            email = valor;
            return this;
        }

        NovoLead canal(String valor) {
            canal = valor;
            return this;
        }

        NovoLead utm(String source, String medium, String campaign) {
            utmSource = source;
            utmMedium = medium;
            utmCampaign = campaign;
            return this;
        }

        NovoLead pontuacao(String faixaDoLead, int pontos) {
            faixa = faixaDoLead;
            pontuacao = pontos;
            return this;
        }

        NovoLead vendedor(UUID valor) {
            vendedor = valor;
            return this;
        }

        NovoLead oportunidade(UUID valor) {
            oportunidade = valor;
            return this;
        }

        NovoLead anonimizado() {
            anonimizado = true;
            return this;
        }

        NovoLead excluido() {
            excluido = true;
            return this;
        }

        NovoLead criadoEm(OffsetDateTime valor) {
            criadoEm = valor;
            return this;
        }

        UUID gravar(UUID tenantDoLead) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO marketing.leads (id, tenant_id, etapa, origem, nome, email, canal, "
                    + "utm_source, utm_medium, utm_campaign, pontuacao, faixa_qualificacao, vendedor_id, "
                    + "crm_oportunidade_id, anonimizado, token_descadastro, created_at, updated_at, deleted_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, tenantDoLead, etapa, origem, nome, email, canal, utmSource, utmMedium, utmCampaign, pontuacao,
                    faixa, vendedor, oportunidade, anonimizado, id.toString().replace("-", ""), criadoEm, criadoEm,
                    excluido ? agora : null);
            return id;
        }
    }
}
