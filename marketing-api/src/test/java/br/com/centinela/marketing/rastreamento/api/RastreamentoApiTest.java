package br.com.centinela.marketing.rastreamento.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;

/**
 * API do rastreamento (R1) pelo filtro de segurança real: configuração, links de campanha e visitas,
 * com permissões, validação e isolamento por tenant.
 */
@AutoConfigureMockMvc
class RastreamentoApiTest extends IntegracaoDeTeste {

    private static final String TOKEN_EDITA = "token-edita";
    private static final String TOKEN_VE = "token-ve";
    private static final String TOKEN_SEM = "token-sem";
    private static final String TOKEN_OUTRO = "token-outro";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    JwtDecoder decoder;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void tokens() {
        Map<String, Jwt> tokens = new HashMap<>();
        tokens.put(TOKEN_EDITA, usuario(tenant, "marketing.configuracao.ver", "marketing.configuracao.editar"));
        tokens.put(TOKEN_VE, usuario(tenant, "marketing.configuracao.ver"));
        tokens.put(TOKEN_SEM, usuario(tenant, "marketing.lead.ver"));
        tokens.put(TOKEN_OUTRO, usuario(UUID.randomUUID(), "marketing.configuracao.ver", "marketing.configuracao.editar"));
        when(decoder.decode(anyString())).thenAnswer(chamada -> {
            Jwt token = tokens.get(chamada.<String>getArgument(0));
            if (token == null) {
                throw new BadJwtException("token desconhecido");
            }
            return token;
        });
    }

    @Test
    void configuracaoTemPadraoEValidaOPrazo() throws Exception {
        pedir(get("/api/marketing/rastreamento"), TOKEN_VE).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativo").value(true))
                .andExpect(jsonPath("$.data.prazoConversaoMinutos").value(30))
                .andExpect(jsonPath("$.data.urlScript").value("http://localhost:8087/public/marketing/rastreamento.js"));

        pedir(put("/api/marketing/rastreamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\": true, \"prazoConversaoMinutos\": 0}"), TOKEN_EDITA)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].campo").value("prazoConversaoMinutos"));
        pedir(put("/api/marketing/rastreamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\": true, \"prazoConversaoMinutos\": 2}"), TOKEN_VE)
                .andExpect(status().isForbidden());
        pedir(put("/api/marketing/rastreamento").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativo\": true, \"prazoConversaoMinutos\": 2}"), TOKEN_EDITA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.prazoConversaoMinutos").value(2));
        pedir(get("/api/marketing/rastreamento"), TOKEN_VE).andExpect(jsonPath("$.data.prazoConversaoMinutos").value(2));
        pedir(get("/api/marketing/rastreamento"), TOKEN_SEM).andExpect(status().isForbidden());
    }

    @Test
    void linksDeCampanhaComUrlPublicaEIsolamentoPorTenant() throws Exception {
        String criado = pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Black Friday · Google", "urlDestino": "https://empresa-a.dev/black-friday",
                 "utmSource": "google", "utmMedium": "cpc", "utmCampaign": "black-friday-2026"}"""), TOKEN_EDITA)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/marketing/links-campanha/")))
                .andExpect(jsonPath("$.data.urlPublica", startsWith("http://localhost:8087/public/marketing/c/")))
                .andExpect(jsonPath("$.data.visitas").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(criado).at("/data/id").asText();
        String codigo = json.readTree(criado).at("/data/codigo").asText();

        pedir(post("/api/marketing/links-campanha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"x\", \"urlDestino\": \"javascript:alert(1)\"}"), TOKEN_EDITA)
                .andExpect(status().isBadRequest());

        mvc.perform(get("/public/marketing/c/" + codigo).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0)"))
                .andExpect(status().isFound());

        pedir(get("/api/marketing/links-campanha"), TOKEN_VE).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itens", hasSize(1)))
                .andExpect(jsonPath("$.data.itens[0].visitas").value(1));
        pedir(get("/api/marketing/visitas-anuncio").param("situacao", "ativa"), TOKEN_VE).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itens", hasSize(1)))
                .andExpect(jsonPath("$.data.itens[0].linkNome").value("Black Friday · Google"))
                .andExpect(jsonPath("$.data.itens[0].contextoTecnico.tipoDispositivo").value("desktop"))
                .andExpect(jsonPath("$.data.itens[0].utmCampaign").value("black-friday-2026"));
        pedir(get("/api/marketing/visitas-anuncio").param("situacao", "outra"), TOKEN_VE)
                .andExpect(status().isBadRequest());
        pedir(get("/api/marketing/visitas-anuncio/resumo"), TOKEN_VE).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ativas").value(1)).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.taxaConversao").value(0.0));

        // Outro tenant não vê nem mexe
        pedir(get("/api/marketing/links-campanha"), TOKEN_OUTRO).andExpect(jsonPath("$.data.itens", hasSize(0)));
        pedir(get("/api/marketing/visitas-anuncio"), TOKEN_OUTRO).andExpect(jsonPath("$.data.itens", hasSize(0)));
        pedir(put("/api/marketing/links-campanha/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"x\", \"urlDestino\": \"https://outro.dev\"}"), TOKEN_OUTRO)
                .andExpect(status().isNotFound());
        pedir(delete("/api/marketing/links-campanha/" + id), TOKEN_OUTRO).andExpect(status().isNotFound());

        pedir(put("/api/marketing/links-campanha/" + id).contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Black Friday · Google", "urlDestino": "https://empresa-a.dev/black-friday", "ativo": false}"""),
                TOKEN_EDITA).andExpect(status().isOk()).andExpect(jsonPath("$.data.ativo").value(false))
                .andExpect(jsonPath("$.data.codigo").value(codigo));
        mvc.perform(get("/public/marketing/c/" + codigo)).andExpect(status().isNotFound());

        pedir(delete("/api/marketing/links-campanha/" + id), TOKEN_EDITA).andExpect(status().isOk());
        pedir(get("/api/marketing/links-campanha"), TOKEN_VE).andExpect(jsonPath("$.data.itens", hasSize(0)));
        assertThat(codigo).hasSize(10);
    }

    private ResultActions pedir(MockHttpServletRequestBuilder pedido, String token) throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + token));
    }

    private static Jwt usuario(UUID tenantDoToken, String... permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("sub", UUID.randomUUID().toString(), "tenant_id", tenantDoToken.toString(),
                        "perms", List.of(permissoes)));
    }
}
