package br.com.centinela.marketing.entrada;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.6 a cada minuto ({@code marketing.entrada.leitura-pendente.intervalo}).
 * {@code marketing.entrada.leitura-pendente.habilitada=false} desliga só o agendamento; a rotina
 * continua disponível em {@link LeituraPendenteDoEnvio}.
 */
@Component
@ConditionalOnProperty(name = "marketing.entrada.leitura-pendente.habilitada", havingValue = "true",
        matchIfMissing = true)
class AgendaDaLeituraPendente {

    private final LeituraPendenteDoEnvio leitura;

    AgendaDaLeituraPendente(LeituraPendenteDoEnvio leitura) {
        this.leitura = leitura;
    }

    @Scheduled(fixedDelayString = "${marketing.entrada.leitura-pendente.intervalo:PT1M}",
            initialDelayString = "${marketing.entrada.leitura-pendente.intervalo:PT1M}")
    void executar() {
        leitura.lerPendentes();
    }
}
