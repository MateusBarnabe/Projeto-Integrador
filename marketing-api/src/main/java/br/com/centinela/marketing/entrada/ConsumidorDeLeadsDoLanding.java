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
 * Consumidor da fila {@code marketing.landing-leads} (norteador §8, Consumo). A fila recebe dois tipos do
 * Landing; {@link #despachar} encaminha cada um ao seu fluxo:
 *
 * <ul>
 * <li>{@code landing.chatbot.lead_capturado} → {@link CapturaDoChatbot} (etapa 1, E1);</li>
 * <li>{@code landing.formulario.recebido} → {@link EnvioDoFormulario} (etapa 3, E5).</li>
 * </ul>
 *
 * Envelope, remetente, tenant, idempotência e transação são do {@link ConsumoDeEventos}.
 * {@code marketing.consumo.landing-leads.auto-startup=false} desliga o listener (testes que usam a fila
 * com um consumidor próprio).
 */
@Component
public class ConsumidorDeLeadsDoLanding {

    public static final String MODULO_ORIGEM = "landing";

    private static final Logger LOG = LoggerFactory.getLogger(ConsumidorDeLeadsDoLanding.class);

    private final ConsumoDeEventos consumo;
    private final CapturaDoChatbot capturaDoChatbot;
    private final EnvioDoFormulario envioDoFormulario;

    public ConsumidorDeLeadsDoLanding(ConsumoDeEventos consumo, CapturaDoChatbot capturaDoChatbot,
            EnvioDoFormulario envioDoFormulario) {
        this.consumo = consumo;
        this.capturaDoChatbot = capturaDoChatbot;
        this.envioDoFormulario = envioDoFormulario;
    }

    @RabbitListener(id = "marketing.landing-leads", queues = TopologiaDeConsumo.FILA_LANDING_LEADS,
            autoStartup = "${marketing.consumo.landing-leads.auto-startup:true}")
    public void receber(Message mensagem) {
        consumo.consumir(mensagem, MODULO_ORIGEM, this::despachar);
    }

    /** Ponto de despacho por {@code tipo}. Roda no tenant do envelope e dentro da transação do consumo. */
    void despachar(EventoRecebido evento) {
        switch (evento.tipo()) {
            case TopologiaDeConsumo.LANDING_CHATBOT_LEAD_CAPTURADO -> capturaDoChatbot.processar(evento);
            case TopologiaDeConsumo.LANDING_FORMULARIO_RECEBIDO -> envioDoFormulario.processar(evento);
            // A fila só é ligada às duas chaves acima; um tipo novo do Landing não trava a fila
            default -> LOG.warn("Tipo {} sem consumidor em {}; ignorado (id {})", evento.tipo(),
                    TopologiaDeConsumo.FILA_LANDING_LEADS, evento.id());
        }
    }
}
