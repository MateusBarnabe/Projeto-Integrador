package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.centinela.marketing.compartilhado.mensageria.TopologiaDeConsumo;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.Temperatura;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * E5 de ponta a ponta: o "Landing" publica {@code landing.formulario.recebido} e responde o envio no
 * WireMock; o consumidor real da fila {@code marketing.landing-leads} grava o lead na etapa 3 com
 * respostas, origem, consentimento, pontuação e vendedor, junta duplicados e, com o Landing fora do ar,
 * deixa a leitura pendente sem mandar a mensagem para a DLQ.
 */
@ExtendWith(OutputCaptureExtension.class)
class EnvioDoFormularioIntegracaoTest extends EnvioDoLandingTeste {

    @Test
    void envioNovoViraLeadNaEtapa3ComRespostasUtmConsentimentoPontuacaoEVendedor(CapturedOutput saida)
            throws Exception {
        UUID envio = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        Map<String, Object> respostaDoLanding = envio(envio, " Ana.Souza@Empresa.COM.br ", "(62) 99999-8888");
        respostaDoLanding.put("empresaId", empresa.toString());
        respostaDoLanding.put("contatoId", contato.toString());
        stubEnvio(tenant, envio, respostaDoLanding);
        Map<String, Object> evento = formularioRecebido(tenant, envio, empresa, contato);

        processar(evento);

        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getTemperatura()).isEqualTo(Temperatura.QUENTE);
        assertThat(lead.getOrigem()).isEqualTo(OrigemLead.LANDING_PAGE);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getCrmEmpresaId()).isEqualTo(empresa);
        assertThat(lead.getCrmContatoId()).isEqualTo(contato);
        assertThat(lead.getNome()).isEqualTo("Ana Souza");
        assertThat(lead.getEmail()).isEqualTo("ana.souza@empresa.com.br");
        assertThat(lead.getTelefone()).isEqualTo("5562999998888");
        assertThat(lead.getUtmSource()).as("da última origem").isEqualTo("google");
        assertThat(lead.getUtmMedium()).isEqualTo("cpc");
        assertThat(lead.getUtmCampaign()).isEqualTo("black-friday-2026");
        assertThat(lead.getLandingPageUrl()).startsWith("https://empresa-a.dev/black-friday?utm_source=google");
        assertThat(lead.getFirstTouch()).isEqualTo(PRIMEIRO_TOQUE);
        assertThat(lead.getLastTouch()).isEqualTo(ULTIMO_TOQUE);
        assertThat(lead.getEnvioLidoEm()).isNotNull();
        assertThat(lead.leituraDoEnvioPendente()).isFalse();
        assertThat(lead.getDadosFormulario()).hasSize(5).anySatisfy(resposta -> assertThat(resposta)
                .containsEntry("campoId", campoCargo.toString()).containsEntry("rotulo", "Cargo")
                .containsEntry("tipo", "LISTA_OPCOES").containsEntry("valor", "Decisor"));
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(lead.isConsentimentoMarketing()).isTrue();
        assertThat(lead.getConsentimentoEm()).isEqualTo(ACEITO_EM);
        assertThat(lead.getCriadoPor()).as("consumidor grava sem usuário").isNull();

        assertThat(consentimentosDe(lead)).singleElement().satisfies(aceite -> {
            assertThat(aceite.getOrigem()).isEqualTo(OrigemConsentimento.FORMULARIO_RECEBIDO);
            assertThat(aceite.getTextoTermo()).isEqualTo(TEXTO_TERMO);
            assertThat(aceite.getRegistradoEm()).isEqualTo(ACEITO_EM);
            assertThat(aceite.getEnvioId()).isEqualTo(envio);
            assertThat(aceite.getFormularioId()).isEqualTo(formulario);
            assertThat(aceite.getEventoId()).isEqualTo(UUID.fromString((String) evento.get("id")));
            assertThat(aceite.getIp()).isNull();
        });
        assertThat(timelineDe(tenant, lead.getId())).extracting(LeadEvento::getTipoEvento).containsExactly(
                TipoEventoLead.ETAPA_ALTERADA, TipoEventoLead.RESPOSTAS_LIDAS,
                TipoEventoLead.CONSENTIMENTO_REGISTRADO, TipoEventoLead.QUALIFICACAO_ALTERADA,
                TipoEventoLead.VENDEDOR_ATRIBUIDO);
        LeadEvento lidas = doTipo(tenant, lead.getId(), TipoEventoLead.RESPOSTAS_LIDAS).getFirst();
        assertThat(lidas.getPayload()).containsEntry("envioId", envio.toString()).containsEntry("respostas", 5)
                .containsEntry("fonte", TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO);
        assertThat(timelineDe(tenant, lead.getId())).allSatisfy(item -> assertThat(String.valueOf(item.getPayload()))
                .doesNotContain("empresa.com.br").doesNotContain("99999").doesNotContain("Decisor")
                .doesNotContain("Autorizo"));

        JsonNode notificacao = notificacao(env -> env.at("/dados/rota").asText().equals("/leads/" + lead.getId()),
                10_000);
        assertThat(notificacao).isNotNull();
        assertThat(notificacao.at("/dados/usuarioId").asText()).isEqualTo(vendedor.toString());
        assertThat(notificacao.at("/dados/titulo").asText()).isEqualTo("Novo lead prioritário");
        assertThat(notificacao(env -> env.at("/dados/rota").asText().equals("/leads/" + lead.getId()), 1_000))
                .as("uma notificação só").isNull();

        assertThat(saida.getAll()).doesNotContainIgnoringCase("ana.souza@").doesNotContain("99999-8888")
                .doesNotContain("62999998888").doesNotContain("Autorizo a Empresa");
    }

    @Test
    void capturaDoChatbotEEnvioDaMesmaPessoaViramUmLeadSo() throws Exception {
        UUID sessao = UUID.randomUUID();
        processar(capturaDoChatbot(tenant, sessao, "Bia@Exemplo.com"));
        Lead daCaptura = TenantContexto.calcular(tenant, () -> leads.findBySessaoId(sessao)).orElseThrow();
        assertThat(daCaptura.getFaixaQualificacao()).as("só UTM").isEqualTo(FaixaQualificacao.NUTRICAO);
        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envio(envio, "bia@exemplo.com", null));

        processar(formularioRecebido(tenant, envio, null, null));

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getId()).isEqualTo(daCaptura.getId());
        assertThat(lead.getSessaoId()).isEqualTo(sessao);
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getCanal()).as("o envio é o fato mais recente").isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(consentimentosDe(lead)).extracting(LeadConsentimento::getOrigem).containsExactlyInAnyOrder(
                OrigemConsentimento.FORMULARIO_RECEBIDO, OrigemConsentimento.CHATBOT_LEAD_CAPTURADO);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.ETAPA_ALTERADA)).extracting(LeadEvento::getEtapaNova)
                .containsExactly(1, 3);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.LEADS_UNIDOS)).as("achado direto, sem merge").isEmpty();
    }

    @Test
    void juntaDuplicadosDeMesmoEmailOuTelefoneNoMaisAntigo() throws Exception {
        UUID sessaoAntiga = UUID.randomUUID();
        Lead antigo = gravar(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
            lead.vincularSessao(sessaoAntiga);
            lead.vincularFormulario(formulario, CanalLead.CHATBOT);
            lead.atualizarContato("Carla", "carla@exemplo.com", null, null);
            return lead;
        });
        Thread.sleep(20); // created_at distinto: o mais antigo é quem fica
        UUID sessaoDoTelefone = UUID.randomUUID();
        Lead peloTelefone = gravar(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
            lead.vincularSessao(sessaoDoTelefone);
            lead.atualizarContato(null, null, "5562988887777", null);
            return lead;
        });
        TenantContexto.executar(tenant, () -> consentimentos.save(LeadConsentimento.doLanding(peloTelefone.getId(),
                OrigemConsentimento.CHATBOT_LEAD_CAPTURADO, true, ACEITO_EM.minusDays(1), null, formulario, null,
                sessaoDoTelefone, UUID.randomUUID())));
        UUID envio = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        Thread.sleep(20);
        Lead daOportunidade = gravar(tenant, () -> leadDaOportunidade(envio, empresa, contato, oportunidade));
        stubEnvio(tenant, envio, envio(envio, "Carla@Exemplo.com", "(62) 98888-7777"));
        Map<String, Object> evento = formularioRecebido(tenant, envio, empresa, contato);

        processar(evento);

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getId()).as("o mais antigo fica").isEqualTo(antigo.getId());
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getSessaoId()).isEqualTo(sessaoAntiga);
        assertThat(lead.getEmail()).isEqualTo("carla@exemplo.com");
        assertThat(lead.getTelefone()).isEqualTo("5562988887777");
        assertThat(lead.getCrmOportunidadeId()).as("a oportunidade ligada pela E6 vem junto").isEqualTo(oportunidade);
        assertThat(lead.getCrmEmpresaId()).isEqualTo(empresa);
        assertThat(lead.getCrmContatoId()).isEqualTo(contato);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getDadosFormulario()).hasSize(5);
        assertThat(lead.getEnvioLidoEm()).isNotNull();
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(consentimentosDe(lead)).extracting(LeadConsentimento::getOrigem).as("o do outro é copiado")
                .containsExactlyInAnyOrder(OrigemConsentimento.FORMULARIO_RECEBIDO,
                        OrigemConsentimento.CHATBOT_LEAD_CAPTURADO);

        for (Lead unido : new Lead[] {peloTelefone, daOportunidade}) {
            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT deleted_at, envio_id, sessao_id FROM marketing.leads WHERE id = ?", unido.getId());
            assertThat(linha.get("deleted_at")).as("marcado como excluído").isNotNull();
            assertThat(linha.get("envio_id")).isNull();
            assertThat(linha.get("sessao_id")).isNull();
            assertThat(doTipo(tenant, unido.getId(), TipoEventoLead.LEADS_UNIDOS)).singleElement()
                    .satisfies(item -> assertThat(item.getPayload()).containsEntry("papel", "unido")
                            .containsEntry("leadMantidoId", antigo.getId().toString()));
        }
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.LEADS_UNIDOS)).extracting(
                item -> item.getPayload().get("leadUnidoId")).containsExactlyInAnyOrder(
                        peloTelefone.getId().toString(), daOportunidade.getId().toString());
        assertThat(timelineDe(tenant, lead.getId())).allSatisfy(item -> assertThat(String.valueOf(item.getPayload()))
                .doesNotContain("carla@").doesNotContain("98888"));
        assertThat(idsNaDlq()).doesNotContain((String) evento.get("id"));
    }

    @Test
    void landingForaDoArDeixaLeituraPendenteSemMandarParaADlq() throws Exception {
        UUID envio = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        stubLandingForaDoAr(envio);
        Map<String, Object> evento = formularioRecebido(tenant, envio, UUID.randomUUID(), contato);

        processar(evento);

        assertThat(processados(evento)).isEqualTo(1);
        assertThat(idsNaDlq()).doesNotContain((String) evento.get("id"));
        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.leituraDoEnvioPendente()).isTrue();
        assertThat(lead.getEnvioLidoEm()).isNull();
        assertThat(lead.getLeituraEnvioFalhas()).isEqualTo(1);
        assertThat(lead.getLeituraEnvioDesistidaEm()).isNull();
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getCrmContatoId()).isEqualTo(contato);
        assertThat(lead.possuiIdentificadorDeContato()).as("o contato vem da leitura").isFalse();
        assertThat(lead.getPontuacao()).isNull();
        assertThat(lead.getVendedorId()).isNull();
        assertThat(consentimentosDe(lead)).isEmpty();
        assertThat(leiturasDoEnvio(envio)).isEqualTo(1);
    }

    @Test
    void mesmaMensagemDuasVezesTemUmEfeitoSo() throws Exception {
        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envio(envio, "davi@exemplo.com", null));
        Map<String, Object> evento = formularioRecebido(tenant, envio, null, null);

        processar(evento, evento);

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead lead = porEnvio(tenant, envio);
        assertThat(consentimentosDe(lead)).hasSize(1);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.RESPOSTAS_LIDAS)).hasSize(1);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.VENDEDOR_ATRIBUIDO)).hasSize(1);
        assertThat(leiturasDoEnvio(envio)).isEqualTo(1);
    }

    @Test
    void mesmoEnvioEMesmoEmailEmOutroTenantViramOutroLead() throws Exception {
        UUID outroTenant = UUID.randomUUID();
        prepararTenant(outroTenant);
        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envio(envio, "eva@exemplo.com", null));
        Map<String, Object> doOutro = envio(envio, "eva@exemplo.com", null);
        doOutro.put("origem", Map.of());
        stubEnvio(outroTenant, envio, doOutro);

        processar(formularioRecebido(tenant, envio, null, null), formularioRecebido(outroTenant, envio, null, null));

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        assertThat(leadsAtivos(outroTenant)).isEqualTo(1);
        Lead doTenant = porEnvio(tenant, envio);
        Lead doOutroTenant = porEnvio(outroTenant, envio);
        assertThat(doOutroTenant.getId()).isNotEqualTo(doTenant.getId());
        assertThat(doOutroTenant.getTenantId()).isEqualTo(outroTenant);
        assertThat(doTenant.getUtmSource()).isEqualTo("google");
        assertThat(doOutroTenant.getUtmSource()).as("cada tenant leu o seu envio").isNull();
        assertThat(doTipo(outroTenant, doOutroTenant.getId(), TipoEventoLead.LEADS_UNIDOS)).isEmpty();
    }

    @Test
    void capturaDoChatbotDepoisDoEnvioNaoRebaixaAEtapa() throws Exception {
        UUID envio = UUID.randomUUID();
        stubEnvio(tenant, envio, envio(envio, "fabio@exemplo.com", null));
        processar(formularioRecebido(tenant, envio, null, null));
        UUID lead = porEnvio(tenant, envio).getId();

        processar(capturaDoChatbot(tenant, UUID.randomUUID(), "FABIO@exemplo.com"));

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead depois = porId(tenant, lead);
        assertThat(depois.getEtapa()).isEqualTo(3);
        assertThat(depois.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(doTipo(tenant, lead, TipoEventoLead.ETAPA_ALTERADA)).singleElement()
                .satisfies(item -> assertThat(item.getEtapaNova()).isEqualTo(3));
    }

    @Test
    void leadCriadoAntesPelaE6EhCompletadoPontuadoEComVendedor() throws Exception {
        UUID envio = UUID.randomUUID();
        UUID empresa = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        Lead daE6 = gravar(tenant, () -> leadDaOportunidade(envio, empresa, contato, oportunidade));
        TenantContexto.executar(tenant, () -> eventos.save(new LeadEvento(daE6.getId(), TipoEventoLead.HANDOFF_CRM,
                Map.of("oportunidadeId", oportunidade.toString()))));
        stubEnvio(tenant, envio, envio(envio, "gil@exemplo.com", "62977776666"));

        processar(formularioRecebido(tenant, envio, empresa, contato));

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getId()).isEqualTo(daE6.getId());
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getNome()).isEqualTo("Ana Souza");
        assertThat(lead.getEmail()).isEqualTo("gil@exemplo.com");
        assertThat(lead.getTelefone()).isEqualTo("5562977776666");
        assertThat(lead.getUtmCampaign()).isEqualTo("black-friday-2026");
        assertThat(lead.getDadosFormulario()).hasSize(5);
        assertThat(lead.getEnvioLidoEm()).isNotNull();
        assertThat(lead.isConsentimentoMarketing()).isTrue();
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(lead.getCrmOportunidadeId()).as("não mexe na oportunidade da E6").isEqualTo(oportunidade);
        assertThat(consentimentosDe(lead)).singleElement()
                .satisfies(aceite -> assertThat(aceite.getTextoTermo()).isEqualTo(TEXTO_TERMO));
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.ETAPA_ALTERADA)).as("já estava na etapa 3").hasSize(1);
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.RESPOSTAS_LIDAS)).hasSize(1);
    }

    /** Lead como a E6 cria quando {@code crm.oportunidade.criada} chega antes do envio. */
    static Lead leadDaOportunidade(UUID envio, UUID empresa, UUID contato, UUID oportunidade) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
        lead.vincularEnvio(envio, null, null);
        lead.vincularCrm(empresa, contato);
        lead.vincularOportunidade(oportunidade);
        return lead;
    }
}
