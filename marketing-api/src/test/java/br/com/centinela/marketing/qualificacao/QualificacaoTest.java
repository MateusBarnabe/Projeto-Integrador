package br.com.centinela.marketing.qualificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    void regraDeCampoPrecisaDoFormularioDoCampoEDeValores() {
        UUID formulario = UUID.randomUUID();
        UUID campo = UUID.randomUUID();

        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", null, campo, List.of("Decisor"), 30))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", formulario, null, List.of("Decisor"), 30))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", formulario, campo, List.of(), 30))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampo("Cargo", formulario, campo, List.of("Decisor"), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RegraPontuacao.porCampoMeta("Cargo", " ", List.of("Decisor"), 30))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trocarAFonteLimpaOAlvoAnterior() {
        RegraPontuacao regra = RegraPontuacao.porCampo("Cargo", UUID.randomUUID(), UUID.randomUUID(),
                List.of("Decisor"), 30);

        regra.apontarParaUtm();
        regra.alterar("Origem do tráfego", List.of(Map.of("direto", true)), 10);

        assertThat(regra.getFonte()).isEqualTo(FonteRegra.UTM);
        assertThat(regra.getFormularioId()).isNull();
        assertThat(regra.getCampoId()).isNull();
        assertThat(regra.getPontos()).isEqualTo(10);

        regra.apontarParaCampoMeta(" qual_o_seu_cargo ");

        assertThat(regra.getFonte()).isEqualTo(FonteRegra.CAMPO_META);
        assertThat(regra.getCampoMeta()).isEqualTo("qual_o_seu_cargo");
    }
}
