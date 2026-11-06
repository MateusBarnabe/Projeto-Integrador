package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.centinela.marketing.compartilhado.teste.ModulosSimulados;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Job 4.6 (E11) contra PostgreSQL, RabbitMQ e o Landing no WireMock: completa o lead que ficou com a
 * leitura pendente, com o mesmo fluxo da chegada, desiste depois de 10 falhas seguidas e pula o lead
 * que outra transação está processando. O agendamento continua ligado; os testes chamam a rotina
 * direto e não dependem de quantas vezes ela rodou sozinha.
 */
class LeituraPendenteDoEnvioIntegracaoTest extends EnvioDoLandingTeste {

    @Autowired
    LeituraPendenteDoEnvio job;

    @Test
    void landingForaDoArDeixaPendenteEOJobCompleta() throws Exception {
        UUID envio = UUID.randomUUID();
        stubLandingForaDoAr(envio);
        processar(formularioRecebido(tenant, envio, UUID.randomUUID(), UUID.randomUUID()));
        assertThat(porEnvio(tenant, envio).leituraDoEnvioPendente()).isTrue();

        ModulosSimulados.LANDING.resetMappings();
        stubEnvio(tenant, envio, envio(envio, "hugo@exemplo.com", "62966665555"));
        job.lerPendentes();

        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.leituraDoEnvioPendente()).isFalse();
        assertThat(lead.getLeituraEnvioFalhas()).as("zera na leitura").isZero();
        assertThat(lead.getEmail()).isEqualTo("hugo@exemplo.com");
        assertThat(lead.getTelefone()).isEqualTo("5562966665555");
        assertThat(lead.getDadosFormulario()).hasSize(5);
        assertThat(lead.getUtmSource()).isEqualTo("google");
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getFaixaQualificacao()).isEqualTo(FaixaQualificacao.MQL_QUENTE);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(consentimentosDe(lead)).singleElement().satisfies(aceite -> {
            assertThat(aceite.getOrigem()).isEqualTo(OrigemConsentimento.FORMULARIO_RECEBIDO);
            assertThat(aceite.getTextoTermo()).isEqualTo(TEXTO_TERMO);
            assertThat(aceite.getEventoId()).as("rotina, sem evento").isNull();
        });
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.RESPOSTAS_LIDAS)).singleElement()
                .satisfies(item -> assertThat(item.getPayload())
                        .containsEntry("fonte", LeituraPendenteDoEnvio.FONTE));
        assertThat(notificacao(env -> env.at("/dados/rota").asText().equals("/leads/" + lead.getId()), 10_000))
                .isNotNull();

        int leituras = leiturasDoEnvio(envio);
        job.lerPendentes();
        assertThat(leiturasDoEnvio(envio)).as("lido não volta ao job").isEqualTo(leituras);
    }

    @Test
    void dezFalhasSeguidasDesistemSemDadoPessoal() throws Exception {
        UUID envio = UUID.randomUUID();
        Lead pendente = gravar(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvioDoFormulario(envio, formulario, CanalLead.FORMULARIO);
            lead.atualizarContato("Iara", "iara@exemplo.com", null, null);
            return lead;
        });
        stubLandingForaDoAr(envio);

        for (int vez = 0; vez < 15 && porId(tenant, pendente.getId()).getLeituraEnvioDesistidaEm() == null; vez++) {
            job.lerPendentes();
        }

        Lead lead = porId(tenant, pendente.getId());
        assertThat(lead.getLeituraEnvioFalhas()).isEqualTo(LeituraDoEnvio.MAXIMO_DE_FALHAS);
        assertThat(lead.getLeituraEnvioDesistidaEm()).isNotNull();
        assertThat(lead.leituraDoEnvioAbandonada()).isTrue();
        assertThat(lead.getEnvioLidoEm()).isNull();
        assertThat(lead.getPontuacao()).as("pontua com o que tem").isNotNull();
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.RESPOSTAS_INDISPONIVEIS)).singleElement()
                .satisfies(item -> assertThat(item.getPayload()).containsEntry("envioId", envio.toString())
                        .containsEntry("falhas", 10).containsEntry("motivo", "INDISPONIVEL")
                        .containsEntry("fonte", LeituraPendenteDoEnvio.FONTE));
        assertThat(timelineDe(tenant, lead.getId())).allSatisfy(item -> assertThat(String.valueOf(item.getPayload()))
                .doesNotContain("iara"));
        assertThat(leiturasDoEnvio(envio)).isEqualTo(LeituraDoEnvio.MAXIMO_DE_FALHAS);

        job.lerPendentes();
        assertThat(leiturasDoEnvio(envio)).as("desistiu: não tenta mais").isEqualTo(LeituraDoEnvio.MAXIMO_DE_FALHAS);
    }

    @Test
    void leadTravadoPorOutraTransacaoEhPuladoELidoDepois() throws Exception {
        UUID envio = UUID.randomUUID();
        Lead pendente = gravar(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvioDoFormulario(envio, formulario, CanalLead.EMBED);
            return lead;
        });
        stubEnvio(tenant, envio, envio(envio, "joao@exemplo.com", null));
        CountDownLatch travado = new CountDownLatch(1);
        CountDownLatch liberar = new CountDownLatch(1);
        CompletableFuture<Void> outra = CompletableFuture.runAsync(() -> TenantContexto.executar(tenant,
                () -> transacao.executeWithoutResult(s -> {
                    jdbc.queryForList("SELECT id FROM marketing.leads WHERE id = ? FOR UPDATE", UUID.class,
                            pendente.getId());
                    travado.countDown();
                    esperar(liberar);
                })));
        assertThat(travado.await(10, TimeUnit.SECONDS)).isTrue();

        try {
            job.lerPendentes();
            assertThat(leiturasDoEnvio(envio)).as("SKIP LOCKED").isZero();
            assertThat(porId(tenant, pendente.getId()).leituraDoEnvioPendente()).isTrue();
        } finally {
            liberar.countDown();
            outra.get(10, TimeUnit.SECONDS);
        }

        job.lerPendentes();
        Lead lido = porId(tenant, pendente.getId());
        assertThat(lido.leituraDoEnvioPendente()).isFalse();
        assertThat(lido.getEmail()).isEqualTo("joao@exemplo.com");
        assertThat(leiturasDoEnvio(envio)).isEqualTo(1);
    }

    @Test
    void completaOLeadDaOportunidadeSemFormularioNemCanalEJuntaComACapturaDoChatbot() throws Exception {
        UUID sessao = UUID.randomUUID();
        processar(capturaDoChatbot(tenant, sessao, "kaio@exemplo.com"));
        UUID daCaptura = TenantContexto.calcular(tenant, () -> leads.findBySessaoId(sessao)).orElseThrow().getId();
        Thread.sleep(20);
        UUID envio = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        Lead daE6 = gravar(tenant, () -> EnvioDoFormularioIntegracaoTest.leadDaOportunidade(envio, UUID.randomUUID(),
                UUID.randomUUID(), oportunidade));
        stubEnvio(tenant, envio, envio(envio, "KAIO@exemplo.com", null));

        job.lerPendentes();

        assertThat(leadsAtivos(tenant)).isEqualTo(1);
        Lead lead = porEnvio(tenant, envio);
        assertThat(lead.getId()).as("a captura é mais antiga e fica").isEqualTo(daCaptura);
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(oportunidade);
        assertThat(lead.getPontuacao()).isEqualTo(70);
        assertThat(lead.getVendedorId()).isEqualTo(vendedor);
        assertThat(consentimentosDe(lead)).extracting(LeadConsentimento::getOrigem).containsExactlyInAnyOrder(
                OrigemConsentimento.FORMULARIO_RECEBIDO, OrigemConsentimento.CHATBOT_LEAD_CAPTURADO);
        Map<String, Object> unido = jdbc.queryForMap("SELECT deleted_at, envio_id FROM marketing.leads WHERE id = ?",
                daE6.getId());
        assertThat(unido.get("deleted_at")).isNotNull();
        assertThat(unido.get("envio_id")).isNull();
        assertThat(doTipo(tenant, lead.getId(), TipoEventoLead.LEADS_UNIDOS)).extracting(
                item -> item.getPayload().get("leadUnidoId")).isEqualTo(List.of(daE6.getId().toString()));
    }

    private static void esperar(CountDownLatch sinal) {
        try {
            sinal.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
