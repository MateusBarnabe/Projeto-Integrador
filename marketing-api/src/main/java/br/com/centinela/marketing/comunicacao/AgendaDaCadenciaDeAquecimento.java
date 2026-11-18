package br.com.centinela.marketing.comunicacao;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.2 (cadência de aquecimento, E9) uma vez por dia, às 9h de Brasília por padrão
 * ({@code marketing.comunicacao.aquecimento.cron}). Por horário, e não por intervalo, para não rodar a
 * cada subida da aplicação nem deixar de rodar quando ela reinicia antes de completar 24 h.
 * {@code marketing.comunicacao.aquecimento.habilitada=false} desliga só o agendamento; a rotina
 * continua disponível em {@link CadenciaDeAquecimento}.
 */
@Component
@ConditionalOnProperty(name = "marketing.comunicacao.aquecimento.habilitada", havingValue = "true",
        matchIfMissing = true)
class AgendaDaCadenciaDeAquecimento {

    private final CadenciaDeAquecimento aquecimento;

    AgendaDaCadenciaDeAquecimento(CadenciaDeAquecimento aquecimento) {
        this.aquecimento = aquecimento;
    }

    @Scheduled(cron = "${marketing.comunicacao.aquecimento.cron:0 0 9 * * *}", zone = "America/Sao_Paulo")
    void executar() {
        aquecimento.executarCadencia();
    }
}
