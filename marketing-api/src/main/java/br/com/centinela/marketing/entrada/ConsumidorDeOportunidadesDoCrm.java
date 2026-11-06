package br.com.centinela.marketing.entrada;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.compartilhado.mensageria.ConsumoDeEventos;
import br.com.centinela.marketing.compartilhado.mensageria.EventoRecebido;
import br.com.centinela.marketing.compartilhado.mensageria.TopologiaDeConsumo;

/**
 * Consumidor da fila {@code marketing.crm-oportunidades} (norteador §8, Consumo): {@code crm.oportunidade.criada}
 * vai para {@link OportunidadeDoCrm} (E6). Envelope, remetente ({@code user_id = mq_crm}), tenant,
 * idempotência e transação são do {@link ConsumoDeEventos}.
 * {@code marketing.consumo.crm-oportunidades.auto-startup=false} desliga o listener (testes que leem a fila
 * diretamente).
 */
@Component
public class ConsumidorDeOportunidadesDoCrm {

    public static final String MODULO_ORIGEM = "crm";

    private static final Logger LOG = LoggerFactory.getLogger(ConsumidorDeOportunidadesDoCrm.class);

    private final ConsumoDeEventos consumo;
    private final OportunidadeDoCrm oportunidade;

    public ConsumidorDeOportunidadesDoCrm(ConsumoDeEventos consumo, OportunidadeDoCrm oportunidade) {
        this.consumo = consumo;
        this.oportunidade = oportunidade;
    }

    @RabbitListener(id = "marketing.crm-oportunidades", queues = TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES,
            autoStartup = "${marketing.consumo.crm-oportunidades.auto-startup:true}")
    public void receber(Message mensagem) {
        consumo.consumir(mensagem, MODULO_ORIGEM, this::despachar);
    }

    /** Ponto de despacho por {@code tipo}. Roda no tenant do envelope e dentro da transação do consumo. */
    void despachar(EventoRecebido evento) {
        switch (evento.tipo()) {
            case TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA -> oportunidade.processar(evento);
            // A fila só é ligada à chave acima; um tipo novo do CRM não trava a fila
            default -> LOG.warn("Tipo {} sem consumidor em {}; ignorado (id {})", evento.tipo(),
                    TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES, evento.id());
        }
    }
}
