package br.com.centinela.marketing.comunicacao;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Agendamento do job 4.3 (anonimização por retenção LGPD, E10): mensal (720 h) por padrão. */
@Component
class AgendaDaAnonimizacaoPorRetencao {

    private final AnonimizacaoPorRetencao anonimizacao;

    AgendaDaAnonimizacaoPorRetencao(AnonimizacaoPorRetencao anonimizacao) {
        this.anonimizacao = anonimizacao;
    }

    @Scheduled(fixedDelayString = "${marketing.ciclo-vida.anonimizacao.intervalo:PT720H}")
    void executar() {
        anonimizacao.anonimizarVencidos();
    }
}
