package br.com.centinela.marketing.painel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;

/**
 * Fluxo SSE do painel (A4 e R6): os eventos mapeados da timeline do tenant, o {@code Last-Event-ID} na
 * reconexão sem repetir, o bloco do lead 0, o limite de conexões e as permissões. O {@code MockMvc}
 * deixa o pedido assíncrono aberto, e o texto que o servidor envia vai se acumulando na resposta.
 */
@AutoConfigureMockMvc
class PainelEventosTest extends IntegracaoDeTeste {

    private static final String TOKEN_PAINEL = "token-painel";
    private static final String TOKEN_SEM_PERMISSAO = "token-sem-permissao";
    private static final String TOKEN_OUTRO_TENANT = "token-outro-tenant";
    private static final Duration ESPERA = Duration.ofSeconds(15);

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FluxoDoPainel fluxo;

    @MockitoBean
    JwtDecoder decoder;

    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

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

    @AfterEach
    void fecharFluxos() {
        fluxo.encerrarTodas();
    }

    @Test
    void mandaOsFatosDoTenantEReenviaSoOQueFaltouNaReconexao() throws Exception {
        UUID lead = leadDe(tenant);
        UUID doOutro = leadDe(outroTenant);
        UUID vendedor = UUID.randomUUID();

        MvcResult primeira = abrir(TOKEN_PAINEL, null);
        // Um fato de outro tenant e um tipo que o painel não usa nunca chegam
        evento(outroTenant, doOutro, "etapa_alterada", null, 0, "{}");
        evento(tenant, lead, "comunicacao_enviada", null, null, "{}");
        evento(tenant, lead, "etapa_alterada", null, 0, "{}");
        evento(tenant, lead, "etapa_alterada", 0, 1, "{}");
        evento(tenant, lead, "qualificacao_alterada", null, null,
                "{\"faixaAnterior\":null,\"faixaNova\":\"mql_quente\",\"pontuacaoAnterior\":null,\"pontuacao\":85}");
        evento(tenant, lead, "vendedor_atribuido", null, null,
                "{\"vendedorId\":\"" + vendedor + "\",\"forma\":\"rodizio\"}");

        List<Mensagem> mensagens = esperarMensagens(primeira, 4);
        assertThat(mensagens).extracting(Mensagem::evento).containsExactly(
                "lead_criado", "lead_etapa_alterada", "lead_qualificado", "lead_atribuido");
        assertThat(mensagens).extracting(Mensagem::id).isSorted().doesNotHaveDuplicates();

        assertThat(mensagens.get(0).dados().get("leadId").asText()).isEqualTo(lead.toString());
        assertThat(mensagens.get(0).dados().get("etapa").asInt()).isZero();
        assertThat(mensagens.get(0).dados().get("origem").asText()).isEqualTo("meta_ads");
        assertThat(mensagens.get(1).dados().get("etapaAnterior").asInt()).isZero();
        assertThat(mensagens.get(1).dados().get("etapaNova").asInt()).isEqualTo(1);
        assertThat(mensagens.get(2).dados().get("faixa").asText()).isEqualTo("mql_quente");
        assertThat(mensagens.get(2).dados().get("pontuacao").asInt()).isEqualTo(85);
        assertThat(mensagens.get(3).dados().get("vendedorId").asText()).isEqualTo(vendedor.toString());
        // Só ids e números: nenhum dado pessoal nas mensagens
        assertThat(primeira.getResponse().getContentAsString()).doesNotContain("Fulano", "@");
        assertThat(primeira.getResponse().getContentType()).startsWith("text/event-stream");

        // Reconecta depois da segunda mensagem: recebe só a terceira e a quarta
        long ultimoVisto = mensagens.get(1).id();
        evento(tenant, lead, "etapa_alterada", 1, 3, "{}");
        MvcResult segunda = abrir(TOKEN_PAINEL, String.valueOf(ultimoVisto));
        List<Mensagem> reenviadas = esperarMensagens(segunda, 3);
        assertThat(reenviadas).extracting(Mensagem::evento)
                .containsExactly("lead_qualificado", "lead_atribuido", "lead_etapa_alterada");
        assertThat(reenviadas).extracting(Mensagem::id).allMatch(id -> id > ultimoVisto);
        assertThat(reenviadas.get(2).dados().get("etapaNova").asInt()).isEqualTo(3);

        // Reconecta de novo, já no fim: não vem nada repetido
        long ultimo = reenviadas.get(2).id();
        MvcResult terceira = abrir(TOKEN_PAINEL, String.valueOf(ultimo));
        evento(tenant, lead, "vendedor_atribuido", null, null, "{\"vendedorId\":\"" + vendedor + "\"}");
        List<Mensagem> novas = esperarMensagens(terceira, 1);
        assertThat(novas).extracting(Mensagem::evento).containsExactly("lead_atribuido");
        assertThat(novas.get(0).id()).isGreaterThan(ultimo);
    }

    @Test
    void jaNaAberturaMandaUmComentarioParaOGatewayNaoDarTimeout() throws Exception {
        MvcResult aberto = abrir(TOKEN_PAINEL, null);

        // Sem esperar o ping: o primeiro byte sai com a abertura
        assertThat(aberto.getResponse().getContentAsString()).startsWith(": conectado");
        assertThat(aberto.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(mensagens(aberto)).isEmpty();
    }

    @Test
    void semLastEventIdComecaDoFatoMaisRecenteSemRepetirOPassado() throws Exception {
        UUID lead = leadDe(tenant);
        evento(tenant, lead, "etapa_alterada", null, 0, "{}");

        MvcResult aberto = abrir(TOKEN_PAINEL, null);
        evento(tenant, lead, "qualificacao_alterada", null, null,
                "{\"faixaNova\":\"nutricao\",\"pontuacao\":10}");

        List<Mensagem> mensagens = esperarMensagens(aberto, 1);
        assertThat(mensagens).extracting(Mensagem::evento).containsExactly("lead_qualificado");
    }

    @Test
    void avisaOBlocoDoLeadZeroQuandoUmaVisitaMuda() throws Exception {
        UUID link = link(tenant);
        MvcResult aberto = abrir(TOKEN_PAINEL, null);

        visita(tenant, link, "ativa");

        Awaitility.await().atMost(ESPERA).untilAsserted(() ->
                assertThat(aberto.getResponse().getContentAsString()).contains("event:lead_zero_atualizado"));
        Mensagem bloco = mensagens(aberto).stream().filter(m -> m.evento().equals("lead_zero_atualizado"))
                .findFirst().orElseThrow();
        // O bloco não vem de lead_eventos: sem id
        assertThat(bloco.id()).isNull();
        assertThat(bloco.dados().get("cliques").asInt()).isEqualTo(1);
        assertThat(bloco.dados().get("ativas").asInt()).isEqualTo(1);
        assertThat(bloco.dados().get("porDispositivo").get(0).get("tipoDispositivo").asText()).isEqualTo("smartphone");
    }

    @Test
    void passaOLimiteDeConexoesDoTenantResponde429EOutroTenantSegue() throws Exception {
        int limite = 20;
        for (int i = 0; i < limite; i++) {
            abrir(TOKEN_PAINEL, null);
        }
        assertThat(fluxo.conexoesAbertas(tenant)).isEqualTo(limite);

        mvc.perform(get("/api/marketing/painel/eventos").accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + TOKEN_PAINEL))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("limite")));
        assertThat(fluxo.conexoesAbertas(tenant)).isEqualTo(limite);

        // Outro tenant tem o próprio limite
        abrir(TOKEN_OUTRO_TENANT, null);
        assertThat(fluxo.conexoesAbertas(outroTenant)).isEqualTo(1);

        // Fechadas as conexões, o tenant volta a poder abrir
        fluxo.encerrarTodas();
        assertThat(fluxo.conexoesAbertas(tenant)).isZero();
        abrir(TOKEN_PAINEL, null);
    }

    @Test
    void exigeTokenEPermissaoEResponde400ComFiltroInvalidoSempreEmJson() throws Exception {
        mvc.perform(get("/api/marketing/painel/eventos").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/marketing/painel/eventos").accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + TOKEN_SEM_PERMISSAO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/marketing/painel/eventos?periodo=ontem").accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + TOKEN_PAINEL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("periodo"));
        assertThat(fluxo.conexoesAbertas(tenant)).isZero();
    }

    // ---- apoio ----

    private MvcResult abrir(String token, String ultimoEventoId) throws Exception {
        var requisicao = get("/api/marketing/painel/eventos").accept(MediaType.TEXT_EVENT_STREAM)
                .header("Authorization", "Bearer " + token);
        if (ultimoEventoId != null) {
            requisicao.header("Last-Event-ID", ultimoEventoId);
        }
        return mvc.perform(requisicao).andExpect(request().asyncStarted()).andReturn();
    }

    private List<Mensagem> esperarMensagens(MvcResult fluxoAberto, int quantidade) {
        Awaitility.await().atMost(ESPERA).untilAsserted(() ->
                assertThat(mensagens(fluxoAberto).stream().filter(m -> m.id() != null)).hasSizeGreaterThanOrEqualTo(quantidade));
        // Espera mais um ciclo de consulta, para um repetido ou um intruso aparecer
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
        }
        List<Mensagem> mensagens = mensagens(fluxoAberto).stream().filter(m -> m.id() != null).toList();
        assertThat(mensagens).hasSize(quantidade);
        return mensagens;
    }

    /** Mensagens (com {@code event:}) já recebidas; comentários como {@code : ping} ficam de fora. */
    private List<Mensagem> mensagens(MvcResult fluxoAberto) {
        try {
            String texto = fluxoAberto.getResponse().getContentAsString();
            List<Mensagem> mensagens = new ArrayList<>();
            for (String bloco : texto.split("\n\n")) {
                Long id = null;
                String evento = null;
                String dados = null;
                for (String linha : bloco.split("\n")) {
                    if (linha.startsWith("id:")) {
                        id = Long.valueOf(linha.substring(3).strip());
                    } else if (linha.startsWith("event:")) {
                        evento = linha.substring(6).strip();
                    } else if (linha.startsWith("data:")) {
                        dados = linha.substring(5);
                    }
                }
                if (evento != null && dados != null) {
                    mensagens.add(new Mensagem(id, evento, json.readTree(dados)));
                }
            }
            return mensagens;
        } catch (Exception excecao) {
            throw new IllegalStateException(excecao);
        }
    }

    private record Mensagem(Long id, String evento, JsonNode dados) {
    }

    private Jwt jwt(UUID tenantDoToken, String... permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", usuario.toString(), "tenant_id", tenantDoToken.toString(), "perms", List.of(permissoes)));
    }

    private UUID leadDe(UUID tenantDoLead) {
        UUID id = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO marketing.leads (id, tenant_id, etapa, origem, nome, token_descadastro, "
                + "created_at, updated_at) VALUES (?, ?, 0, 'meta_ads', 'Fulano', ?, ?, ?)",
                id, tenantDoLead, id.toString().replace("-", ""), agora, agora);
        return id;
    }

    private void evento(UUID tenantDoEvento, UUID lead, String tipo, Integer etapaAnterior, Integer etapaNova,
            String payload) {
        jdbc.update("INSERT INTO marketing.lead_eventos (id, tenant_id, lead_id, tipo_evento, etapa_anterior, "
                + "etapa_nova, payload, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())",
                UUID.randomUUID(), tenantDoEvento, lead, tipo, etapaAnterior, etapaNova, payload);
    }

    private UUID link(UUID tenantDoLink) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO marketing.links_campanha (id, tenant_id, codigo, nome, url_destino, ativo, "
                + "created_at, updated_at) VALUES (?, ?, ?, 'Link', 'https://exemplo.dev/lp', true, now(), now())",
                id, tenantDoLink, id.toString().replace("-", "").substring(0, 12));
        return id;
    }

    private void visita(UUID tenantDaVisita, UUID link, String situacao) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO marketing.visitas_anuncio (id, tenant_id, link_id, situacao, clicado_em, expira_em, "
                + "contexto_tecnico, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())",
                UUID.randomUUID(), tenantDaVisita, link, situacao, agora, agora.plusMinutes(30),
                "{\"tipoDispositivo\":\"smartphone\"}");
    }
}
