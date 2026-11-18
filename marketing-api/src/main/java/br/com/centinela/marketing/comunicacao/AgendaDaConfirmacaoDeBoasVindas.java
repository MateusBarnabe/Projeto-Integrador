package br.com.centinela.marketing.comunicacao;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendamento do job 4.5 (confirmação de boas-vindas, E8): a cada minuto por padrão. */
@Component
class AgendaDaConfirmacaoDeBoasVindas {

    private final ConfirmacaoDeBoasVindas boasVindas;

    AgendaDaConfirmacaoDeBoasVindas(ConfirmacaoDeBoasVindas boasVindas) {
        this.boasVindas = boasVindas;
    }

    @Scheduled(fixedDelayString = "${marketing.comunicacao.boas-vindas.intervalo:PT1M}")
    void executar() {
        boasVindas.enviarPendentes();
    }
}
