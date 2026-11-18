package br.com.centinela.marketing.comunicacao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.5 (confirmação de boas-vindas, E8) a cada minuto
 * ({@code marketing.comunicacao.boas-vindas.intervalo}), começando um intervalo depois da subida.
 * {@code marketing.comunicacao.boas-vindas.habilitada=false} desliga só o agendamento; a rotina
 * continua disponível em {@link ConfirmacaoDeBoasVindas}.
 */
@Component
@ConditionalOnProperty(name = "marketing.comunicacao.boas-vindas.habilitada", havingValue = "true",
        matchIfMissing = true)
class AgendaDaConfirmacaoDeBoasVindas {

    private final ConfirmacaoDeBoasVindas boasVindas;

    AgendaDaConfirmacaoDeBoasVindas(ConfirmacaoDeBoasVindas boasVindas) {
        this.boasVindas = boasVindas;
    }

    @Scheduled(fixedDelayString = "${marketing.comunicacao.boas-vindas.intervalo:PT1M}",
            initialDelayString = "${marketing.comunicacao.boas-vindas.intervalo:PT1M}")
    void executar() {
        boasVindas.enviarPendentes();
    }
}
