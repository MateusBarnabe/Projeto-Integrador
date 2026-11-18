package br.com.centinela.marketing.comunicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.comunicacao.ClienteSmtp.MensagemSmtp;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.StatusAquecimento;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Testes de integração da D3, E7 (descadastro público), E8 (boas-vindas, job 4.5), E9 (aquecimento,
 * job 4.2) e E10 (anonimização LGPD, job 4.3) contra o PostgreSQL real.
 */
@AutoConfigureMockMvc
class ComunicacaoJobsEDescadastroTest extends IntegracaoDeTeste {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadConsentimentoRepository consentimentos;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    ModeloMensagemRepository modelos;

    @Autowired
    LeadComunicacaoRepository comunicacoes;

    @Autowired
    CicloVidaConfiguracaoRepository ciclos;

    @Autowired
    ConfirmacaoDeBoasVindas boasVindas;

    @Autowired
    CadenciaDeAquecimento aquecimento;

    @Autowired
    AnonimizacaoPorRetencao anonimizacao;

    @MockitoBean
    ClienteSmtp smtp;

    private final UUID tenant = UUID.randomUUID();
    private final List<MensagemSmtp> emailsEnviados = new ArrayList<>();

    @BeforeEach
    void prepararCorreio() {
        emailsEnviados.clear();
        reset(smtp);
        doAnswer(invocacao -> {
            emailsEnviados.add(invocacao.getArgument(0));
            return null;
        }).when(smtp).enviar(any(MensagemSmtp.class));
    }

    @Test
    void descadastroPublicoNaoMudaNoGetEEfetivaNoPostComIpEUserAgent() throws Exception {
        Lead lead = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 1);
            novo.atualizarContato("Ana Souza", "ana@exemplo.dev", null, null);
            novo.registrarConsentimento(true, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
            return leads.save(novo);
        });
        String token = lead.getTokenDescadastro();

        // GET nunca efetiva o opt-out (leitores de e-mail abrem links sozinhos)
        mvc.perform(get("/public/marketing/descadastro/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.descadastrado").value(false));

        Lead aposGet = TenantContexto.calcular(tenant, () -> leads.findById(lead.getId())).orElseThrow();
        assertThat(aposGet.getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);
        assertThat(aposGet.isConsentimentoMarketing()).isTrue();

        // POST efetiva o opt-out, grava a revogação com IP e User-Agent e registra na timeline
        mvc.perform(post("/public/marketing/descadastro/" + token)
                .header("X-Forwarded-For", "203.0.113.42, 10.0.0.1")
                .header("User-Agent", "Mozilla/5.0 Teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.descadastrado").value(true));

        Lead aposPost = TenantContexto.calcular(tenant, () -> leads.findById(lead.getId())).orElseThrow();
        assertThat(aposPost.getStatusAquecimento()).isEqualTo(StatusAquecimento.OPT_OUT);
        assertThat(aposPost.getOptOutEm()).isNotNull();
        assertThat(aposPost.isConsentimentoMarketing()).isFalse();

        List<LeadConsentimento> historico = TenantContexto.calcular(tenant,
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()));
        assertThat(historico).singleElement().satisfies(revogacao -> {
            assertThat(revogacao.isAceito()).isFalse();
            assertThat(revogacao.getOrigem()).isEqualTo(OrigemConsentimento.DESCADASTRO);
            assertThat(revogacao.getIp()).isEqualTo("203.0.113.42");
            assertThat(revogacao.getUserAgent()).isEqualTo("Mozilla/5.0 Teste");
        });
        assertThat(TenantContexto.calcular(tenant, () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId())))
                .extracting(LeadEvento::getTipoEvento)
                .containsExactly(TipoEventoLead.DESCADASTRADO);

        // Segundo POST é idempotente e token inválido recebe a mesma resposta neutra
        mvc.perform(post("/public/marketing/descadastro/" + token)).andExpect(status().isOk());
        mvc.perform(post("/public/marketing/descadastro/token-inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(TenantContexto.calcular(tenant,
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()))).hasSize(1);
    }

    @Test
    void descadastroPeloNavegadorMostraAPaginaComOBotaoEEfetivaNoPost() throws Exception {
        Lead lead = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 1);
            novo.atualizarContato("Gil Rocha", "gil@exemplo.dev", null, null);
            novo.registrarConsentimento(true, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
            return leads.save(novo);
        });
        String token = lead.getTokenDescadastro();
        String navegador = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

        // GET: página com o formulário que faz POST na própria URL, sem nada do lead e sem o token
        mvc.perform(get("/public/marketing/descadastro/" + token).header("Accept", navegador))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<form method=\"post\">")))
                .andExpect(content().string(not(containsString(token))))
                .andExpect(content().string(not(containsString("gil@exemplo.dev"))))
                .andExpect(header().string("Content-Security-Policy", containsString("form-action 'self'")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(TenantContexto.calcular(tenant, () -> leads.findById(lead.getId())).orElseThrow()
                .getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);

        // POST do formulário: efetiva e responde a página de concluído
        mvc.perform(post("/public/marketing/descadastro/" + token).header("Accept", navegador)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("User-Agent", "Mozilla/5.0 Navegador"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Descadastro registrado")));
        assertThat(TenantContexto.calcular(tenant, () -> leads.findById(lead.getId())).orElseThrow()
                .getStatusAquecimento()).isEqualTo(StatusAquecimento.OPT_OUT);

        // Token inválido recebe a mesma página neutra
        mvc.perform(post("/public/marketing/descadastro/token-inexistente").header("Accept", navegador))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Descadastro registrado")));
    }

    @Test
    void boasVindasSaiUmaVezParaEtapa3MesmoSemConsentimentoENaoSaiParaOptOut() {
        Lead etapa3 = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 3);
            novo.atualizarContato("Carlos Lima", "carlos@exemplo.dev", null, null);
            novo.atualizarUtm("google", "cpc", "black-friday", null, null);
            return leads.save(novo);
        });
        Lead optOut = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 3);
            novo.atualizarContato("OptOut", "optout@exemplo.dev", null, null);
            novo.registrarOptOut(OffsetDateTime.now(ZoneOffset.UTC));
            return leads.save(novo);
        });

        ConfirmacaoDeBoasVindas.Resultado primeiro = boasVindas.enviarPendentes();
        assertThat(primeiro.enviados()).isGreaterThanOrEqualTo(1);

        assertThat(emailsEnviados).anySatisfy(msg -> {
            assertThat(msg.destinatario()).isEqualTo("carlos@exemplo.dev");
            assertThat(msg.assunto()).contains("Carlos Lima");
            assertThat(msg.corpo()).contains("google")
                    .contains("/public/marketing/descadastro/" + etapa3.getTokenDescadastro());
        });
        assertThat(emailsEnviados).noneSatisfy(msg -> assertThat(msg.destinatario()).isEqualTo("optout@exemplo.dev"));

        Lead atualizado = TenantContexto.calcular(tenant, () -> leads.findById(etapa3.getId())).orElseThrow();
        assertThat(atualizado.getBoasVindasEnviadaEm()).isNotNull();
        assertThat(atualizado.getTentativasAquecimento()).as("boas-vindas não conta em aquecimento").isZero();

        // Segunda execução não envia de novo
        emailsEnviados.clear();
        boasVindas.enviarPendentes();
        assertThat(emailsEnviados).noneSatisfy(msg -> assertThat(msg.destinatario()).isEqualTo("carlos@exemplo.dev"));
        assertThat(TenantContexto.calcular(tenant, () -> leads.findById(optOut.getId())).orElseThrow()
                .getBoasVindasEnviadaEm()).isNull();
    }

    @Test
    void boasVindasDesisteDepoisDeTresFalhasSeguidas() {
        doThrow(new IllegalStateException("SMTP fora do ar")).when(smtp).enviar(any(MensagemSmtp.class));
        Lead lead = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 3);
            novo.atualizarContato("Davi", "davi@exemplo.dev", null, null);
            return leads.save(novo);
        });

        for (int i = 0; i < 3; i++) {
            boasVindas.enviarPendentes();
        }
        // Na 4ª execução, o lead já tem 3 falhas e não é mais selecionado
        boasVindas.enviarPendentes();

        List<LeadComunicacao> historico = TenantContexto.calcular(tenant,
                () -> comunicacoes.findByLeadIdOrderByEnviadoEmDescCriadoEmDesc(lead.getId()));
        assertThat(historico).hasSize(3).allSatisfy(
                c -> assertThat(c.getStatus()).isEqualTo(StatusComunicacao.FALHOU));
    }

    @Test
    void cadenciaDeAquecimentoEnviaEmOrdemRespeitaConsentimentoEEsgotaNoLimite() {
        TenantContexto.executar(tenant, () -> {
            ciclos.save(new CicloVidaConfiguracao(2, 6, 12));
            modelos.save(ModeloMensagem.deAquecimento("1º Toque", 1, 1, 0,
                    "Olá {{nome}}, tudo bem?", "Primeiro contato via {{origem}}", true));
            modelos.save(ModeloMensagem.deAquecimento("2º Toque", null, 2, 0,
                    "Ainda podemos ajudar, {{nome}}?", "Segundo contato", true));
        });

        Lead comConsentimento = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 1);
            novo.atualizarContato("Bia", "bia@exemplo.dev", null, null);
            novo.registrarConsentimento(true, OffsetDateTime.now(ZoneOffset.UTC));
            return leads.save(novo);
        });
        Lead semConsentimento = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 1);
            novo.atualizarContato("Sem", "sem@exemplo.dev", null, null);
            return leads.save(novo);
        });

        // 1º toque
        aquecimento.executarCadencia();
        Lead apos1 = TenantContexto.calcular(tenant, () -> leads.findById(comConsentimento.getId())).orElseThrow();
        assertThat(apos1.getTentativasAquecimento()).isEqualTo(1);
        assertThat(apos1.getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);

        // 2º toque atinge max_tentativas_aquecimento (2) → esgota
        aquecimento.executarCadencia();
        Lead apos2 = TenantContexto.calcular(tenant, () -> leads.findById(comConsentimento.getId())).orElseThrow();
        assertThat(apos2.getTentativasAquecimento()).isEqualTo(2);
        assertThat(apos2.getStatusAquecimento()).isEqualTo(StatusAquecimento.ESGOTADO);
        assertThat(apos2.getEsgotadoEm()).isNotNull();

        assertThat(TenantContexto.calcular(tenant, () -> leads.findById(semConsentimento.getId())).orElseThrow()
                .getTentativasAquecimento()).isZero();
        assertThat(TenantContexto.calcular(tenant,
                () -> comunicacoes.findByLeadIdOrderByEnviadoEmDescCriadoEmDesc(comConsentimento.getId()))).hasSize(2);
    }

    @Test
    void anonimizacaoPorRetencaoZeraDadosPessoaisEMantemEstatisticas() {
        TenantContexto.executar(tenant, () -> ciclos.save(new CicloVidaConfiguracao(3, 6, 12)));
        OffsetDateTime seteMesesAtras = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(7);

        Lead vencido = TenantContexto.calcular(tenant, () -> {
            Lead novo = new Lead(OrigemLead.LANDING_PAGE, 1);
            novo.atualizarContato("Eva", "eva@exemplo.dev", "5562999991111", "5562999991111");
            novo.atualizarUtm("google", "cpc", "campanha-retencao", null, null);
            novo.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.FORMULARIO);
            novo.registrarRespostas(List.of(Map.of("rotulo", "Cargo", "valor", "Diretora")), false, seteMesesAtras);
            novo.registrarInteracao(Map.of("tipoDispositivo", "smartphone"), Map.of("tempoNaPaginaSegundos", 45));
            novo.esgotarAquecimento(seteMesesAtras);
            Lead salvo = leads.save(novo);
            consentimentos.save(LeadConsentimento.revogacaoPorDescadastro(
                    salvo.getId(), seteMesesAtras, "198.51.100.10", "Mozilla/5.0"));
            return salvo;
        });

        int total = anonimizacao.anonimizarVencidos();
        assertThat(total).isGreaterThanOrEqualTo(1);

        Lead anonimizado = TenantContexto.calcular(tenant, () -> leads.findById(vencido.getId())).orElseThrow();
        assertThat(anonimizado.isAnonimizado()).isTrue();
        assertThat(anonimizado.getNome()).isNull();
        assertThat(anonimizado.getEmail()).isNull();
        assertThat(anonimizado.getTelefone()).isNull();
        assertThat(anonimizado.getWhatsapp()).isNull();
        assertThat(anonimizado.getTokenDescadastro()).isNull();
        assertThat(anonimizado.getDadosFormulario()).isNull();
        assertThat(anonimizado.getContextoTecnico()).isNull();
        assertThat(anonimizado.getComportamento()).isNull();
        assertThat(anonimizado.getUtmCampaign()).as("mantém dados estatísticos").isEqualTo("campanha-retencao");
        assertThat(anonimizado.getEtapa()).isEqualTo(1);

        LeadConsentimento consentimento = TenantContexto.calcular(tenant,
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(vencido.getId())).getFirst();
        assertThat(consentimento.getIp()).isNull();
        assertThat(consentimento.getUserAgent()).isNull();
        assertThat(consentimento.getFinalidade()).isEqualTo("marketing");

        assertThat(TenantContexto.calcular(tenant, () -> eventos.findByLeadIdOrderBySequenciaAsc(vencido.getId())))
                .extracting(LeadEvento::getTipoEvento)
                .contains(TipoEventoLead.ANONIMIZADO);
    }
}
