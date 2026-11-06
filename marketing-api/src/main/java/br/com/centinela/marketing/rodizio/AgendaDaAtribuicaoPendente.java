package br.com.centinela.marketing.rodizio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.4 a cada minuto ({@code marketing.rodizio.atribuicao-pendente.intervalo}).
 * {@code marketing.rodizio.atribuicao-pendente.habilitada=false} desliga só o agendamento; a
 * rotina continua disponível em {@link AtribuicaoPendente}.
 */
@Component
@ConditionalOnProperty(name = "marketing.rodizio.atribuicao-pendente.habilitada", havingValue = "true",
        matchIfMissing = true)
class AgendaDaAtribuicaoPendente {

    private final AtribuicaoPendente atribuicao;

    AgendaDaAtribuicaoPendente(AtribuicaoPendente atribuicao) {
        this.atribuicao = atribuicao;
    }

    @Scheduled(fixedDelayString = "${marketing.rodizio.atribuicao-pendente.intervalo:PT1M}",
            initialDelayString = "${marketing.rodizio.atribuicao-pendente.intervalo:PT1M}")
    void executar() {
        atribuicao.atribuirPendentes();
    }
}
