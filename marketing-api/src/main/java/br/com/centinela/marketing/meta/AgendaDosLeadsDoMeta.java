package br.com.centinela.marketing.meta;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o job 4.1 a cada 5 minutos ({@code marketing.meta.leads.intervalo}).
 * {@code marketing.meta.leads.habilitada=false} desliga só o agendamento; a rotina continua disponível
 * em {@link LeadsDoMeta}.
 */
@Component
@ConditionalOnProperty(name = "marketing.meta.leads.habilitada", havingValue = "true", matchIfMissing = true)
class AgendaDosLeadsDoMeta {

    private final LeadsDoMeta leads;

    AgendaDosLeadsDoMeta(LeadsDoMeta leads) {
        this.leads = leads;
    }

    @Scheduled(fixedDelayString = "${marketing.meta.leads.intervalo:PT5M}",
            initialDelayString = "${marketing.meta.leads.intervalo:PT5M}")
    void executar() {
        leads.processar();
    }
}
