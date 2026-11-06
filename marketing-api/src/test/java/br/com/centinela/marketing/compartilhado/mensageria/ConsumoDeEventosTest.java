package br.com.centinela.marketing.compartilhado.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

/**
 * Leitura do envelope, conferência do remetente e a guarda de idempotência, sem broker nem banco.
 * O caminho completo (fila, DLQ, banco) está em {@code ConsumoDeEventosIntegracaoTest}.
 */
class ConsumoDeEventosTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-00000000000a");

    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    private final EventosProcessados eventosProcessados = mock(EventosProcessados.class);
    private final FakeTransacoes transacoes = new FakeTransacoes();
    private ConsumoDeEventos consumo;

    @BeforeEach
    void preparar() {
        consumo = new ConsumoDeEventos(json, new TransactionTemplate(transacoes), eventosProcessados);
        when(eventosProcessados.registrar(any(), anyString())).thenReturn(true);
    }

    @AfterEach
    void limpar() {
        MDC.clear();
        TenantContexto.limpar();
    }

    @Test
    void rodaOEfeitoNoTenantDoEnvelopeNumaTransacaoComACorrelacaoNoMdc() throws Exception {
        Map<String, Object> envelope = envelope("landing.chatbot.lead_capturado");
        AtomicReference<UUID> tenantNoEfeito = new AtomicReference<>();
        AtomicReference<String> correlacaoNoEfeito = new AtomicReference<>();
        AtomicBoolean emTransacao = new AtomicBoolean();

        boolean processado = consumo.consumir(mensagem(envelope, "mq_landing"), "landing", evento -> {
            tenantNoEfeito.set(TenantContexto.exigir());
            correlacaoNoEfeito.set(MDC.get(RequestIdFilter.CHAVE_MDC));
            emTransacao.set(transacoes.ativa);
            assertThat(evento.dados().get("sessaoId").asText()).isEqualTo("s-1");
        });

        assertThat(processado).isTrue();
        assertThat(tenantNoEfeito.get()).isEqualTo(TENANT);
        assertThat(correlacaoNoEfeito.get()).isEqualTo("req-9");
        assertThat(emTransacao.get()).as("efeito dentro da transação").isTrue();
        assertThat(transacoes.commits).isEqualTo(1);
        verify(eventosProcessados).registrar(UUID.fromString((String) envelope.get("id")),
                "landing.chatbot.lead_capturado");
        assertThat(TenantContexto.atual()).as("tenant limpo depois").isEmpty();
        assertThat(MDC.get(RequestIdFilter.CHAVE_MDC)).as("MDC restaurado").isNull();
    }

    @Test
    void eventoJaProcessadoNaoRodaOEfeito() throws Exception {
        when(eventosProcessados.registrar(any(), anyString())).thenReturn(false);
        AtomicBoolean rodou = new AtomicBoolean();

        boolean processado = consumo.consumir(mensagem(envelope("landing.formulario.recebido"), "mq_landing"),
                "landing", evento -> rodou.set(true));

        assertThat(processado).isFalse();
        assertThat(rodou).isFalse();
    }

    @Test
    void falhaNoEfeitoDesfazATransacaoEPropaga() throws Exception {
        assertThatThrownBy(() -> consumo.consumir(mensagem(envelope("landing.formulario.recebido"), "mq_landing"),
                "landing", evento -> {
                    throw new IllegalStateException("Landing fora do ar");
                })).isInstanceOf(IllegalStateException.class);

        assertThat(transacoes.rollbacks).isEqualTo(1);
        assertThat(transacoes.commits).isZero();
    }

    @Test
    void remetenteQueNaoConfereComOModuloDeOrigemEhInvalido() throws Exception {
        Message deOutro = mensagem(envelope("landing.formulario.recebido"), "mq_crm");
        Message semUserId = mensagem(envelope("landing.formulario.recebido"), null);

        assertThatThrownBy(() -> consumo.consumir(deOutro, "landing", evento -> { }))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("user_id");
        assertThatThrownBy(() -> consumo.consumir(semUserId, "landing", evento -> { }))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("user_id");
    }

    @Test
    void moduloOuTipoDeOutraOrigemEhInvalido() throws Exception {
        Map<String, Object> crmNaFilaDoLanding = envelope("crm.oportunidade.criada");
        crmNaFilaDoLanding.put("moduloOrigem", "crm");
        Map<String, Object> tipoTrocado = envelope("crm.oportunidade.criada");

        assertThatThrownBy(() -> consumo.consumir(mensagem(crmNaFilaDoLanding, "mq_crm"), "landing", e -> { }))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("moduloOrigem");
        assertThatThrownBy(() -> consumo.consumir(mensagem(tipoTrocado, "mq_landing"), "landing", e -> { }))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("tipo");
    }

    @Test
    void envelopeIncompletoEhInvalidoSemCitarValores() throws Exception {
        for (String campo : new String[] { "id", "tipo", "versao", "tenantId", "ocorridoEm", "dados" }) {
            Map<String, Object> envelope = envelope("landing.formulario.recebido");
            envelope.remove(campo);
            assertThatThrownBy(() -> consumo.consumir(mensagem(envelope, "mq_landing"), "landing", e -> { }))
                    .as(campo).isInstanceOf(EventoInvalidoException.class).hasMessageContaining(campo);
        }

        Map<String, Object> tenantRuim = envelope("landing.formulario.recebido");
        tenantRuim.put("tenantId", "fulano@exemplo.com");
        assertThatThrownBy(() -> consumo.consumir(mensagem(tenantRuim, "mq_landing"), "landing", e -> { }))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("tenantId")
                .hasMessageNotContaining("fulano")
                .hasNoCause();

        Message lixo = new Message("não é json".getBytes(), propriedades("mq_landing"));
        assertThatThrownBy(() -> consumo.consumir(lixo, "landing", e -> { }))
                .isInstanceOf(EventoInvalidoException.class).hasNoCause();
    }

    @Test
    void campoAMaisNoEnvelopeEhIgnorado() throws Exception {
        Map<String, Object> envelope = envelope("landing.formulario.recebido");
        envelope.put("campoNovo", "qualquer");

        assertThat(consumo.consumir(mensagem(envelope, "mq_landing"), "landing", e -> { })).isTrue();
    }

    @Test
    void dadosForaDoFormatoSaoInvalidosComOCaminhoESemOValor() throws Exception {
        AtomicReference<EventoRecebido> recebido = new AtomicReference<>();
        consumo.consumir(mensagem(envelope("landing.chatbot.lead_capturado"), "mq_landing"), "landing",
                recebido::set);

        DadosDeTeste dados = consumo.dados(recebido.get(), DadosDeTeste.class);
        assertThat(dados.sessaoId()).isEqualTo("s-1");
        assertThat(dados.contato().email()).isEqualTo("fulano@exemplo.com");

        assertThatThrownBy(() -> consumo.dados(recebido.get(), DadosComUuid.class))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("sessaoId")
                .hasMessageNotContaining("s-1");
    }

    @Test
    void toStringDoEventoNaoTrazOsDados() throws Exception {
        AtomicReference<EventoRecebido> recebido = new AtomicReference<>();
        consumo.consumir(mensagem(envelope("landing.chatbot.lead_capturado"), "mq_landing"), "landing",
                recebido::set);

        assertThat(recebido.get().toString()).doesNotContain("fulano").contains("landing.chatbot.lead_capturado");
    }

    @Test
    void falhaPermanenteNaoGanhaNovaTentativaEFalhaComumGanhaAteTres() {
        SimpleRetryPolicy politica = FalhasDeConsumo.politica(3);

        assertThat(podeTentarDeNovo(politica, new ListenerExecutionFailedException("x",
                new EventoInvalidoException("envelope sem id")))).isFalse();
        assertThat(podeTentarDeNovo(politica, new ListenerExecutionFailedException("x",
                new AmqpRejectAndDontRequeueException("permanente")))).isFalse();
        assertThat(podeTentarDeNovo(politica, new ListenerExecutionFailedException("x",
                new IllegalStateException("Landing fora do ar")))).isTrue();
        assertThat(politica.getMaxAttempts()).isEqualTo(3);
    }

    private static boolean podeTentarDeNovo(SimpleRetryPolicy politica, Throwable falha) {
        RetryContext contexto = politica.open(RetrySynchronizationManager.getContext());
        politica.registerThrowable(contexto, falha);
        return politica.canRetry(contexto);
    }

    record ContatoDeTeste(String nome, String email) {
    }

    record DadosDeTeste(String sessaoId, ContatoDeTeste contato) {
    }

    record DadosComUuid(UUID sessaoId) {
    }

    private Map<String, Object> envelope(String tipo) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("tipo", tipo);
        envelope.put("versao", 1);
        envelope.put("tenantId", TENANT.toString());
        envelope.put("moduloOrigem", "landing");
        envelope.put("ocorridoEm", "2026-11-05T13:02:11Z");
        envelope.put("usuarioId", null);
        envelope.put("correlacaoId", "req-9");
        envelope.put("dados", Map.of("sessaoId", "s-1",
                "contato", Map.of("nome", "Fulano", "email", "fulano@exemplo.com")));
        return envelope;
    }

    private Message mensagem(Map<String, Object> envelope, String userId) throws Exception {
        return new Message(json.writeValueAsBytes(envelope), propriedades(userId));
    }

    private static MessageProperties propriedades(String userId) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setReceivedUserId(userId);
        return propriedades;
    }

    /** Gerenciador de transação de mentira, só para contar commit e rollback. */
    static class FakeTransacoes extends AbstractPlatformTransactionManager {

        private static final long serialVersionUID = 1L;

        boolean ativa;
        int commits;
        int rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transacao, TransactionDefinition definicao) {
            ativa = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            ativa = false;
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            ativa = false;
            rollbacks++;
        }
    }
}
