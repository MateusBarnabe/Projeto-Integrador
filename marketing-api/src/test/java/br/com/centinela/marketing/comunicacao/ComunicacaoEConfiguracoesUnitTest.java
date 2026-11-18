package br.com.centinela.marketing.comunicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.centinela.marketing.comunicacao.ClienteSmtp.MensagemSmtp;
import br.com.centinela.marketing.configuracoes.CanalAnuncio;
import br.com.centinela.marketing.configuracoes.CanalAnuncioTipo;
import br.com.centinela.marketing.configuracoes.CifradorDeSegredo;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.StatusAquecimento;

/** Testes de unidade da D3, D6, E7, E8, E9 e E10 (sem banco nem Docker). */
class ComunicacaoEConfiguracoesUnitTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 16, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void cifradorDeSegredoCifraComIvAleatorioEDecifraCorretamente() {
        CifradorDeSegredo cifrador = new CifradorDeSegredo("chave-teste-marketing-2026");

        String cifrado1 = cifrador.cifrar("segredo-do-meta");
        String cifrado2 = cifrador.cifrar("segredo-do-meta");

        assertThat(cifrado1).isNotBlank().isNotEqualTo("segredo-do-meta").isNotEqualTo(cifrado2);
        assertThat(cifrador.decifrar(cifrado1)).isEqualTo("segredo-do-meta");
        assertThat(cifrador.decifrar(cifrado2)).isEqualTo("segredo-do-meta");
    }

    @Test
    void cifradorSemChaveNaoUsaChavePadraoERecusaCifrarSegredo() {
        CifradorDeSegredo semChave = new CifradorDeSegredo("  ");

        assertThat(semChave.configurado()).isFalse();
        assertThat(semChave.cifrar(null)).as("nada a cifrar não exige chave").isNull();
        assertThat(semChave.cifrar("")).isNull();
        assertThatThrownBy(() -> semChave.cifrar("segredo-do-meta"))
                .isInstanceOf(CifradorDeSegredo.ChaveDeCifraAusente.class);

        String cifradoComOutraChave = new CifradorDeSegredo("outra-chave").cifrar("segredo-do-meta");
        assertThatThrownBy(() -> semChave.decifrar(cifradoComOutraChave))
                .isInstanceOf(CifradorDeSegredo.ChaveDeCifraAusente.class);
    }

    @Test
    void canalAnuncioGuardaCredenciaisCifradasSoNoMetaAds() {
        CifradorDeSegredo cifrador = new CifradorDeSegredo("chave-teste");
        CanalAnuncio meta = new CanalAnuncio(CanalAnuncioTipo.META_ADS);
        meta.atualizarPreferencias(new BigDecimal("1500.00"), "PMEs", "Leads", true);
        meta.atualizarCredenciaisMeta("page-123", "app-secret-1", "verify-1", "token-1", cifrador);

        assertThat(meta.possuiMetaAppSecret()).isTrue();
        assertThat(meta.possuiMetaVerifyToken()).isTrue();
        assertThat(meta.possuiMetaPageAccessToken()).isTrue();
        assertThat(meta.decifrarMetaAppSecret(cifrador)).isEqualTo("app-secret-1");

        // Atualização com segredos em branco mantém os anteriores
        meta.atualizarCredenciaisMeta("page-123", "", null, "   ", cifrador);
        assertThat(meta.decifrarMetaAppSecret(cifrador)).isEqualTo("app-secret-1");

        CanalAnuncio google = new CanalAnuncio(CanalAnuncioTipo.GOOGLE_ADS);
        assertThatThrownBy(() -> google.atualizarCredenciaisMeta("p", "s", "v", "t", cifrador))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void enviadorDeEmailSubstituiVariaveisEIncluiLinkDeDescadastro() {
        List<MensagemSmtp> enviadas = new ArrayList<>();
        EnviadorDeEmail enviador = new EnviadorDeEmail(enviadas::add, "marketing@empresa.dev",
                "http://localhost:8087/");
        ModeloMensagem modelo = ModeloMensagem.boasVindasPadrao();
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
        lead.atualizarContato("Maria Souza", "maria@empresa.dev", null, null);
        lead.atualizarUtm("google", "cpc", null, null, null);

        EnviadorDeEmail.EmailRenderizado resultado = enviador.enviar(modelo, lead);

        assertThat(resultado.assunto()).contains("Maria Souza");
        assertThat(resultado.corpo())
                .contains("Maria Souza")
                .contains("google")
                .contains("http://localhost:8087/public/marketing/descadastro/" + lead.getTokenDescadastro());
        assertThat(enviadas).singleElement().satisfies(msg -> {
            assertThat(msg.destinatario()).isEqualTo("maria@empresa.dev");
            assertThat(msg.remetente()).isEqualTo("marketing@empresa.dev");
        });
    }

    @Test
    void cicloDeVidaDoLeadDoOptOutEAquecimentoAteAnonimizacao() {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, 1);
        lead.atualizarContato("João", "joao@exemplo.dev", "5562999990000", "5562999990000");
        lead.registrarConsentimento(true, AGORA);
        lead.registrarInteracao(Map.of("tipoDispositivo", "smartphone"), Map.of("tempoNaPaginaSegundos", 60));

        // 1º envio de aquecimento (ainda há próximo modelo e max = 2)
        lead.registrarEnvioAquecimento(AGORA.plusDays(1), 2, true);
        assertThat(lead.getTentativasAquecimento()).isEqualTo(1);
        assertThat(lead.getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);

        // 2º envio atinge max = 2 → esgotado
        lead.registrarEnvioAquecimento(AGORA.plusDays(4), 2, true);
        assertThat(lead.getTentativasAquecimento()).isEqualTo(2);
        assertThat(lead.getStatusAquecimento()).isEqualTo(StatusAquecimento.ESGOTADO);
        assertThat(lead.getEsgotadoEm()).isEqualTo(AGORA.plusDays(4));

        // Opt-out é idempotente
        assertThat(lead.registrarOptOut(AGORA.plusDays(5))).isTrue();
        assertThat(lead.registrarOptOut(AGORA.plusDays(6))).isFalse();
        assertThat(lead.getStatusAquecimento()).isEqualTo(StatusAquecimento.OPT_OUT);
        assertThat(lead.isConsentimentoMarketing()).isFalse();

        // Anonimização zera dados pessoais e preserva etapa/origem
        lead.anonimizar();
        assertThat(lead.isAnonimizado()).isTrue();
        assertThat(lead.getNome()).isNull();
        assertThat(lead.getEmail()).isNull();
        assertThat(lead.getTelefone()).isNull();
        assertThat(lead.getWhatsapp()).isNull();
        assertThat(lead.getTokenDescadastro()).isNull();
        assertThat(lead.getContextoTecnico()).isNull();
        assertThat(lead.getComportamento()).isNull();
        assertThat(lead.getEtapa()).isEqualTo(1);

        LeadConsentimento revogacao = LeadConsentimento.revogacaoPorDescadastro(
                lead.getId() != null ? lead.getId() : java.util.UUID.randomUUID(), AGORA, "203.0.113.1", "Mozilla");
        revogacao.anonimizar();
        assertThat(revogacao.getIp()).isNull();
        assertThat(revogacao.getUserAgent()).isNull();
    }
}
