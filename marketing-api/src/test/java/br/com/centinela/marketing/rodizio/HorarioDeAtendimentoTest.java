package br.com.centinela.marketing.rodizio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;

/** Horário comercial padrão (segunda a sexta, 8h às 18h) no fuso de São Paulo (UTC-3). */
class HorarioDeAtendimentoTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private final QualificacaoConfiguracao padrao = QualificacaoConfiguracao.padrao();

    @Test
    void abertoDentroDaJanelaNumDiaDeAtendimento() {
        // quinta-feira, 05/11/2026, 08:00 em São Paulo
        assertThat(aberto("2026-11-05T11:00:00Z")).isTrue();
        assertThat(aberto("2026-11-05T20:59:59Z")).as("17:59:59").isTrue();
    }

    @Test
    void fechadoAntesDoInicioNoFimEForaDosDias() {
        assertThat(aberto("2026-11-05T10:59:59Z")).as("07:59:59").isFalse();
        assertThat(aberto("2026-11-05T21:00:00Z")).as("18:00, o fim não entra").isFalse();
        assertThat(aberto("2026-11-07T14:00:00Z")).as("sábado").isFalse();
        assertThat(aberto("2026-11-08T14:00:00Z")).as("domingo").isFalse();
    }

    @Test
    void usaOFusoDoModuloENaoOUtc() {
        // 01:00 UTC de sexta ainda é 22:00 de quinta em São Paulo
        assertThat(HorarioDeAtendimento.aberto(padrao, Instant.parse("2026-11-06T01:00:00Z"), SAO_PAULO)).isFalse();

        QualificacaoConfiguracao noturno = QualificacaoConfiguracao.padrao();
        noturno.definirAtendimento(LocalTime.of(20, 0), LocalTime.of(23, 0), EnumSet.of(DayOfWeek.THURSDAY));
        assertThat(HorarioDeAtendimento.aberto(noturno, Instant.parse("2026-11-06T01:00:00Z"), SAO_PAULO)).isTrue();
    }

    private boolean aberto(String instante) {
        return HorarioDeAtendimento.aberto(padrao, Instant.parse(instante), SAO_PAULO);
    }
}
