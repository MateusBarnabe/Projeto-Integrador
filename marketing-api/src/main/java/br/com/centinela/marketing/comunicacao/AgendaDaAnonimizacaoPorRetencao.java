package br.com.centinela.marketing.comunicacao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.3 (anonimização por retenção LGPD, E10) uma vez por mês, no dia 1 às 3h de Brasília
 * por padrão ({@code marketing.ciclo-vida.anonimizacao.cron}). Por horário, e não por intervalo: com
 * um intervalo de 30 dias, qualquer reinício antes disso adiaria a anonimização de novo.
 * {@code marketing.ciclo-vida.anonimizacao.habilitada=false} desliga só o agendamento; a rotina
 * continua disponível em {@link AnonimizacaoPorRetencao}.
 */
@Component
@ConditionalOnProperty(name = "marketing.ciclo-vida.anonimizacao.habilitada", havingValue = "true",
        matchIfMissing = true)
class AgendaDaAnonimizacaoPorRetencao {

    private final AnonimizacaoPorRetencao anonimizacao;

    AgendaDaAnonimizacaoPorRetencao(AnonimizacaoPorRetencao anonimizacao) {
        this.anonimizacao = anonimizacao;
    }

    @Scheduled(cron = "${marketing.ciclo-vida.anonimizacao.cron:0 0 3 1 * *}", zone = "America/Sao_Paulo")
    void executar() {
        anonimizacao.anonimizarVencidos();
    }
}
