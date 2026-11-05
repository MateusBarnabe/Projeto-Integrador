package br.com.centinela.marketing.compartilhado.mensageria;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.centinela.marketing.compartilhado.seguranca.ContextoSeguranca;
import br.com.centinela.marketing.compartilhado.seguranca.UsuarioAtual;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Ponto único para o domínio publicar mensagens. Monta o envelope a partir do contexto (tenant,
 * usuário e {@code X-Request-Id}) e entrega para {@link EnvioDeEventos}, que só envia depois do
 * commit. Por isso precisa ser chamado dentro de uma transação: se ela fizer rollback, nada sai.
 */
@Component
public class PublicadorDeEventos {

    /** Exchange (topic) dos eventos do módulo; a routing key é o tipo do evento. */
    public static final String EXCHANGE_EVENTOS = "marketing.eventos";

    /** Exchange dos pedidos à plataforma, como notificação e timeline. */
    public static final String EXCHANGE_PLATAFORMA = "identity.entrada";

    private final ApplicationEventPublisher publicadorSpring;

    public PublicadorDeEventos(ApplicationEventPublisher publicadorSpring) {
        this.publicadorSpring = publicadorSpring;
    }

    /** Publica um fato do Marketing em {@code marketing.eventos} (ex.: {@code marketing.formulario.publicado}). */
    public Evento publicar(String tipo, int versao, Object dados) {
        return enfileirar(EXCHANGE_EVENTOS, tipo, versao, dados);
    }

    /** Envia um pedido à plataforma em {@code identity.entrada} (ex.: {@code identity.notificacao.criar}). */
    public Evento pedirAPlataforma(String tipo, int versao, Object dados) {
        return enfileirar(EXCHANGE_PLATAFORMA, tipo, versao, dados);
    }

    private Evento enfileirar(String exchange, String tipo, int versao, Object dados) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Eventos só podem ser publicados dentro de uma transação, para sair depois do commit.");
        }
        if (tipo == null || !tipo.matches("[a-z]+(\\.[a-z-]+){2,}")) {
            throw new IllegalArgumentException("Tipo de evento fora do padrão modulo.entidade.acao: " + tipo);
        }
        if (versao < 1) {
            throw new IllegalArgumentException("A versão do evento começa em 1.");
        }

        Evento evento = new Evento(
                UUID.randomUUID(),
                tipo,
                versao,
                TenantContexto.exigir(),
                Evento.MODULO_ORIGEM,
                OffsetDateTime.now(ZoneOffset.UTC),
                ContextoSeguranca.usuarioAtual().map(UsuarioAtual::usuarioId).orElse(null),
                MDC.get(RequestIdFilter.CHAVE_MDC),
                Objects.requireNonNull(dados, "dados"));
        publicadorSpring.publishEvent(new EventoPendente(exchange, evento));
        return evento;
    }

    /** Mensagem aguardando o commit da transação em que foi publicada. */
    record EventoPendente(String exchange, Evento evento) {
    }
}
