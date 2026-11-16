package br.com.centinela.marketing.rastreamento;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.7 a cada minuto ({@code marketing.rastreamento.descarte.intervalo}).
 * {@code marketing.rastreamento.descarte.habilitado=false} desliga só o agendamento; a rotina
 * continua disponível em {@link DescarteDeVisitas}.
 */
@Component
@ConditionalOnProperty(name = "marketing.rastreamento.descarte.habilitado", havingValue = "true",
        matchIfMissing = true)
class AgendaDoDescarteDeVisitas {

    private final DescarteDeVisitas descarte;

    AgendaDoDescarteDeVisitas(DescarteDeVisitas descarte) {
        this.descarte = descarte;
    }

    @Scheduled(fixedDelayString = "${marketing.rastreamento.descarte.intervalo:PT1M}",
            initialDelayString = "${marketing.rastreamento.descarte.intervalo:PT1M}")
    void executar() {
        descarte.descartar();
    }
}
