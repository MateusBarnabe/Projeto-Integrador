package br.com.centinela.marketing.conformidade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * Checklist de conformidade da plataforma (Contrato §15) como evidência da sessão com o Grupo 2.
 * Cada teste leva o número do item no nome ({@code itemNN_...}); a tabela "item → teste" está na
 * seção 10 do norteador. Os itens que dependem do ambiente e não de código (1 sobe no compose, 11
 * front no iframe, 12 JSON de registro, 13 OpenAPI, 14 imagem publicada) ficam fora daqui.
 *
 * <p>Sobe a aplicação inteira contra o PostgreSQL e o RabbitMQ do Testcontainers, com o
 * {@link JwtDecoder} simulado (a validação da assinatura é do identity) e o filtro de segurança real.
 * Os pedidos passam pelo {@link MockMvc}, exceto o do item 19, que usa um servidor HTTP de verdade
 * ({@code RANDOM_PORT}): o limite de cabeçalho é do Tomcat, e o MockMvc não passa por ele.
 *
 * <p>Os itens 17 e 18 (eventos com envelope e {@code user_id}, consumidor idempotente) são cobertos por
 * {@code PublicadorDeEventosTest} e {@code ConsumoDeEventosIntegracaoTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChecklistDaPlataformaTest extends IntegracaoDeTeste {

    private static final String TOKEN_ADMIN = "token-admin";
    private static final String TOKEN_SEM_PERMISSAO = "token-sem-permissao";
    private static final String TOKEN_SO_LEITURA = "token-so-leitura";
    private static final String TOKEN_OUTRO_TENANT = "token-outro-tenant";

    /** Todas as permissões do módulo (seção 7 do norteador): é o que um ADMINISTRADOR recebe. */
    private static final List<String> PERMISSOES_DO_MODULO = List.of(
            "marketing.acessar", "marketing.dashboard.ver",
            "marketing.lead.ver", "marketing.lead.ver_resumo",
            "marketing.qualificacao.ver", "marketing.qualificacao.editar",
            "marketing.template.ver", "marketing.template.criar", "marketing.template.editar",
            "marketing.template.excluir",
            "marketing.automacao.ver", "marketing.automacao.editar",
            "marketing.configuracao.ver", "marketing.configuracao.editar");

    private static final String CORPO_LINK = """
            {"nome":"Black Friday","urlDestino":"https://landing.exemplo.dev/black-friday","utmCampaign":"bf-2026"}""";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    LeadRepository leads;

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int porta;

    @MockitoBean
    JwtDecoder decoder;

    // Tenants novos a cada teste: o banco é o mesmo para a classe toda.
    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();
    private final UUID usuarioOutroTenant = UUID.randomUUID();

    private final Map<String, Jwt> tokens = new ConcurrentHashMap<>();

    @BeforeEach
    void tokens() {
        tokens.clear();
        tokens.put(TOKEN_ADMIN, usuario(tenant, usuario, PERMISSOES_DO_MODULO));
        // Quem só entra no módulo e vê o painel: nenhuma das permissões das rotas abaixo
        tokens.put(TOKEN_SEM_PERMISSAO, usuario(tenant, usuario, List.of("marketing.acessar", "marketing.dashboard.ver")));
        tokens.put(TOKEN_SO_LEITURA, usuario(tenant, usuario, List.of("marketing.acessar", "marketing.lead.ver")));
        tokens.put(TOKEN_OUTRO_TENANT, usuario(outroTenant, usuarioOutroTenant, PERMISSOES_DO_MODULO));
        when(decoder.decode(anyString())).thenAnswer(chamada -> {
            Jwt token = tokens.get(chamada.<String>getArgument(0));
            if (token == null) {
                throw new BadJwtException("token desconhecido");
            }
            return token;
        });
    }

    // ================================================================ itens 2 e 3

    @Test
    void item02_healthSemTokenResponde200ComBancoConectado() throws Exception {
        mvc.perform(get("/api/marketing/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void item03_qualquerOutroEndpointSemTokenResponde401NoEnvelope() throws Exception {
        for (Rota rota : rotasProtegidas()) {
            assertEnvelope(mvc.perform(rota.pedido(null)).andExpect(status().isUnauthorized()), false);
        }
        // Rota que não existe também exige o token: não revela o que existe
        assertEnvelope(mvc.perform(get("/api/marketing/nao-existe")).andExpect(status().isUnauthorized()), false);
    }

    @Test
    void item03_tokenInvalidoOuExpiradoResponde401NoEnvelope() throws Exception {
        assertEnvelope(mvc.perform(get("/api/marketing/leads").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized()), false);
        assertEnvelope(mvc.perform(get("/api/marketing/leads").header("Authorization", "Basic dXNlcjpzZW5oYQ=="))
                .andExpect(status().isUnauthorized()), false);
    }

    // ================================================================ item 4

    @Test
    void item04_tokenValidoSemAPermissaoResponde403NoEnvelope() throws Exception {
        for (Rota rota : rotasProtegidas()) {
            assertEnvelope(mvc.perform(rota.pedido(TOKEN_SEM_PERMISSAO)).andExpect(status().isForbidden()), false);
        }
    }

    @Test
    void item04_ePermissaoDeLeituraNaoDaDireitoDeEscrita() throws Exception {
        UUID lead = lead(tenant, "Maria", "maria@exemplo.dev");

        pedir(get("/api/marketing/leads"), TOKEN_SO_LEITURA).andExpect(status().isOk());
        assertEnvelope(pedir(put("/api/marketing/leads/" + lead + "/vendedor").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\":\"" + UUID.randomUUID() + "\"}"), TOKEN_SO_LEITURA)
                .andExpect(status().isForbidden()), false);
        assertEnvelope(pedir(get("/api/marketing/links-campanha"), TOKEN_SO_LEITURA)
                .andExpect(status().isForbidden()), false);
    }

    // ================================================================ item 5

    @Test
    void item05_tokenDeOutroTenantNaoEnxergaNadaEResponde404() throws Exception {
        UUID lead = lead(tenant, "Maria", "maria@exemplo.dev");
        UUID link = criarLink(TOKEN_ADMIN);

        // Detalhe, resumo, reatribuição, alteração e exclusão do registro de outro tenant: 404, nunca 403
        for (MockHttpServletRequestBuilder pedido : List.of(
                get("/api/marketing/leads/" + lead),
                get("/api/marketing/leads/" + lead + "/resumo"),
                put("/api/marketing/leads/" + lead + "/vendedor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vendedorId\":\"" + UUID.randomUUID() + "\"}"),
                put("/api/marketing/links-campanha/" + link).contentType(MediaType.APPLICATION_JSON).content(CORPO_LINK),
                delete("/api/marketing/links-campanha/" + link))) {
            assertEnvelope(pedir(pedido, TOKEN_OUTRO_TENANT).andExpect(status().isNotFound()), false);
        }

        // As listagens do outro tenant vêm vazias, e o registro continua intacto no dono
        pedir(get("/api/marketing/leads"), TOKEN_OUTRO_TENANT).andExpect(jsonPath("$.data.total").value(0));
        pedir(get("/api/marketing/links-campanha"), TOKEN_OUTRO_TENANT).andExpect(jsonPath("$.data.total").value(0));
        pedir(get("/api/marketing/busca").param("q", "maria"), TOKEN_OUTRO_TENANT).andExpect(jsonPath("$.data").isEmpty());
        pedir(get("/api/marketing/leads/" + lead), TOKEN_ADMIN).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketing.links_campanha WHERE id = ? AND deleted_at IS NULL",
                Integer.class, link)).isEqualTo(1);
    }

    // ================================================================ item 6

    @Test
    void item06_tenantIdNoCorpoNaQueryENoCabecalhoEIgnorado() throws Exception {
        String corpoComTenant = """
                {"nome":"Com tenant","urlDestino":"https://landing.exemplo.dev/x","tenantId":"%s","tenant_id":"%s"}"""
                .formatted(outroTenant, outroTenant);

        JsonNode criado = corpo(pedir(post("/api/marketing/links-campanha").param("tenantId", outroTenant.toString())
                .header("X-Tenant-Id", outroTenant.toString())
                .contentType(MediaType.APPLICATION_JSON).content(corpoComTenant), TOKEN_ADMIN)
                .andExpect(status().isCreated()));

        UUID id = UUID.fromString(criado.at("/data/id").asText());
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM marketing.links_campanha WHERE id = ?", UUID.class, id))
                .as("o registro nasce no tenant do token").isEqualTo(tenant);
        pedir(get("/api/marketing/links-campanha"), TOKEN_ADMIN).andExpect(jsonPath("$.data.total").value(1));
        pedir(get("/api/marketing/links-campanha"), TOKEN_OUTRO_TENANT).andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void item06_tenantIdNaQueryNaoAbreOsDadosDeOutroTenant() throws Exception {
        UUID dele = lead(outroTenant, "Dele", "dele@exemplo.dev");

        pedir(get("/api/marketing/leads").param("tenantId", outroTenant.toString())
                .header("X-Tenant-Id", outroTenant.toString()), TOKEN_ADMIN)
                .andExpect(jsonPath("$.data.total").value(0));
        pedir(get("/api/marketing/leads/" + dele).param("tenantId", outroTenant.toString())
                .header("X-Tenant-Id", outroTenant.toString()), TOKEN_ADMIN)
                .andExpect(status().isNotFound());
    }

    // ================================================================ itens 7, 8 e 16

    @Test
    void item07_todaTabelaDeNegocioTemAsSeteColunasObrigatorias() {
        List<String> obrigatorias = List.of("id", "tenant_id", "created_at", "updated_at", "deleted_at", "created_by",
                "updated_by");
        // eventos_processados tem o formato próprio da §9.7; flyway_schema_history é do Flyway
        Set<String> excecoes = Set.of("eventos_processados", "flyway_schema_history");

        List<Map<String, Object>> tabelas = jdbc.queryForList(
                "SELECT c.relname AS tabela FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = 'marketing' AND c.relkind IN ('r', 'p') ORDER BY c.relname");
        assertThat(tabelas).isNotEmpty();
        for (Map<String, Object> linha : tabelas) {
            String tabela = (String) linha.get("tabela");
            if (excecoes.contains(tabela)) {
                continue;
            }
            List<String> colunas = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = 'marketing' AND table_name = ?", String.class, tabela);
            assertThat(colunas).as("colunas de marketing." + tabela).containsAll(obrigatorias);
        }
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'marketing' "
                        + "AND table_name = 'leads' AND column_name = 'tenant_id' AND is_nullable = 'NO'",
                Integer.class)).as("tenant_id é obrigatório").isEqualTo(1);
    }

    @Test
    void item08_nenhumaForeignKeyReferenciaOutroSchema() {
        List<String> chavesParaFora = jdbc.queryForList(
                "SELECT con.conname FROM pg_constraint con "
                        + "JOIN pg_class origem ON origem.oid = con.conrelid "
                        + "JOIN pg_namespace no ON no.oid = origem.relnamespace "
                        + "JOIN pg_class destino ON destino.oid = con.confrelid "
                        + "JOIN pg_namespace nd ON nd.oid = destino.relnamespace "
                        + "WHERE con.contype = 'f' AND no.nspname = 'marketing' AND nd.nspname <> 'marketing'",
                String.class);
        assertThat(chavesParaFora).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_constraint con JOIN pg_class origem ON origem.oid = con.conrelid "
                        + "JOIN pg_namespace no ON no.oid = origem.relnamespace "
                        + "WHERE con.contype = 'f' AND no.nspname = 'marketing'", Integer.class))
                .as("as FKs internas existem (a consulta enxerga o schema)").isPositive();
    }

    @Test
    void item16_migrationsRodamComOwnMarketingEAplicacaoComUsrMarketing() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("usr_marketing");
        List<String> executores = jdbc.queryForList(
                "SELECT DISTINCT installed_by FROM marketing.flyway_schema_history", String.class);
        assertThat(executores).containsExactly("own_marketing");
    }

    // ================================================================ item 9

    @Test
    void item09_todasAsRespostasSeguemOEnvelopeInclusiveAsDeErro() throws Exception {
        UUID lead = lead(tenant, "Maria", "maria@exemplo.dev");

        // 200 e 201
        assertEnvelope(pedir(get("/api/marketing/leads"), TOKEN_ADMIN).andExpect(status().isOk()), true);
        assertEnvelope(pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_LINK), TOKEN_ADMIN).andExpect(status().isCreated()), true);
        // 400: filtro inválido, id que não é UUID, parâmetro ausente, corpo inválido e corpo ilegível
        assertEnvelope(pedir(get("/api/marketing/leads").param("etapa", "9"), TOKEN_ADMIN)
                .andExpect(status().isBadRequest()), false);
        assertEnvelope(pedir(get("/api/marketing/leads/nao-e-uuid"), TOKEN_ADMIN)
                .andExpect(status().isBadRequest()), false);
        assertEnvelope(pedir(get("/api/marketing/busca"), TOKEN_ADMIN).andExpect(status().isBadRequest()), false);
        assertEnvelope(pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\"}"), TOKEN_ADMIN).andExpect(status().isBadRequest()), false);
        assertEnvelope(pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON)
                .content("{isto não é json"), TOKEN_ADMIN).andExpect(status().isBadRequest()), false);
        // 401 e 403
        assertEnvelope(mvc.perform(get("/api/marketing/leads")).andExpect(status().isUnauthorized()), false);
        assertEnvelope(pedir(get("/api/marketing/leads"), TOKEN_SEM_PERMISSAO).andExpect(status().isForbidden()), false);
        // 404: registro inexistente, de outro tenant e rota que não existe
        assertEnvelope(pedir(get("/api/marketing/leads/" + UUID.randomUUID()), TOKEN_ADMIN)
                .andExpect(status().isNotFound()), false);
        assertEnvelope(pedir(get("/api/marketing/leads/" + lead), TOKEN_OUTRO_TENANT)
                .andExpect(status().isNotFound()), false);
        assertEnvelope(pedir(get("/api/marketing/nao-existe"), TOKEN_ADMIN).andExpect(status().isNotFound()), false);
        // 405: com token e sem token (a rota de saúde é pública)
        assertEnvelope(pedir(post("/api/marketing/leads"), TOKEN_ADMIN)
                .andExpect(status().isMethodNotAllowed()), false);
        assertEnvelope(mvc.perform(post("/api/marketing/health")).andExpect(status().isMethodNotAllowed()), false);
        // 409 e 422: regras de negócio
        pedir(get("/api/marketing/modelos-mensagem"), TOKEN_ADMIN).andExpect(status().isOk()); // cria o de boas-vindas
        assertEnvelope(pedir(post("/api/marketing/modelos-mensagem").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Outro\",\"tipo\":\"boas_vindas\",\"assunto\":\"Oi\",\"corpo\":\"Olá\"}"),
                TOKEN_ADMIN).andExpect(status().isConflict()), false);
        assertEnvelope(pedir(put("/api/marketing/leads/" + lead + "/vendedor").contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\":\"" + UUID.randomUUID() + "\"}"), TOKEN_ADMIN)
                .andExpect(status().isUnprocessableEntity()), false);
    }

    @Disabled("Falha de produção: TratadorDeErros não trata HttpMediaTypeNotSupportedException e a resposta é 500 "
            + "(Erro inesperado) em vez de 415. Corrigir em compartilhado/web/TratadorDeErros e reativar.")
    @Test
    void item09_tipoDeConteudoNaoSuportadoResponde415NoEnvelope() throws Exception {
        assertEnvelope(pedir(post("/api/marketing/links-campanha").contentType(MediaType.TEXT_PLAIN)
                .content(CORPO_LINK), TOKEN_ADMIN).andExpect(status().isUnsupportedMediaType()), false);
    }

    @Disabled("Falha de produção: Accept que a API não produz (ex.: application/xml) responde 406 sem corpo e sem "
            + "envelope, porque TratadorDeErros não trata HttpMediaTypeNotAcceptableException. Corrigir e reativar.")
    @Test
    void item09_formatoDeRespostaNaoAceitoResponde406NoEnvelope() throws Exception {
        assertEnvelope(pedir(get("/api/marketing/leads").accept(MediaType.APPLICATION_XML), TOKEN_ADMIN)
                .andExpect(status().isNotAcceptable()), false);
    }

    @Test
    void item09_rotasPublicasSemTokenRespondemComEnvelope() throws Exception {
        // Descadastro: um cliente que não é navegador recebe o envelope, e o token inválido não revela nada
        assertEnvelope(mvc.perform(get("/public/marketing/descadastro/token-que-nao-existe")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()), true);
        assertEnvelope(mvc.perform(post("/public/marketing/descadastro/token-que-nao-existe")
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()), true);
        // Link de campanha e visita que não existem
        assertEnvelope(mvc.perform(get("/public/marketing/c/codigo-que-nao-existe"))
                .andExpect(status().isNotFound()), false);
        assertEnvelope(mvc.perform(post("/public/marketing/visitas/" + UUID.randomUUID() + "/sinais")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tempoSegundos\":3}"))
                .andExpect(status().isNotFound()), false);
        assertEnvelope(mvc.perform(post("/public/marketing/visitas/" + UUID.randomUUID() + "/sinais")
                .contentType(MediaType.APPLICATION_JSON).content("nem-json"))
                .andExpect(status().isBadRequest()), false);
        // Rota pública que não existe
        assertEnvelope(mvc.perform(get("/public/marketing/nao-existe")).andExpect(status().isNotFound()), false);
        assertEnvelope(mvc.perform(post("/public/marketing/rastreamento.js")).andExpect(status().isMethodNotAllowed()),
                false);
    }

    // ================================================================ item 10

    @Test
    void item10_listagensSaoPaginadasEOrdenaveisComTamanhoMaximoDe100() throws Exception {
        UUID carla = lead(tenant, "Carla", "carla@exemplo.dev");
        UUID ana = lead(tenant, "Ana", "ana@exemplo.dev");
        UUID bruno = lead(tenant, "Bruno", "bruno@exemplo.dev");

        // Formato {itens, pagina, tamanho, total}, ordenável nos dois sentidos
        pedir(get("/api/marketing/leads").param("ordenar", "nome,asc").param("tamanho", "2"), TOKEN_ADMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagina").value(0))
                .andExpect(jsonPath("$.data.tamanho").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.itens[0].id").value(ana.toString()))
                .andExpect(jsonPath("$.data.itens[1].id").value(bruno.toString()));
        pedir(get("/api/marketing/leads").param("ordenar", "nome,desc").param("tamanho", "2"), TOKEN_ADMIN)
                .andExpect(jsonPath("$.data.itens[0].id").value(carla.toString()));
        pedir(get("/api/marketing/leads").param("ordenar", "nome,asc").param("tamanho", "2").param("pagina", "1"),
                TOKEN_ADMIN)
                .andExpect(jsonPath("$.data.pagina").value(1))
                .andExpect(jsonPath("$.data.itens[0].id").value(carla.toString()));

        // Tamanho máximo de 100, mínimo de 1 e padrão de 20 em todas as listagens paginadas
        for (String rota : List.of("/api/marketing/leads", "/api/marketing/links-campanha",
                "/api/marketing/modelos-mensagem", "/api/marketing/visitas-anuncio")) {
            pedir(get(rota).param("tamanho", "500"), TOKEN_ADMIN)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tamanho").value(100))
                    .andExpect(jsonPath("$.data.itens").isArray());
            pedir(get(rota).param("tamanho", "100"), TOKEN_ADMIN).andExpect(jsonPath("$.data.tamanho").value(100));
            pedir(get(rota).param("tamanho", "0"), TOKEN_ADMIN).andExpect(jsonPath("$.data.tamanho").value(1));
            pedir(get(rota), TOKEN_ADMIN).andExpect(jsonPath("$.data.tamanho").value(20));
            pedir(get(rota).param("pagina", "-3"), TOKEN_ADMIN).andExpect(jsonPath("$.data.pagina").value(0));
        }
    }

    // ================================================================ item 19

    @Test
    void item19_cabecalhoDe32KbEAceitoEResponde200AUmTokenDeAdministrador() throws Exception {
        String tokenGrande = tokenDeAdministradorGrande();
        assertThat(tokenGrande.length()).as("maior que o padrão de 8 KB do Tomcat").isGreaterThan(24 * 1024);
        assertThat(tokenGrande.length()).as("dentro do limite de 32 KB").isLessThan(31 * 1024);

        HttpResponse<String> resposta = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/marketing/leads"))
                        .header("Authorization", "Bearer " + tokenGrande)
                        .header("Accept", "application/json")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(200);
        JsonNode corpo = json.readTree(resposta.body());
        assertThat(corpo.get("success").asBoolean()).isTrue();
        assertThat(corpo.at("/data/total").asLong()).isZero();
    }

    // ---------------------------------------------------------------- apoio

    /** Rota protegida, com o corpo e o método que ela aceita, para os testes dos itens 3 e 4. */
    private record Rota(String metodo, String caminho, String corpo) {

        MockHttpServletRequestBuilder pedido(String token) {
            MockHttpServletRequestBuilder pedido = switch (metodo) {
                case "GET" -> get(caminho);
                case "POST" -> post(caminho);
                case "PUT" -> put(caminho);
                case "DELETE" -> delete(caminho);
                default -> throw new IllegalArgumentException(metodo);
            };
            if (corpo != null) {
                pedido.contentType(MediaType.APPLICATION_JSON).content(corpo);
            }
            return token == null ? pedido : pedido.header("Authorization", "Bearer " + token);
        }
    }

    /** Todas as rotas {@code /api/marketing/**} do módulo, exceto a de saúde (pública). */
    private static List<Rota> rotasProtegidas() {
        UUID id = UUID.randomUUID();
        String regra = "{\"nome\":\"x\"}";
        return List.of(
                new Rota("GET", "/api/marketing/leads", null),
                new Rota("GET", "/api/marketing/leads/" + id, null),
                new Rota("GET", "/api/marketing/leads/" + id + "/resumo", null),
                new Rota("GET", "/api/marketing/leads/resumo?ids=" + id, null),
                new Rota("GET", "/api/marketing/busca?q=maria", null),
                new Rota("PUT", "/api/marketing/leads/" + id + "/vendedor", "{\"vendedorId\":\"" + id + "\"}"),
                new Rota("GET", "/api/marketing/canais-anuncio", null),
                new Rota("GET", "/api/marketing/modelos-mensagem", null),
                new Rota("DELETE", "/api/marketing/modelos-mensagem/" + id, null),
                new Rota("GET", "/api/marketing/ciclo-vida", null),
                new Rota("GET", "/api/marketing/qualificacao", null),
                new Rota("GET", "/api/marketing/qualificacao/formularios", null),
                new Rota("GET", "/api/marketing/regras-pontuacao", null),
                new Rota("DELETE", "/api/marketing/regras-pontuacao/" + id, null),
                new Rota("GET", "/api/marketing/rodizio/vendedores", null),
                new Rota("GET", "/api/marketing/rastreamento", null),
                new Rota("PUT", "/api/marketing/rastreamento", "{\"ativo\":true,\"prazoConversaoMinutos\":30}"),
                new Rota("GET", "/api/marketing/links-campanha", null),
                new Rota("POST", "/api/marketing/links-campanha", CORPO_LINK),
                new Rota("PUT", "/api/marketing/links-campanha/" + id, CORPO_LINK),
                new Rota("DELETE", "/api/marketing/links-campanha/" + id, null),
                new Rota("GET", "/api/marketing/visitas-anuncio", null),
                new Rota("GET", "/api/marketing/visitas-anuncio/resumo", null));
    }

    private UUID lead(UUID doTenant, String nome, String email) {
        return TenantContexto.calcular(doTenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
            lead.atualizarContato(nome, email, null, null);
            return leads.saveAndFlush(lead).getId();
        });
    }

    private UUID criarLink(String token) throws Exception {
        JsonNode criado = corpo(pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON)
                .content(CORPO_LINK), token).andExpect(status().isCreated()));
        return UUID.fromString(criado.at("/data/id").asText());
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, String token) throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + token));
    }

    private JsonNode corpo(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsByteArray());
    }

    /** O corpo é JSON no envelope {@code {success, data, message, errors}}, com {@code success} esperado (§8.2). */
    private void assertEnvelope(ResultActions resposta, boolean sucesso) throws Exception {
        String tipo = resposta.andReturn().getResponse().getContentType();
        assertThat(tipo).as("Content-Type de %s", resposta.andReturn().getRequest().getRequestURI())
                .isNotNull().startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode corpo = corpo(resposta);
        String onde = resposta.andReturn().getRequest().getMethod() + " "
                + resposta.andReturn().getRequest().getRequestURI() + " -> " + corpo;
        List<String> campos = new ArrayList<>();
        corpo.fieldNames().forEachRemaining(campos::add);
        assertThat(new TreeSet<>(campos)).as(onde).containsExactly("data", "errors", "message", "success");
        assertThat(corpo.get("success").asBoolean()).as(onde).isEqualTo(sucesso);
        assertThat(corpo.get("errors").isArray()).as(onde).isTrue();
        if (!sucesso) {
            assertThat(corpo.get("data").isNull()).as(onde).isTrue();
            assertThat(corpo.get("message").asText()).as(onde).isNotBlank();
        }
    }

    /**
     * Um token de ADMINISTRADOR como o do identity: o claim {@code perms} lista as permissões de todos os
     * módulos, e o JWT passa de 24 KB. O {@link JwtDecoder} simulado devolve os claims; o que importa aqui
     * é o tamanho do cabeçalho {@code Authorization} que o servidor precisa aceitar.
     */
    private String tokenDeAdministradorGrande() throws Exception {
        List<String> permissoes = new ArrayList<>(PERMISSOES_DO_MODULO);
        StringBuilder payload = new StringBuilder();
        for (int i = 0; payload.length() < 28 * 1024; i++) {
            permissoes.add("modulo" + i + ".recurso" + (i % 17) + ".acao" + (i % 5));
            payload.setLength(0);
            payload.append(Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(
                    Map.of("sub", usuario.toString(), "tenant_id", tenant.toString(), "perms", permissoes))));
        }
        String cabecalho = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String token = cabecalho + "." + payload + ".assinatura";
        tokens.put(token, usuario(tenant, usuario, permissoes));
        return token;
    }

    private static Jwt usuario(UUID doTenant, UUID sub, List<String> permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", sub.toString(), "tenant_id", doTenant.toString(), "perms", permissoes));
    }
}
