package br.com.centinela.marketing.comunicacao;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendamento do job 4.2 (cadência de aquecimento, E9): diário por padrão. */
@Component
class AgendaDaCadenciaDeAquecimento {

    private final CadenciaDeAquecimento aquecimento;

    AgendaDaCadenciaDeAquecimento(CadenciaDeAquecimento aquecimento) {
        this.aquecimento = aquecimento;
    }

    @Scheduled(fixedDelayString = "${marketing.comunicacao.aquecimento.intervalo:PT24H}")
    void executar() {
        aquecimento.executarCadencia();
    }
}
