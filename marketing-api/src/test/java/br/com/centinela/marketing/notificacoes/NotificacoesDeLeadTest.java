package br.com.centinela.marketing.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos;
import br.com.centinela.marketing.compartilhado.plataforma.PedidosAPlataforma;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * Pedidos de N1 em {@code identity.entrada}, contra o RabbitMQ com as permissões da plataforma:
 * formato de {@code identity.asyncapi.yaml} (0.3.0), {@code user_id = mq_marketing}, sem dado
 * pessoal e nenhum pedido quando falta o destinatário ou a empresa.
 */
class NotificacoesDeLeadTest extends IntegracaoDeTeste {

    @Autowired
    NotificacoesDeLead notificacoes;

    @Autowired
    LeadRepository leads;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    private final UUID tenant = UUID.randomUUID();
    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate observador;
    private Queue fila;

    @BeforeEach
    void observarIdentityEntrada() {
        conexaoAdmin = conexaoRabbitAdmin();
        RabbitAdmin admin = new RabbitAdmin(conexaoAdmin);
        fila = new AnonymousQueue();
        admin.declareQueue(fila);
        admin.declareBinding(BindingBuilder.bind(fila)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_PLATAFORMA)).with("#"));
        observador = new RabbitTemplate(conexaoAdmin);
    }

    @AfterEach
    void fechar() {
        conexaoAdmin.destroy();
    }

    @Test
    void notificaOVendedorComNovoLeadPrioritarioNaEtapa3() throws Exception {
        UUID vendedor = UUID.randomUUID();
        Lead lead = gravarLead(3, FaixaQualificacao.MQL_MORNO, 55, vendedor, null);

        boolean enviado = emTransacao(() -> notificacoes.notificarNovoLead(recarregar(lead)));

        assertThat(enviado).isTrue();
        Message mensagem = receber(env -> env.at("/dados/usuarioId").asText().equals(vendedor.toString()));
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("identity.notificacao.criar");
        assertThat(mensagem.getMessageProperties().getReceivedUserId()).isEqualTo("mq_marketing");

        JsonNode envelope = json.readTree(mensagem.getBody());
        assertThat(envelope.get("tipo").asText()).isEqualTo("identity.notificacao.criar");
        assertThat(envelope.get("versao").asInt()).isEqualTo(1);
        assertThat(envelope.get("tenantId").asText()).isEqualTo(tenant.toString());
        assertThat(envelope.get("moduloOrigem").asText()).isEqualTo("marketing");
        assertThat(envelope.get("usuarioId").isNull()).as("rotina, sem usuário").isTrue();

        JsonNode dados = envelope.get("dados");
        assertThat(fieldNames(dados)).containsExactlyInAnyOrder("usuarioId", "categoria", "titulo", "texto", "rota");
        assertThat(dados.get("categoria").asText()).isEqualTo("NOVO_LEAD");
        assertThat(dados.get("titulo").asText()).isEqualTo("Novo lead prioritário");
        assertThat(dados.get("rota").asText()).isEqualTo("/leads/" + lead.getId());
        assertThat(dados.get("texto").asText())
                .isEqualTo("MQL morno, 55 pontos · enviou o formulário · campanha black-friday");
        assertThat(new String(mensagem.getBody())).as("sem dado pessoal")
                .doesNotContain("Ana Souza", "ana@exemplo.com", "5562999990000");
    }

    @Test
    void mqlQuenteForaDaEtapa3TambemEhPrioritarioEMornoNao() throws Exception {
        UUID vendedorQuente = UUID.randomUUID();
        UUID vendedorMorno = UUID.randomUUID();
        Lead quente = gravarLead(1, FaixaQualificacao.MQL_QUENTE, 80, vendedorQuente, null);
        Lead morno = gravarLead(1, FaixaQualificacao.MQL_MORNO, 45, vendedorMorno, null);

        emTransacao(() -> notificacoes.notificarNovoLead(recarregar(quente)));
        emTransacao(() -> notificacoes.notificarNovoLead(recarregar(morno)));

        JsonNode dadosQuente = json.readTree(receber(env -> env.at("/dados/usuarioId").asText()
                .equals(vendedorQuente.toString())).getBody()).get("dados");
        JsonNode dadosMorno = json.readTree(receber(env -> env.at("/dados/usuarioId").asText()
                .equals(vendedorMorno.toString())).getBody()).get("dados");
        assertThat(dadosQuente.get("titulo").asText()).isEqualTo("Novo lead prioritário");
        assertThat(dadosMorno.get("titulo").asText()).isEqualTo("Novo lead");
        assertThat(dadosMorno.get("texto").asText()).startsWith("MQL morno, 45 pontos · contato pelo chatbot");
    }

    @Test
    void leadSemVendedorNaoNotificaNinguem() {
        Lead lead = gravarLead(3, FaixaQualificacao.MQL_QUENTE, 90, null, null);

        boolean enviado = emTransacao(() -> notificacoes.notificarNovoLead(recarregar(lead)));

        assertThat(enviado).isFalse();
        assertThat(receberOuNulo(env -> env.toString().contains(lead.getId().toString()), 1_500)).isNull();
    }

    @Test
    void registraNaTimelineDaEmpresaSoQuandoHaEmpresa() throws Exception {
        UUID empresa = UUID.randomUUID();
        Lead comEmpresa = gravarLead(3, FaixaQualificacao.MQL_QUENTE, 90, UUID.randomUUID(), empresa);
        Lead semEmpresa = gravarLead(1, FaixaQualificacao.MQL_QUENTE, 90, UUID.randomUUID(), null);

        assertThat(emTransacao(() -> notificacoes.registrarNaTimeline(recarregar(comEmpresa),
                FatoDaTimeline.OPORTUNIDADE_VINCULADA))).isTrue();
        assertThat(emTransacao(() -> notificacoes.registrarNaTimeline(recarregar(semEmpresa),
                FatoDaTimeline.OPORTUNIDADE_VINCULADA))).isFalse();

        Message mensagem = receber(env -> env.at("/dados/empresaId").asText().equals(empresa.toString()));
        assertThat(mensagem.getMessageProperties().getReceivedRoutingKey()).isEqualTo("identity.timeline.registrar");
        assertThat(mensagem.getMessageProperties().getReceivedUserId()).isEqualTo("mq_marketing");
        JsonNode envelope = json.readTree(mensagem.getBody());
        assertThat(envelope.get("tipo").asText()).isEqualTo("identity.timeline.registrar");
        JsonNode dados = envelope.get("dados");
        assertThat(fieldNames(dados)).containsExactlyInAnyOrder("empresaId", "tipoDoFato", "texto", "rota");
        assertThat(dados.get("tipoDoFato").asText()).isEqualTo("marketing.lead.oportunidade_vinculada");
        assertThat(dados.get("texto").asText()).isEqualTo(
                "Lead do Marketing ligado à oportunidade (MQL quente, 90 pontos · enviou o formulário"
                        + " · campanha black-friday)");
        assertThat(dados.get("rota").asText()).isEqualTo("/modulos/marketing/leads/" + comEmpresa.getId());
        assertThat(new String(mensagem.getBody())).doesNotContain("Ana Souza", "ana@exemplo.com");
        assertThat(receberOuNulo(env -> env.toString().contains(semEmpresa.getId().toString()), 1_000)).isNull();
    }

    @Test
    void naoPedeNadaParaLeadDeOutroTenant() {
        Lead lead = gravarLead(3, FaixaQualificacao.MQL_QUENTE, 90, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> TenantContexto.executar(UUID.randomUUID(),
                () -> transacao.executeWithoutResult(s -> notificacoes.notificarNovoLead(lead))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tituloETextoRespeitamOsLimitesDoContrato() {
        PedidosAPlataforma.Notificacao longa = new PedidosAPlataforma.Notificacao(UUID.randomUUID(),
                br.com.centinela.marketing.compartilhado.plataforma.CategoriaNotificacao.NOVO_LEAD,
                "x".repeat(200), "y".repeat(800), "  ");
        assertThat(longa.titulo()).hasSize(120).endsWith("…");
        assertThat(longa.texto()).hasSize(500);
        assertThat(longa.rota()).isNull();
        assertThatThrownBy(() -> new PedidosAPlataforma.RegistroDeTimeline(UUID.randomUUID(), "x", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------------------------------

    private Lead gravarLead(int etapa, FaixaQualificacao faixa, int pontos, UUID vendedor, UUID empresa) {
        Lead salvo = TenantContexto.calcular(tenant, () -> transacao.execute(s -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, etapa);
            lead.atualizarContato("Ana Souza", "ana@exemplo.com", "5562999990000", null);
            if (empresa != null) {
                lead.vincularEnvio(UUID.randomUUID(), UUID.randomUUID(), CanalLead.FORMULARIO);
                lead.vincularCrm(empresa, UUID.randomUUID());
            }
            lead.registrarPontuacao(pontos, faixa, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
            return leads.save(lead);
        }));
        jdbc.update("UPDATE marketing.leads SET utm_campaign = 'black-friday', vendedor_id = ? WHERE id = ?",
                vendedor, salvo.getId());
        return salvo;
    }

    private Lead recarregar(Lead lead) {
        return leads.findById(lead.getId()).orElseThrow();
    }

    private <T> T emTransacao(java.util.function.Supplier<T> acao) {
        return TenantContexto.calcular(tenant, () -> transacao.execute(s -> acao.get()));
    }

    private Message receber(Predicate<JsonNode> filtro) {
        Message mensagem = receberOuNulo(filtro, 5_000);
        assertThat(mensagem).as("pedido em identity.entrada").isNotNull();
        return mensagem;
    }

    /**
     * Outras rotinas também publicam em identity.entrada; fica só com a mensagem deste teste. Usa
     * {@code basicGet} (receive sem timeout): um consumidor cancelado apagaria a fila auto-delete.
     */
    private Message receberOuNulo(Predicate<JsonNode> filtro, long esperaMs) {
        long limite = System.currentTimeMillis() + esperaMs;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = observador.receive(fila.getName());
            if (mensagem == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
                continue;
            }
            try {
                if (filtro.test(json.readTree(mensagem.getBody()))) {
                    return mensagem;
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    private static List<String> fieldNames(JsonNode no) {
        List<String> nomes = new java.util.ArrayList<>();
        no.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }
}
