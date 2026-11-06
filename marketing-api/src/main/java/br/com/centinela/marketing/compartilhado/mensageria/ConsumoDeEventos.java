package br.com.centinela.marketing.compartilhado.mensageria;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Base de todo consumidor de evento de outro módulo (Contrato §9.7). O {@code @RabbitListener}
 * recebe a {@link Message} crua e entrega aqui, com o efeito:
 *
 * <pre>{@code
 * @RabbitListener(queues = TopologiaDeConsumo.FILA_LANDING_LEADS)
 * void receber(Message mensagem) {
 *     consumo.consumir(mensagem, "landing", evento -> switch (evento.tipo()) { ... });
 * }
 * }</pre>
 *
 * O que esta classe garante:
 * <ul>
 * <li>envelope completo, {@code moduloOrigem} igual ao esperado e propriedade AMQP {@code user_id}
 * igual a {@code mq_<moduloOrigem>}; senão, {@link EventoInvalidoException}, que vai direto para a DLQ;</li>
 * <li>tenant do envelope no {@link TenantContexto}, definido antes de abrir a transação (o Hibernate
 * fixa o tenant ao abrir a sessão);</li>
 * <li>{@code correlacaoId} no MDC, para o log e para o que o efeito publicar;</li>
 * <li>idempotência: o id vai para {@code eventos_processados} na mesma transação do efeito. Id já
 * visto não roda o efeito; falha no efeito desfaz o registro e a mensagem é tentada de novo.</li>
 * </ul>
 *
 * Exceção do efeito vira nova tentativa (até 3, depois a DLQ). Para mandar direto para a DLQ, lance
 * {@link EventoInvalidoException} ou {@link org.springframework.amqp.AmqpRejectAndDontRequeueException}.
 * A mensagem da exceção vai para o log: nada de dado pessoal nela.
 */
@Component
public class ConsumoDeEventos {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumoDeEventos.class);

    private final ObjectMapper json;
    private final TransactionTemplate transacao;
    private final EventosProcessados eventosProcessados;

    ConsumoDeEventos(ObjectMapper json, TransactionTemplate transacao, EventosProcessados eventosProcessados) {
        this.json = json;
        this.transacao = transacao;
        this.eventosProcessados = eventosProcessados;
    }

    /**
     * Lê e confere o envelope e roda o efeito uma vez só por id, no tenant do evento e numa transação.
     *
     * @param moduloOrigem módulo que publica na exchange ligada à fila ({@code landing}, {@code crm})
     * @return {@code true} se o efeito rodou; {@code false} se o evento já tinha sido processado
     */
    public boolean consumir(Message mensagem, String moduloOrigem, Consumer<EventoRecebido> efeito) {
        Objects.requireNonNull(efeito, "efeito");
        EventoRecebido evento = ler(mensagem, moduloOrigem);

        String correlacaoAnterior = MDC.get(RequestIdFilter.CHAVE_MDC);
        if (evento.correlacaoId() != null && !evento.correlacaoId().isBlank()) {
            MDC.put(RequestIdFilter.CHAVE_MDC, evento.correlacaoId());
        }
        try {
            Boolean processado = TenantContexto.calcular(evento.tenantId(), () -> transacao.execute(status -> {
                if (!eventosProcessados.registrar(evento.id(), evento.tipo())) {
                    return false;
                }
                efeito.accept(evento);
                return true;
            }));
            if (Boolean.TRUE.equals(processado)) {
                LOG.info("Evento {} processado (id {}, tenant {})", evento.tipo(), evento.id(), evento.tenantId());
                return true;
            }
            LOG.info("Evento {} já processado; ignorado (id {}, tenant {})", evento.tipo(), evento.id(),
                    evento.tenantId());
            return false;
        } finally {
            if (correlacaoAnterior == null) {
                MDC.remove(RequestIdFilter.CHAVE_MDC);
            } else {
                MDC.put(RequestIdFilter.CHAVE_MDC, correlacaoAnterior);
            }
        }
    }

    /**
     * Converte {@code dados} para o formato do consumidor. Fora do formato, {@link EventoInvalidoException}
     * (direto para a DLQ), com o caminho do campo e sem o valor.
     */
    public <T> T dados(EventoRecebido evento, Class<T> formato) {
        try {
            return json.treeToValue(evento.dados(), formato);
        } catch (IOException | IllegalArgumentException falha) {
            throw new EventoInvalidoException("dados de " + evento.tipo() + " (id " + evento.id()
                    + ") fora do formato" + caminho(falha));
        }
    }

    /** Lê o envelope da mensagem e confere o §9.7 e o remetente. */
    EventoRecebido ler(Message mensagem, String moduloOrigem) {
        byte[] corpo = mensagem.getBody();
        if (corpo == null || corpo.length == 0) {
            throw new EventoInvalidoException("mensagem sem corpo");
        }
        EventoRecebido evento;
        try {
            evento = json.readValue(corpo, EventoRecebido.class);
        } catch (IOException falha) {
            // Sem a causa: a mensagem do Jackson pode citar o valor lido.
            throw new EventoInvalidoException("envelope ilegível" + caminho(falha));
        }
        if (evento == null) {
            throw new EventoInvalidoException("envelope vazio");
        }

        exigir(evento.id() != null, "envelope sem id");
        exigir(evento.tipo() != null && !evento.tipo().isBlank(), "envelope sem tipo (id " + evento.id() + ")");
        exigir(evento.versao() >= 1, "envelope sem versao (id " + evento.id() + ")");
        exigir(evento.tenantId() != null, "envelope sem tenantId (id " + evento.id() + ")");
        exigir(evento.ocorridoEm() != null, "envelope sem ocorridoEm (id " + evento.id() + ")");
        exigir(evento.dados() != null && !evento.dados().isNull(), "envelope sem dados (id " + evento.id() + ")");
        exigir(moduloOrigem.equals(evento.moduloOrigem()), "moduloOrigem " + evento.moduloOrigem()
                + " numa fila de " + moduloOrigem + " (id " + evento.id() + ")");
        exigir(evento.tipo().startsWith(moduloOrigem + "."), "tipo " + evento.tipo() + " não é de "
                + moduloOrigem + " (id " + evento.id() + ")");

        // O RabbitMQ garante que user_id é o usuário da conexão; aqui se confere que é o do módulo de origem.
        String remetente = mensagem.getMessageProperties().getReceivedUserId();
        exigir(("mq_" + moduloOrigem).equals(remetente), "user_id " + remetente + " não confere com moduloOrigem "
                + moduloOrigem + " (id " + evento.id() + ")");
        return evento;
    }

    private static void exigir(boolean condicao, String motivo) {
        if (!condicao) {
            throw new EventoInvalidoException(motivo);
        }
    }

    /** Caminho do campo com problema, só com nomes de campo e índices. */
    private static String caminho(Exception falha) {
        if (falha instanceof JsonMappingException mapeamento && !mapeamento.getPath().isEmpty()) {
            return ": " + mapeamento.getPath().stream()
                    .map(r -> r.getFieldName() != null ? r.getFieldName() : "[" + r.getIndex() + "]")
                    .collect(Collectors.joining("."));
        }
        return "";
    }
}
