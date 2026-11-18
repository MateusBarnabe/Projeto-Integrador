package br.com.centinela.marketing.compartilhado.teste;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Deixa consumindo as filas só os {@code @RabbitListener} do contexto da classe de teste atual.
 *
 * <p>Toda classe com {@code @MockitoBean} ou {@code @AutoConfigureMockMvc} ganha um contexto próprio,
 * e o Spring mantém os contextos em cache com os consumidores ligados, todos no mesmo broker. Sem
 * isso, o evento publicado por um teste pode ser consumido por outro contexto enquanto o marcador de
 * {@code processar()} é consumido pelo do teste, que lê o banco antes da hora. Ao começar uma classe,
 * os consumidores dos outros contextos param, e os que este contexto tinha parado voltam.
 */
public class ConsumidoresDoContextoAtual implements TestExecutionListener {

    /** Consumidores que estavam ligados quando o contexto deixou de ser o atual. */
    private static final Map<ApplicationContext, List<MessageListenerContainer>> PARADOS = new IdentityHashMap<>();

    private static ApplicationContext atual;

    @Override
    public void beforeTestClass(TestContext testContext) {
        ApplicationContext contexto = testContext.getApplicationContext();
        synchronized (PARADOS) {
            if (contexto == atual) {
                return;
            }
            if (atual != null) {
                pararConsumidores(atual);
            }
            List<MessageListenerContainer> parados = PARADOS.remove(contexto);
            if (parados != null) {
                parados.forEach(MessageListenerContainer::start);
            }
            atual = contexto;
        }
    }

    private static void pararConsumidores(ApplicationContext contexto) {
        if (contexto instanceof ConfigurableApplicationContext configuravel && !configuravel.isActive()) {
            return; // fechado por @DirtiesContext
        }
        RabbitListenerEndpointRegistry registro = contexto.getBeanProvider(RabbitListenerEndpointRegistry.class)
                .getIfAvailable();
        if (registro == null) {
            return;
        }
        List<MessageListenerContainer> ligados = new ArrayList<>();
        for (MessageListenerContainer consumidor : registro.getListenerContainers()) {
            if (consumidor.isRunning()) {
                consumidor.stop();
                ligados.add(consumidor);
            }
        }
        PARADOS.put(contexto, ligados);
    }
}
