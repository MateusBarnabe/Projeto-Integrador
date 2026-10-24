package br.com.centinela.marketing.qualificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QualificacaoTest {

    @Test
    void padraoSegueAsRegrasDeNegocio() {
        QualificacaoConfiguracao padrao = QualificacaoConfiguracao.padrao();

        assertThat(padrao.getLimiteMqlQuente()).isEqualTo(70);
        assertThat(padrao.getLimiteMqlMorno()).isEqualTo(40);
        assertThat(padrao.isRodizioAtivo()).isTrue();
        assertThat(padrao.getHorarioComercialInicio()).isEqualTo(LocalTime.of(8, 0));
        assertThat(padrao.getHorarioComercialFim()).isEqualTo(LocalTime.of(18, 0));
        assertThat(padrao.getDiasAtendimento()).isEqualTo(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        assertThat(padrao.getMinutosTransbordo()).isEqualTo(15);
    }

    @Test
    void configuracaoRecusaValoresIncoerentes() {
        QualificacaoConfiguracao configuracao = QualificacaoConfiguracao.padrao();

        assertThatThrownBy(() -> configuracao.definirLimites(40, 40)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracao.definirAtendimento(LocalTime.of(18, 0), LocalTime.of(8, 0),
                Set.of(DayOfWeek.MONDAY))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracao.definirAtendimento(LocalTime.of(8, 0), LocalTime.of(18, 0),
                Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> configuracao.definirRodizio(true, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regraDeCampoPrecisaDaChaveEDeValores() {
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", " ", List.of("Decisor"), 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", "cargo", List.of(), 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", "cargo", List.of("Decisor"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void regraDeUtmNaoGuardaChaveDeCampo() {
        RegraPontuacao regra = RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("source", "google", "medium", "cpc")), 20);

        regra.alterar("Origem do tráfego", FonteRegra.UTM, "cargo", List.of(Map.of("direto", true)), 10);

        assertThat(regra.getCampoChave()).isNull();
        assertThat(regra.getPontos()).isEqualTo(10);
    }
}
