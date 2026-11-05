package br.com.centinela.marketing.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LeadTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void etapaNuncaRebaixa() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 2);

        assertThat(lead.avancarEtapa(1)).isFalse();
        assertThat(lead.avancarEtapa(2)).isFalse();
        assertThat(lead.getEtapa()).isEqualTo(2);

        assertThat(lead.avancarEtapa(3)).isTrue();
        assertThat(lead.getEtapa()).isEqualTo(3);
    }

    @Test
    void etapaForaDoFunilEhRecusada() {
        assertThatThrownBy(() -> new Lead(OrigemLead.MANUAL, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Lead(OrigemLead.MANUAL, 0).avancarEtapa(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contatoVazioNaoApagaOQueJaExiste() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
        lead.atualizarContato("Ana", "ana@exemplo.com", "11999990000", null);

        lead.atualizarContato(" ", null, "", "11988887777");

        assertThat(lead.getNome()).isEqualTo("Ana");
        assertThat(lead.getEmail()).isEqualTo("ana@exemplo.com");
        assertThat(lead.getTelefone()).isEqualTo("11999990000");
        assertThat(lead.getWhatsapp()).isEqualTo("11988887777");
    }

    @Test
    void soViraLeadQuemTemMetaEmailOuTelefone() {
        Lead semContato = new Lead(OrigemLead.LANDING_PAGE, 0);
        semContato.atualizarContato("Sem contato", null, null, "11988887777");
        Lead doMeta = new Lead(OrigemLead.META_ADS, 0);
        doMeta.vincularMetaLead("123456");
        Lead comEmail = new Lead(OrigemLead.LANDING_PAGE, 1);
        comEmail.atualizarContato(null, "ana@exemplo.com", null, null);

        assertThat(semContato.possuiIdentificadorDeContato()).isFalse();
        assertThat(doMeta.possuiIdentificadorDeContato()).isTrue();
        assertThat(comEmail.possuiIdentificadorDeContato()).isTrue();
    }

    @Test
    void cadaLeadGanhaTokenDeDescadastroProprio() {
        Lead um = new Lead(OrigemLead.LANDING_PAGE, 1);
        Lead outro = new Lead(OrigemLead.LANDING_PAGE, 1);

        assertThat(um.getTokenDescadastro()).hasSize(43).isNotEqualTo(outro.getTokenDescadastro());
        assertThat(um.getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);
    }

    @Test
    void novoEnvioDeixaALeituraPendenteAteRegistrarAsRespostas() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
        UUID envio = UUID.randomUUID();
        UUID formulario = UUID.randomUUID();
        assertThat(lead.leituraDoEnvioPendente()).as("sem envio").isFalse();

        lead.vincularEnvio(envio, formulario, CanalLead.EMBED);
        assertThat(lead.leituraDoEnvioPendente()).isTrue();

        lead.registrarRespostas(List.of(resposta("Cargo", "Decisor")), false, AGORA);
        assertThat(lead.leituraDoEnvioPendente()).isFalse();
        assertThat(lead.getEnvioLidoEm()).isEqualTo(AGORA);

        lead.vincularEnvio(envio, null, null);
        assertThat(lead.leituraDoEnvioPendente()).as("mesmo envio de novo").isFalse();
        assertThat(lead.getFormularioId()).isEqualTo(formulario);
        assertThat(lead.getCanal()).isEqualTo(CanalLead.EMBED);

        lead.vincularEnvio(UUID.randomUUID(), formulario, CanalLead.FORMULARIO);
        assertThat(lead.leituraDoEnvioPendente()).as("envio novo").isTrue();
    }

    @Test
    void envioAnonimizadoNoLandingNaoApagaACopiaDasRespostas() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
        lead.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.FORMULARIO);
        lead.registrarRespostas(List.of(resposta("Cargo", "Decisor")), false, AGORA);

        lead.registrarRespostas(List.of(resposta("Cargo", null)), true, AGORA.plusDays(1));

        assertThat(lead.getDadosFormulario()).singleElement()
                .satisfies(resposta -> assertThat(resposta).containsEntry("valor", "Decisor"));
    }

    @Test
    void respostasExigemUmEnvio() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);

        assertThatThrownBy(() -> lead.registrarRespostas(List.of(), false, AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oportunidadeSoContaComoNovaNaPrimeiraVez() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
        UUID oportunidade = UUID.randomUUID();

        assertThat(lead.vincularOportunidade(oportunidade)).isTrue();
        assertThat(lead.vincularOportunidade(oportunidade)).isFalse();
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(oportunidade);
    }

    @Test
    void capturaEEnvioDaMesmaConversaNaoDuplicamOConsentimento() {
        UUID lead = UUID.randomUUID();
        LeadConsentimento daCaptura = consentimento(lead, OrigemConsentimento.CHATBOT_LEAD_CAPTURADO, true, AGORA, null);
        LeadConsentimento doEnvio = consentimento(lead, OrigemConsentimento.FORMULARIO_RECEBIDO, true, AGORA, "Autorizo");

        assertThat(doEnvio.repete(daCaptura)).as("o texto que faltava na captura não é mudança").isFalse();
        assertThat(consentimento(lead, OrigemConsentimento.CHATBOT_LEAD_CAPTURADO, true, AGORA, null)
                .repete(doEnvio)).isTrue();
        assertThat(consentimento(lead, OrigemConsentimento.FORMULARIO_RECEBIDO, true, AGORA, "Autorizo")
                .repete(doEnvio)).isTrue();
        assertThat(consentimento(lead, OrigemConsentimento.FORMULARIO_RECEBIDO, true, AGORA, "Outro texto")
                .repete(doEnvio)).isFalse();
        assertThat(consentimento(lead, OrigemConsentimento.FORMULARIO_RECEBIDO, true, AGORA.plusMinutes(1), "Autorizo")
                .repete(doEnvio)).isFalse();
        assertThat(doEnvio.repete(null)).isFalse();
    }

    @Test
    void consentimentoDoLandingNaoTemIpNemUserAgent() {
        LeadConsentimento doEnvio = consentimento(UUID.randomUUID(), OrigemConsentimento.FORMULARIO_RECEBIDO, true,
                AGORA, "Autorizo");

        assertThat(doEnvio.getIp()).isNull();
        assertThat(doEnvio.getUserAgent()).isNull();
        assertThatThrownBy(() -> consentimento(UUID.randomUUID(), OrigemConsentimento.DESCADASTRO, false, AGORA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void userAgentGrandeEhCortadoNoTamanhoDaColuna() {
        LeadConsentimento revogacao = LeadConsentimento.revogacaoPorDescadastro(
                UUID.randomUUID(), AGORA, "2001:db8::1", "x".repeat(800));

        assertThat(revogacao.getUserAgent()).hasSize(LeadConsentimento.TAMANHO_MAXIMO_USER_AGENT);
        assertThat(revogacao.isAceito()).isFalse();
        assertThat(revogacao.getOrigem()).isEqualTo(OrigemConsentimento.DESCADASTRO);
        assertThat(revogacao.getIp()).isEqualTo("2001:db8::1");
        assertThat(revogacao.getTextoTermo()).isNull();
    }

    private static LeadConsentimento consentimento(UUID lead, OrigemConsentimento origem, boolean aceito,
            OffsetDateTime em, String texto) {
        return LeadConsentimento.doLanding(lead, origem, aceito, em, texto, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
    }

    private static Map<String, Object> resposta(String rotulo, Object valor) {
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("campoId", UUID.randomUUID().toString());
        resposta.put("rotulo", rotulo);
        resposta.put("tipo", "LISTA_OPCOES");
        resposta.put("valor", valor);
        return resposta;
    }
}
