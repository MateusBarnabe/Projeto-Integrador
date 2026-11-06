package br.com.centinela.marketing.rodizio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;

/**
 * Horário comercial do rodízio (§9.3, {@code qualificacao_configuracoes}): dias de atendimento e
 * janela {@code [inicio, fim)}, no fuso do tenant. Nenhum contrato da plataforma informa o fuso de um
 * tenant, então vale um fuso único do módulo ({@code marketing.rodizio.fuso-horario}, padrão
 * {@code America/Sao_Paulo}).
 */
@Component
public class HorarioDeAtendimento {

    private final ZoneId fuso;
    private final Clock relogio;

    @Autowired
    HorarioDeAtendimento(@Value("${marketing.rodizio.fuso-horario:America/Sao_Paulo}") String fuso) {
        this(ZoneId.of(fuso), Clock.systemUTC());
    }

    HorarioDeAtendimento(ZoneId fuso, Clock relogio) {
        this.fuso = fuso;
        this.relogio = relogio;
    }

    /** O rodízio está em horário de atendimento agora? */
    public boolean aberto(QualificacaoConfiguracao configuracao) {
        return aberto(configuracao, Instant.now(relogio), fuso);
    }

    public ZoneId fuso() {
        return fuso;
    }

    static boolean aberto(QualificacaoConfiguracao configuracao, Instant momento, ZoneId fuso) {
        ZonedDateTime local = momento.atZone(fuso);
        if (!configuracao.getDiasAtendimento().contains(local.getDayOfWeek())) {
            return false;
        }
        LocalTime hora = local.toLocalTime();
        return !hora.isBefore(configuracao.getHorarioComercialInicio())
                && hora.isBefore(configuracao.getHorarioComercialFim());
    }
}
