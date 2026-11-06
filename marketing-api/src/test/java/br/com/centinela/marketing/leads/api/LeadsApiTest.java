package br.com.centinela.marketing.leads.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

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
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * API de leads (A2) contra o PostgreSQL do Testcontainers, pelo filtro de segurança real com o
 * {@link JwtDecoder} simulado: lista, filtros e paginação, detalhe, resumo sem dado pessoal, busca
 * global, isolamento por tenant, permissões e token de serviço com {@code X-Tenant-Id}.
 */
@AutoConfigureMockMvc
class LeadsApiTest extends IntegracaoDeTeste {

    private static final String TOKEN_MARKETING = "token-marketing";
    private static final String TOKEN_SO_ACESSO = "token-so-acesso";
    private static final String TOKEN_SO_RESUMO = "token-so-resumo";
    private static final String TOKEN_OUTRO_TENANT = "token-outro-tenant";
    private static final String TOKEN_SERVICO = "token-servico-crm";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    LeadConsentimentoRepository consentimentos;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JwtDecoder decoder;

    // Tenants novos a cada teste: o banco é o mesmo para a classe toda.
    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();
    private final UUID usuario = UUID.randomUUID();

    @BeforeEach
    void tokens() {
        Map<String, Jwt> tokens = new HashMap<>();
        tokens.put(TOKEN_MARKETING, usuario(tenant, "marketing.acessar", "marketing.lead.ver", "marketing.lead.ver_resumo"));
        tokens.put(TOKEN_SO_ACESSO, usuario(tenant, "marketing.acessar", "marketing.dashboard.ver"));
        tokens.put(TOKEN_SO_RESUMO, usuario(tenant, "marketing.lead.ver_resumo"));
        tokens.put(TOKEN_OUTRO_TENANT, usuario(outroTenant, "marketing.lead.ver", "marketing.lead.ver_resumo"));
        tokens.put(TOKEN_SERVICO, jwt(Map.of("sub", "svc:crm", "perms", List.of("marketing.lead.ver_resumo"))));
        when(decoder.decode(anyString())).thenAnswer(chamada -> {
            Jwt token = tokens.get(chamada.<String>getArgument(0));
            if (token == null) {
                throw new BadJwtException("token desconhecido");
            }
            return token;
        });
    }

    // ---------------------------------------------------------------- lista

    @Test
    void listaPaginadaNoEnvelopeEOrdenavel() throws Exception {
        UUID carla = lead(OrigemLead.LANDING_PAGE, 1, "Carla", "carla@exemplo.dev", l -> { });
        UUID ana = lead(OrigemLead.LANDING_PAGE, 3, "Ana", "ana@exemplo.dev", l -> { });
        UUID bruno = lead(OrigemLead.META_ADS, 0, "Bruno", "bruno@exemplo.dev", l -> { });

        pedir(get("/api/marketing/leads").param("tamanho", "2").param("ordenar", "nome,asc"), TOKEN_MARKETING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(nullValue()))
                .andExpect(jsonPath("$.errors", empty()))
                .andExpect(jsonPath("$.data.pagina").value(0))
                .andExpect(jsonPath("$.data.tamanho").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.itens[*].id", contains(ana.toString(), bruno.toString())));

        pedir(get("/api/marketing/leads").param("tamanho", "2").param("pagina", "1").param("ordenar", "nome,asc"),
                TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.pagina").value(1))
                .andExpect(jsonPath("$.data.itens[*].id", contains(carla.toString())));

        // Padrão: mais recentes primeiro
        pedir(get("/api/marketing/leads"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.tamanho").value(20))
                .andExpect(jsonPath("$.data.itens[*].id", contains(bruno.toString(), ana.toString(), carla.toString())));

        pedir(get("/api/marketing/leads").param("tamanho", "500"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.tamanho").value(100));
    }

    @Test
    void itemDaListaTrazAsColunasDoNorteador() throws Exception {
        UUID vendedor = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        UUID id = lead(OrigemLead.LANDING_PAGE, 3, "Maria Souza", "maria@exemplo.dev", l -> {
            l.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.EMBED);
            l.vincularOportunidade(oportunidade);
            l.registrarPontuacao(85, FaixaQualificacao.MQL_QUENTE, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        });
        sql("UPDATE marketing.leads SET utm_campaign = 'black-friday', vendedor_id = ? WHERE id = ?", vendedor, id);

        pedir(get("/api/marketing/leads"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.itens[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data.itens[0].nome").value("Maria Souza"))
                .andExpect(jsonPath("$.data.itens[0].email").value("maria@exemplo.dev"))
                .andExpect(jsonPath("$.data.itens[0].etapa").value(3))
                .andExpect(jsonPath("$.data.itens[0].temperatura").value("quente"))
                .andExpect(jsonPath("$.data.itens[0].faixaQualificacao").value("mql_quente"))
                .andExpect(jsonPath("$.data.itens[0].pontuacao").value(85))
                .andExpect(jsonPath("$.data.itens[0].origem").value("landing_page"))
                .andExpect(jsonPath("$.data.itens[0].canal").value("embed"))
                .andExpect(jsonPath("$.data.itens[0].utmCampaign").value("black-friday"))
                .andExpect(jsonPath("$.data.itens[0].vendedorId").value(vendedor.toString()))
                .andExpect(jsonPath("$.data.itens[0].crmOportunidadeId").value(oportunidade.toString()))
                .andExpect(jsonPath("$.data.itens[0].anonimizado").value(false))
                .andExpect(jsonPath("$.data.itens[0].criadoEm").isNotEmpty());
    }

    @Test
    void filtrosDaLista() throws Exception {
        UUID vendedor = UUID.randomUUID();
        UUID maria = lead(OrigemLead.LANDING_PAGE, 3, "Maria Souza", "maria@exemplo.dev", l -> {
            l.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.CHATBOT);
            l.registrarPontuacao(85, FaixaQualificacao.MQL_QUENTE, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        });
        sql("UPDATE marketing.leads SET utm_campaign = 'Black-Friday', vendedor_id = ? WHERE id = ?", vendedor, maria);
        UUID joao = lead(OrigemLead.META_ADS, 0, "João Lima", "joao@empresa.dev", l -> l.registrarPontuacao(
                50, FaixaQualificacao.MQL_MORNO, List.of(), OffsetDateTime.now(ZoneOffset.UTC)));
        OffsetDateTime antigo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(40);
        sql("UPDATE marketing.leads SET created_at = ? WHERE id = ?", antigo, joao);
        UUID pedro = lead(OrigemLead.LANDING_PAGE, 1, "Pedro", "pedro_50%@exemplo.dev", l -> { });
        sql("UPDATE marketing.leads SET canal = 'formulario' WHERE id = ?", pedro);

        assertIds(listar("etapa", "3"), maria);
        assertIds(listar("etapa", "0,1"), joao, pedro);
        assertIds(listar("faixa", "mql_quente"), maria);
        assertIds(listar("faixa", "mql_quente,mql_morno"), maria, joao);
        assertIds(listar("origem", "meta_ads"), joao);
        assertIds(listar("canal", "chatbot"), maria);
        assertIds(listar("canal", "formulario,chatbot"), maria, pedro);
        assertIds(listar("campanha", "black-friday"), maria);
        assertIds(listar("vendedorId", vendedor.toString()), maria);
        assertIds(listar("periodo", "30d"), maria, pedro);
        assertIds(listar("periodo", "90d"), maria, joao, pedro);
        String dia = LocalDate.ofInstant(antigo.toInstant(), ZoneOffset.UTC).toString();
        assertIds(listar("periodo", dia + "," + dia), joao);
        assertIds(listar("q", "souza"), maria);
        assertIds(listar("q", "EMPRESA.dev"), joao);
        assertIds(listar("q", "50%"), pedro);
        assertIds(listar("q", "_"), pedro);

        pedir(get("/api/marketing/leads").param("origem", "landing_page").param("etapa", "1"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].id").value(pedro.toString()));
    }

    @Test
    void filtroInvalidoResponde400ComOsCampos() throws Exception {
        pedir(get("/api/marketing/leads").param("etapa", "5").param("canal", "whatsapp")
                .param("vendedorId", "x").param("periodo", "ontem"), TOKEN_MARKETING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.errors[*].campo", containsInAnyOrder("etapa", "canal", "vendedorId", "periodo")))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_INVALIDO"));
    }

    @Test
    void leadExcluidoNaoApareceEAnonimizadoSaiSemDadoPessoal() throws Exception {
        UUID excluido = lead(OrigemLead.LANDING_PAGE, 1, "Excluído", "excluido@exemplo.dev", l -> { });
        sql("UPDATE marketing.leads SET deleted_at = now() WHERE id = ?", excluido);
        UUID anonimizado = lead(OrigemLead.LANDING_PAGE, 3, "Anônimo", "anonimo@exemplo.dev", l -> { });
        // A coluna nome ficou preenchida de propósito: a API esconde mesmo assim
        sql("UPDATE marketing.leads SET anonimizado = true, email = null WHERE id = ?", anonimizado);

        pedir(get("/api/marketing/leads"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].id").value(anonimizado.toString()))
                .andExpect(jsonPath("$.data.itens[0].anonimizado").value(true))
                .andExpect(jsonPath("$.data.itens[0].nome").value(nullValue()))
                .andExpect(jsonPath("$.data.itens[0].email").value(nullValue()))
                .andExpect(jsonPath("$.data.itens[0].etapa").value(3));
        assertIds(listar("q", "anôn"));

        pedir(get("/api/marketing/leads/" + excluido), TOKEN_MARKETING).andExpect(status().isNotFound());
        pedir(get("/api/marketing/leads/" + excluido + "/resumo"), TOKEN_MARKETING).andExpect(status().isNotFound());
        pedir(get("/api/marketing/leads/" + anonimizado), TOKEN_MARKETING)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contato.nome").value(nullValue()))
                .andExpect(jsonPath("$.data.contato.email").value(nullValue()));
        pedir(get("/api/marketing/busca").param("q", "anôn"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data", empty()));
    }

    // ---------------------------------------------------------------- detalhe

    @Test
    void detalheTrazAsSecoesEAEvidenciaDoConsentimento() throws Exception {
        UUID envio = UUID.randomUUID();
        UUID formulario = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        OffsetDateTime aceitoEm = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        UUID id = lead(OrigemLead.LANDING_PAGE, 3, "Maria Souza", "maria@exemplo.dev", l -> {
            l.atualizarContato(null, null, "5562999990000", null);
            l.vincularEnvio(envio, formulario, CanalLead.LANDING_PAGE);
            l.registrarRespostas(List.of(Map.of("campoId", "c1", "rotulo", "Cargo", "tipo", "LISTA_OPCOES",
                    "valor", "Decisor")), false, aceitoEm.plusMinutes(1));
            l.vincularCrm(empresa, contato);
            l.vincularOportunidade(oportunidade);
            l.registrarConsentimento(true, aceitoEm);
            l.registrarPontuacao(30, FaixaQualificacao.NUTRICAO, List.of(Map.of("regraId", "r1", "criterio", "Cargo",
                    "fonte", "campo_formulario", "pontos", 30, "campoId", "c1")), aceitoEm.plusMinutes(2));
        });
        TenantContexto.executar(tenant, () -> {
            consentimentos.save(LeadConsentimento.doLanding(id, OrigemConsentimento.FORMULARIO_RECEBIDO, true,
                    aceitoEm, "Aceito receber contato.", formulario, envio, null, UUID.randomUUID()));
            consentimentos.save(LeadConsentimento.revogacaoPorDescadastro(id, aceitoEm.plusDays(1),
                    "203.0.113.7", "Mozilla/5.0"));
            eventos.save(LeadEvento.etapaAlterada(id, 1, 3, Map.of("motivo", "landing.formulario.recebido")));
            eventos.save(new LeadEvento(id, TipoEventoLead.HANDOFF_CRM, Map.of("oportunidadeId", oportunidade.toString())));
        });

        JsonNode dados = corpo(pedir(get("/api/marketing/leads/" + id), TOKEN_MARKETING)
                .andExpect(status().isOk())).get("data");

        assertThat(dados.get("id").asText()).isEqualTo(id.toString());
        assertThat(dados.get("etapa").asInt()).isEqualTo(3);
        assertThat(dados.get("temperatura").asText()).isEqualTo("quente");
        assertThat(dados.at("/contato/nome").asText()).isEqualTo("Maria Souza");
        assertThat(dados.at("/contato/telefone").asText()).isEqualTo("5562999990000");
        assertThat(dados.at("/origem/origem").asText()).isEqualTo("landing_page");
        assertThat(dados.at("/origem/canal").asText()).isEqualTo("landing_page");
        assertThat(dados.at("/respostas/situacao").asText()).isEqualTo("lidas");
        assertThat(dados.at("/respostas/formularioId").asText()).isEqualTo(formulario.toString());
        assertThat(dados.at("/respostas/itens/0/rotulo").asText()).isEqualTo("Cargo");
        assertThat(dados.at("/respostas/itens/0/valor").asText()).isEqualTo("Decisor");
        assertThat(dados.at("/qualificacao/pontuacao").asInt()).isEqualTo(30);
        assertThat(dados.at("/qualificacao/faixaQualificacao").asText()).isEqualTo("nutricao");
        assertThat(dados.at("/qualificacao/detalhe/0/criterio").asText()).isEqualTo("Cargo");
        assertThat(dados.at("/qualificacao/detalhe/0/pontos").asInt()).isEqualTo(30);
        assertThat(dados.at("/consentimento/marketing").asBoolean()).isTrue();
        assertThat(dados.at("/consentimento/historico")).hasSize(2);
        JsonNode revogacao = dados.at("/consentimento/historico/0");
        assertThat(revogacao.get("aceito").asBoolean()).isFalse();
        assertThat(revogacao.get("origem").asText()).isEqualTo("descadastro");
        assertThat(revogacao.get("ip").asText()).isEqualTo("203.0.113.7");
        assertThat(revogacao.get("userAgent").asText()).isEqualTo("Mozilla/5.0");
        JsonNode aceite = dados.at("/consentimento/historico/1");
        assertThat(aceite.get("origem").asText()).isEqualTo("landing.formulario.recebido");
        assertThat(aceite.get("textoTermo").asText()).isEqualTo("Aceito receber contato.");
        assertThat(aceite.get("envioId").asText()).isEqualTo(envio.toString());
        assertThat(dados.at("/aquecimento/status").asText()).isEqualTo("ativo");
        assertThat(dados.at("/crm/empresaId").asText()).isEqualTo(empresa.toString());
        assertThat(dados.at("/crm/contatoId").asText()).isEqualTo(contato.toString());
        assertThat(dados.at("/crm/oportunidadeId").asText()).isEqualTo(oportunidade.toString());
        assertThat(dados.get("timeline")).hasSize(2);
        assertThat(dados.at("/timeline/0/tipo").asText()).isEqualTo("etapa_alterada");
        assertThat(dados.at("/timeline/0/etapaAnterior").asInt()).isEqualTo(1);
        assertThat(dados.at("/timeline/0/etapaNova").asInt()).isEqualTo(3);
        assertThat(dados.at("/timeline/1/tipo").asText()).isEqualTo("handoff_crm");
        assertThat(dados.at("/timeline/1/sequencia").asLong())
                .isGreaterThan(dados.at("/timeline/0/sequencia").asLong());
        assertThat(dados.toString()).as("o token do descadastro nunca sai").doesNotContain(tokenDescadastro(id));
    }

    @Test
    void detalheMostraLeituraPendenteDoEnvio() throws Exception {
        UUID id = lead(OrigemLead.LANDING_PAGE, 3, null, "lead@exemplo.dev",
                l -> l.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.FORMULARIO));

        pedir(get("/api/marketing/leads/" + id), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.respostas.situacao").value("aguardando_landing"))
                .andExpect(jsonPath("$.data.respostas.itens", empty()))
                .andExpect(jsonPath("$.data.qualificacao.pontuacao").value(nullValue()));
    }

    @Test
    void leadDeOutroTenantResponde404EmTodasAsRotas() throws Exception {
        UUID meu = lead(OrigemLead.LANDING_PAGE, 1, "Maria", "maria@exemplo.dev", l -> { });

        pedir(get("/api/marketing/leads/" + meu), TOKEN_OUTRO_TENANT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
        pedir(get("/api/marketing/leads/" + meu + "/resumo"), TOKEN_OUTRO_TENANT).andExpect(status().isNotFound());
        pedir(get("/api/marketing/leads/resumo").param("ids", meu.toString()), TOKEN_OUTRO_TENANT)
                .andExpect(jsonPath("$.data", empty()));
        pedir(get("/api/marketing/leads"), TOKEN_OUTRO_TENANT).andExpect(jsonPath("$.data.total").value(0));
        pedir(get("/api/marketing/busca").param("q", "maria"), TOKEN_OUTRO_TENANT)
                .andExpect(jsonPath("$.data", empty()));
        pedir(get("/api/marketing/leads/" + UUID.randomUUID()), TOKEN_MARKETING).andExpect(status().isNotFound());
        pedir(get("/api/marketing/leads/nao-e-uuid"), TOKEN_MARKETING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("id"));
    }

    @Test
    void tenantIdNaQueryENoCabecalhoDoUsuarioEhIgnorado() throws Exception {
        UUID meu = lead(OrigemLead.LANDING_PAGE, 1, "Meu", "meu@exemplo.dev", l -> { });
        UUID dele = TenantContexto.calcular(outroTenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
            lead.atualizarContato("Dele", "dele@exemplo.dev", null, null);
            return leads.save(lead).getId();
        });

        pedir(get("/api/marketing/leads").param("tenantId", outroTenant.toString())
                .header("X-Tenant-Id", outroTenant.toString()), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.itens[0].id").value(meu.toString()));
        pedir(get("/api/marketing/leads/" + dele).param("tenantId", outroTenant.toString())
                .header("X-Tenant-Id", outroTenant.toString()), TOKEN_MARKETING)
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- permissões

    @Test
    void semPermissaoResponde403ESemTokenResponde401() throws Exception {
        UUID id = lead(OrigemLead.LANDING_PAGE, 1, "Maria", "maria@exemplo.dev", l -> { });

        for (String rota : List.of("/api/marketing/leads", "/api/marketing/leads/" + id,
                "/api/marketing/leads/" + id + "/resumo", "/api/marketing/leads/resumo?ids=" + id,
                "/api/marketing/busca?q=maria")) {
            pedir(get(rota), TOKEN_SO_ACESSO)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors").isArray());
            mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        }

        // ver_resumo não dá acesso à lista, ao detalhe nem à busca
        pedir(get("/api/marketing/leads/" + id + "/resumo"), TOKEN_SO_RESUMO).andExpect(status().isOk());
        pedir(get("/api/marketing/leads/" + id), TOKEN_SO_RESUMO).andExpect(status().isForbidden());
        pedir(get("/api/marketing/leads"), TOKEN_SO_RESUMO).andExpect(status().isForbidden());
        pedir(get("/api/marketing/busca").param("q", "maria"), TOKEN_SO_RESUMO).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- resumo

    @Test
    void resumoNoFormatoDoContratoSemDadoPessoal() throws Exception {
        UUID vendedor = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        UUID id = lead(OrigemLead.LANDING_PAGE, 3, "Maria Souza", "maria@exemplo.dev", l -> {
            l.atualizarContato(null, null, "5562999990000", "5562999990000");
            l.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.LANDING_PAGE);
            l.registrarRespostas(List.of(Map.of("campoId", "c1", "rotulo", "Cargo", "tipo", "TEXTO",
                    "valor", "Diretora")), false, OffsetDateTime.now(ZoneOffset.UTC));
            l.vincularOportunidade(oportunidade);
            l.registrarPontuacao(85, FaixaQualificacao.MQL_QUENTE, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        });
        sql("UPDATE marketing.leads SET utm_campaign = 'black-friday-2026', vendedor_id = ? WHERE id = ?", vendedor, id);

        JsonNode corpo = corpo(pedir(get("/api/marketing/leads/" + id + "/resumo"), TOKEN_SO_RESUMO)
                .andExpect(status().isOk()));
        JsonNode dados = corpo.get("data");

        assertThat(corpo.get("success").asBoolean()).isTrue();
        assertThat(nomes(dados)).containsExactlyInAnyOrder("id", "etapa", "temperatura", "faixaQualificacao",
                "pontuacao", "origem", "utmCampaign", "vendedorId", "crmOportunidadeId");
        assertThat(dados.get("id").asText()).isEqualTo(id.toString());
        assertThat(dados.get("etapa").asInt()).isEqualTo(3);
        assertThat(dados.get("temperatura").asText()).isEqualTo("quente");
        assertThat(dados.get("faixaQualificacao").asText()).isEqualTo("mql_quente");
        assertThat(dados.get("pontuacao").asInt()).isEqualTo(85);
        assertThat(dados.get("origem").asText()).isEqualTo("landing_page");
        assertThat(dados.get("utmCampaign").asText()).isEqualTo("black-friday-2026");
        assertThat(dados.get("vendedorId").asText()).isEqualTo(vendedor.toString());
        assertThat(dados.get("crmOportunidadeId").asText()).isEqualTo(oportunidade.toString());
        assertThat(corpo.toString()).doesNotContain("Maria", "maria@exemplo.dev", "5562999990000", "Diretora");
    }

    @Test
    void resumoDeLeadSemQualificacaoTrazOsNulos() throws Exception {
        UUID id = lead(OrigemLead.META_ADS, 0, "Bruno", "bruno@exemplo.dev", l -> { });

        JsonNode dados = corpo(pedir(get("/api/marketing/leads/" + id + "/resumo"), TOKEN_MARKETING)).get("data");

        assertThat(nomes(dados)).hasSize(9);
        assertThat(dados.get("temperatura").asText()).isEqualTo("frio");
        assertThat(dados.get("origem").asText()).isEqualTo("meta_ads");
        assertThat(dados.get("faixaQualificacao").isNull()).isTrue();
        assertThat(dados.get("pontuacao").isNull()).isTrue();
        assertThat(dados.get("vendedorId").isNull()).isTrue();
    }

    @Test
    void resumoEmLoteNaOrdemPedidaSemOsDeOutroTenant() throws Exception {
        UUID a = lead(OrigemLead.LANDING_PAGE, 1, "A", "a@exemplo.dev", l -> { });
        UUID b = lead(OrigemLead.META_ADS, 0, "B", "b@exemplo.dev", l -> { });
        UUID dele = TenantContexto.calcular(outroTenant,
                () -> leads.save(new Lead(OrigemLead.LANDING_PAGE, 1)).getId());

        pedir(get("/api/marketing/leads/resumo").param("ids", b + "," + dele + "," + a + "," + b), TOKEN_SO_RESUMO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", contains(b.toString(), a.toString())))
                .andExpect(jsonPath("$.data[0].origem").value("meta_ads"))
                .andExpect(jsonPath("$.data[0].nome").doesNotExist());

        pedir(get("/api/marketing/leads/resumo").param("ids", a.toString()).param("ids", b.toString()),
                TOKEN_SO_RESUMO)
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void resumoEmLoteValidaOsIds() throws Exception {
        String cento = String.join(",", IntStream.range(0, 101).mapToObj(i -> UUID.randomUUID().toString()).toList());

        pedir(get("/api/marketing/leads/resumo").param("ids", cento), TOKEN_SO_RESUMO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("ids"));
        pedir(get("/api/marketing/leads/resumo").param("ids", "abc"), TOKEN_SO_RESUMO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_INVALIDO"));
        pedir(get("/api/marketing/leads/resumo").param("ids", ""), TOKEN_SO_RESUMO)
                .andExpect(status().isBadRequest());
        pedir(get("/api/marketing/leads/resumo"), TOKEN_SO_RESUMO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].campo").value("ids"));
    }

    @Test
    void tokenDeServicoLeOResumoNoTenantDoCabecalho() throws Exception {
        UUID id = lead(OrigemLead.LANDING_PAGE, 3, "Maria", "maria@exemplo.dev", l -> { });

        pedir(get("/api/marketing/leads/" + id + "/resumo").header("X-Tenant-Id", tenant.toString()), TOKEN_SERVICO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
        pedir(get("/api/marketing/leads/resumo").param("ids", id.toString())
                .header("X-Tenant-Id", tenant.toString()), TOKEN_SERVICO)
                .andExpect(jsonPath("$.data[*].id", contains(id.toString())));
        pedir(get("/api/marketing/leads/" + id + "/resumo").header("X-Tenant-Id", outroTenant.toString()),
                TOKEN_SERVICO)
                .andExpect(status().isNotFound());
        pedir(get("/api/marketing/leads/" + id + "/resumo"), TOKEN_SERVICO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
        pedir(get("/api/marketing/leads/" + id).header("X-Tenant-Id", tenant.toString()), TOKEN_SERVICO)
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- busca

    @Test
    void buscaGlobalNoFormatoDoContrato() throws Exception {
        UUID maria = lead(OrigemLead.LANDING_PAGE, 2, "Maria Souza", "maria@exemplo.dev", l -> { });
        UUID semNome = lead(OrigemLead.META_ADS, 0, null, "mariana@exemplo.dev", l -> { });
        lead(OrigemLead.LANDING_PAGE, 1, "Carlos", "carlos@exemplo.dev", l -> { });

        JsonNode corpo = corpo(pedir(get("/api/marketing/busca").param("q", "MARI"), TOKEN_MARKETING)
                .andExpect(status().isOk()));

        assertThat(corpo.get("success").asBoolean()).isTrue();
        assertThat(corpo.get("data")).hasSize(2);
        JsonNode primeiro = corpo.at("/data/0");
        assertThat(nomes(primeiro)).containsExactlyInAnyOrder("id", "titulo", "subtitulo", "rota");
        assertThat(primeiro.get("id").asText()).isEqualTo(semNome.toString());
        assertThat(primeiro.get("titulo").asText()).as("sem nome, vai o e-mail").isEqualTo("mariana@exemplo.dev");
        assertThat(primeiro.get("subtitulo").asText()).isEqualTo("Etapa 0 · meta_ads");
        assertThat(primeiro.get("rota").asText()).isEqualTo("/leads/" + semNome);
        JsonNode segundo = corpo.at("/data/1");
        assertThat(segundo.get("titulo").asText()).isEqualTo("Maria Souza");
        assertThat(segundo.get("subtitulo").asText()).isEqualTo("Etapa 2 · landing_page");
        assertThat(segundo.get("rota").asText()).isEqualTo("/leads/" + maria);
    }

    @Test
    void buscaDevolveNoMaximoCincoEExigeDoisCaracteres() throws Exception {
        for (int i = 0; i < 7; i++) {
            lead(OrigemLead.LANDING_PAGE, 1, "Cliente " + i, "cliente" + i + "@exemplo.dev", l -> { });
        }

        pedir(get("/api/marketing/busca").param("q", "cliente"), TOKEN_MARKETING)
                .andExpect(jsonPath("$.data", hasSize(5)));
        pedir(get("/api/marketing/busca").param("q", " c "), TOKEN_MARKETING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Informe pelo menos 2 caracteres para buscar."))
                .andExpect(jsonPath("$.errors[0].campo").value("q"))
                .andExpect(jsonPath("$.errors[0].codigo").value("CAMPO_INVALIDO"));
        pedir(get("/api/marketing/busca"), TOKEN_MARKETING)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("q"));
        pedir(get("/api/marketing/busca").param("q", "x".repeat(101)), TOKEN_MARKETING)
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- apoio

    private UUID lead(OrigemLead origem, int etapa, String nome, String email, Consumer<Lead> preparar) {
        return TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(origem, etapa);
            lead.atualizarContato(nome, email, null, null);
            preparar.accept(lead);
            return leads.saveAndFlush(lead).getId();
        });
    }

    private void sql(String comando, Object... argumentos) {
        jdbc.update(comando, argumentos);
    }

    private String tokenDescadastro(UUID id) {
        return jdbc.queryForObject("SELECT token_descadastro FROM marketing.leads WHERE id = ?", String.class, id);
    }

    private ResultActions listar(String filtro, String valor) throws Exception {
        return pedir(get("/api/marketing/leads").param(filtro, valor).param("ordenar", "nome,asc"), TOKEN_MARKETING)
                .andExpect(status().isOk());
    }

    private void assertIds(ResultActions resposta, UUID... esperados) throws Exception {
        JsonNode itens = corpo(resposta).at("/data/itens");
        List<String> ids = new ArrayList<>();
        itens.forEach(item -> ids.add(item.get("id").asText()));
        assertThat(ids).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(esperados).map(UUID::toString).toList());
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, String token) throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + token));
    }

    private JsonNode corpo(ResultActions resposta) throws Exception {
        return json.readTree(resposta.andReturn().getResponse().getContentAsByteArray());
    }

    private static List<String> nomes(JsonNode objeto) {
        List<String> nomes = new ArrayList<>();
        objeto.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }

    private Jwt usuario(UUID tenantDoToken, String... permissoes) {
        return jwt(Map.of("sub", usuario.toString(), "tenant_id", tenantDoToken.toString(),
                "perms", List.of(permissoes)));
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"), claims);
    }
}
