package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.MetricaComportamento;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.rastreamento.DescarteDeVisitas;
import br.com.centinela.marketing.rastreamento.LinkCampanha;
import br.com.centinela.marketing.rastreamento.LinkCampanhaRepository;
import br.com.centinela.marketing.rastreamento.SituacaoVisita;
import br.com.centinela.marketing.rastreamento.VisitaAnuncio;
import br.com.centinela.marketing.rastreamento.VisitaAnuncioRepository;

/**
 * R1 de ponta a ponta, o "lead 0": o clique no link de campanha registra a visita com o contexto
 * técnico do aparelho e redireciona para a landing page com o {@code mkt_visita}; o script manda os
 * sinais; o envio do formulário traz o {@code mkt_visita} na página de entrada e a leitura do envio
 * (E5) liga a visita ao lead, que fica com o contexto e o comportamento e pontua pelas regras de
 * comportamento. Sem conversão no prazo, o job 4.7 descarta os dados da visita.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class VisitaDeAnuncioIntegracaoTest extends EnvioDoLandingTeste {

    static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    static final String DESTINO = "https://empresa-a.dev/black-friday";

    @Autowired
    MockMvc mvc;

    @Autowired
    LinkCampanhaRepository links;

    @Autowired
    VisitaAnuncioRepository visitas;

    @Autowired
    DescarteDeVisitas descarte;

    LinkCampanha link;

    @BeforeEach
    void prepararCampanha() {
        link = TenantContexto.calcular(tenant, () -> transacao.execute(s -> {
            regras.save(RegraPontuacao.porComportamento("Engajamento", MetricaComportamento.TEMPO_NA_PAGINA,
                    List.of(Map.of("minimo", 60)), 20));
            regras.save(RegraPontuacao.porComportamento("Interesse em preço", MetricaComportamento.PAGINA_VISITADA,
                    List.of("precos"), 10));
            return links.save(new LinkCampanha("Black Friday · Google", DESTINO,
                    new LinkCampanha.Utm("google", "cpc", "black-friday-2026", null, null)));
        }));
    }

    @Test
    void cliqueSinaisEEnvioLigamAVisitaAoLeadQuePontuaPeloComportamento(CapturedOutput saida) throws Exception {
        String paginaDeEntrada = clicar(IPHONE);
        assertThat(paginaDeEntrada).startsWith(DESTINO + "?utm_source=google&utm_medium=cpc"
                + "&utm_campaign=black-friday-2026&mkt_visita=");
        UUID visitaId = visitaDa(paginaDeEntrada);

        VisitaAnuncio clicada = visita(visitaId);
        assertThat(clicada.getSituacao()).isEqualTo(SituacaoVisita.ATIVA);
        assertThat(clicada.getUtmCampaign()).isEqualTo("black-friday-2026");
        assertThat(clicada.getContextoTecnico()).containsEntry("tipoDispositivo", "smartphone")
                .containsEntry("sistema", "iOS").containsEntry("navegador", "Safari")
                .containsEntry("localizacao", Map.of("cidade", "Goiânia", "uf", "GO", "pais", "BR", "simulada", true));
        assertThat(String.valueOf(clicada.getContextoTecnico())).as("sem User-Agent bruto").doesNotContain("Mozilla");

        sinais(visitaId, """
                {"tempoNaPaginaSegundos": 95, "profundidadeRolagem": 80, "elementosClicados": ["cta-principal"],
                 "paginasVisitadas": ["/", "/#precos?email=ana@exemplo.com"], "idioma": "pt-BR",
                 "fuso": "America/Sao_Paulo", "tela": "390x844"}""");
        // Reenvio com totais menores (outra aba) não diminui; listas se juntam
        sinais(visitaId, """
                {"tempoNaPaginaSegundos": 30, "profundidadeRolagem": 40, "elementosClicados": ["fale-conosco"]}""");

        VisitaAnuncio comSinais = visita(visitaId);
        assertThat(comSinais.getComportamento()).containsEntry("tempoNaPaginaSegundos", 95)
                .containsEntry("profundidadeRolagem", 80)
                .containsEntry("elementosClicados", List.of("cta-principal", "fale-conosco"))
                .containsEntry("paginasVisitadas", List.of("/", "/#precos"));
        assertThat(comSinais.getContextoTecnico()).containsEntry("idioma", "pt-BR").containsEntry("tela", "390x844");
        assertThat(comSinais.getExpiraEm()).isAfter(clicada.getExpiraEm());

        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envioComEntrada(envio, paginaDeEntrada));
        processarEsperandoOEvento(formularioRecebido(tenant, envio, null, null));

        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getContextoTecnico()).containsEntry("tipoDispositivo", "smartphone");
        assertThat(lead.getComportamento()).containsEntry("tempoNaPaginaSegundos", 95);
        assertThat(lead.getPontuacao()).as("70 das respostas e UTM + 20 de tempo + 10 de preço").isEqualTo(100);
        assertThat(lead.getPontuacaoDetalhe()).anySatisfy(item -> assertThat(item)
                .containsEntry("fonte", "comportamento").containsEntry("campoComportamento", "tempo_na_pagina"));

        VisitaAnuncio convertida = visita(visitaId);
        assertThat(convertida.getSituacao()).isEqualTo(SituacaoVisita.CONVERTIDA);
        assertThat(convertida.getLeadId()).isEqualTo(lead.getId());
        LeadEvento vinculo = doTipo(tenant, lead.getId(), TipoEventoLead.VISITA_VINCULADA).getFirst();
        assertThat(vinculo.getPayload()).containsEntry("visitaId", visitaId.toString())
                .containsEntry("utmCampaign", "black-friday-2026").containsEntry("tipoDispositivo", "smartphone");
        assertThat(timelineDe(tenant, lead.getId())).extracting(LeadEvento::getTipoEvento)
                .containsSubsequence(TipoEventoLead.VISITA_VINCULADA, TipoEventoLead.QUALIFICACAO_ALTERADA);

        // Visita convertida não muda mais
        sinais(visitaId, """
                {"tempoNaPaginaSegundos": 500}""");
        assertThat(visita(visitaId).getComportamento()).containsEntry("tempoNaPaginaSegundos", 95);
        assertThat(saida.getAll()).doesNotContain("iPhone OS").doesNotContain("ana@exemplo.com");
    }

    @Test
    void visitaSemConversaoNoPrazoEDescartadaENaoSeLigaAoEnvio() throws Exception {
        String paginaDeEntrada = clicar(IPHONE);
        UUID visitaId = visitaDa(paginaDeEntrada);
        sinais(visitaId, """
                {"tempoNaPaginaSegundos": 120, "paginasVisitadas": ["/#precos"]}""");
        jdbc.update("UPDATE marketing.visitas_anuncio SET expira_em = now() - interval '1 minute' WHERE id = ?",
                visitaId);

        assertThat(descarte.descartar()).isGreaterThanOrEqualTo(1);

        VisitaAnuncio descartada = visita(visitaId);
        assertThat(descartada.getSituacao()).isEqualTo(SituacaoVisita.DESCARTADA);
        assertThat(descartada.getDescartadaEm()).isNotNull();
        assertThat(descartada.getContextoTecnico()).isNull();
        assertThat(descartada.getComportamento()).isNull();
        assertThat(descartada.getUtmCampaign()).as("fica para as contagens").isEqualTo("black-friday-2026");

        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envioComEntrada(envio, paginaDeEntrada));
        processarEsperandoOEvento(formularioRecebido(tenant, envio, null, null));

        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getContextoTecnico()).isNull();
        assertThat(lead.getPontuacao()).as("sem as regras de comportamento").isEqualTo(70);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.VISITA_VINCULADA)).isEmpty();
        assertThat(visita(visitaId).getSituacao()).isEqualTo(SituacaoVisita.DESCARTADA);
    }

    @Test
    void rotasPublicasRecusamLinkEVisitaDesconhecidosESinalForaDoFormato() throws Exception {
        mvc.perform(get("/public/marketing/c/naoexiste")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/public/marketing/visitas/" + UUID.randomUUID() + "/sinais")
                .contentType(MediaType.TEXT_PLAIN).content("{\"tempoNaPaginaSegundos\": 10}"))
                .andExpect(status().isNotFound());
        UUID visitaId = visitaDa(clicar(IPHONE));
        mvc.perform(post("/public/marketing/visitas/" + visitaId + "/sinais")
                .contentType(MediaType.TEXT_PLAIN).content("não é json")).andExpect(status().isBadRequest());
        mvc.perform(get("/public/marketing/rastreamento.js")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));

        TenantContexto.executar(tenant, () -> transacao.executeWithoutResult(s -> {
            LinkCampanha desligado = links.findById(link.getId()).orElseThrow();
            desligado.alterar(desligado.getNome(), DESTINO, desligado.utm(), false);
            links.save(desligado);
        }));
        mvc.perform(get("/public/marketing/c/" + link.getCodigo())).andExpect(status().isNotFound());
    }

    // --- apoio -----------------------------------------------------------------------------------

    /**
     * Publica e espera o próprio evento: com outros contextos do cache de testes também ouvindo a fila,
     * o marcador do {@link #processar} pode ser consumido antes dele, por outro consumidor.
     */
    void processarEsperandoOEvento(Map<String, Object> envelope) throws Exception {
        publicar(envelope);
        esperarAte(() -> processados(envelope) == 1);
    }

    /** Clica no link como o navegador do anúncio e devolve a URL da landing page. */
    String clicar(String userAgent) throws Exception {
        return mvc.perform(get("/public/marketing/c/" + link.getCodigo()).header("User-Agent", userAgent)
                .header("X-Forwarded-For", "200.100.50.25, 10.0.0.1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getHeader("Location");
    }

    void sinais(UUID visitaId, String corpo) throws Exception {
        // Como o navigator.sendBeacon manda da landing page: outra origem, text/plain, sem preflight
        mvc.perform(post("/public/marketing/visitas/" + visitaId + "/sinais")
                .header("Origin", "http://localhost:3008")
                .contentType("text/plain;charset=UTF-8").content(corpo))
                .andExpect(status().isOk());
    }

    static UUID visitaDa(String paginaDeEntrada) {
        return UUID.fromString(paginaDeEntrada.replaceAll(".*mkt_visita=([0-9a-f-]{36}).*", "$1"));
    }

    VisitaAnuncio visita(UUID id) {
        return TenantContexto.calcular(tenant, () -> visitas.findById(id)).orElseThrow();
    }

    /** O envio com a página de entrada da última origem vinda do clique, como o Landing guarda. */
    Map<String, Object> envioComEntrada(UUID envioId, String paginaDeEntrada) {
        Map<String, Object> envio = envio(envioId, "ana.souza@empresa.com.br", "(62) 99999-8888");
        Map<String, Object> ultima = new LinkedHashMap<>();
        ultima.put("utmSource", "google");
        ultima.put("utmMedium", "cpc");
        ultima.put("utmCampaign", "black-friday-2026");
        ultima.put("referenciador", "https://www.google.com/");
        ultima.put("paginaEntrada", paginaDeEntrada);
        ultima.put("ocorridoEm", ULTIMO_TOQUE.toString());
        envio.put("origem", Map.of("primeira", ultima, "ultima", ultima));
        return envio;
    }
}
