package br.com.centinela.marketing.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    void consentimentoRepetidoDaEtapa2NaoGeraNovaLinha() {
        UUID lead = UUID.randomUUID();
        LeadConsentimento primeiro = consentimento(lead, true, AGORA, "v1");

        assertThat(consentimento(lead, true, AGORA, "v1").repete(primeiro)).isTrue();
        assertThat(consentimento(lead, false, AGORA, "v1").repete(primeiro)).isFalse();
        assertThat(consentimento(lead, true, AGORA.plusMinutes(1), "v1").repete(primeiro)).isFalse();
        assertThat(consentimento(lead, true, AGORA, "v2").repete(primeiro)).isFalse();
        assertThat(primeiro.repete(null)).isFalse();
    }

    @Test
    void userAgentGrandeEhCortadoNoTamanhoDaColuna() {
        LeadConsentimento revogacao = LeadConsentimento.revogacaoPorDescadastro(
                UUID.randomUUID(), AGORA, "2001:db8::1", "x".repeat(800));

        assertThat(revogacao.getUserAgent()).hasSize(LeadConsentimento.TAMANHO_MAXIMO_USER_AGENT);
        assertThat(revogacao.isAceito()).isFalse();
        assertThat(revogacao.getOrigem()).isEqualTo(OrigemConsentimento.DESCADASTRO);
        assertThat(revogacao.getVersaoTermo()).isNull();
    }

    private static LeadConsentimento consentimento(UUID lead, boolean aceito, OffsetDateTime em, String termo) {
        return LeadConsentimento.doLanding(lead, OrigemConsentimento.FORMULARIO_ATUALIZADO, aceito, em,
                "203.0.113.10", "Mozilla/5.0", termo, UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID());
    }
}
