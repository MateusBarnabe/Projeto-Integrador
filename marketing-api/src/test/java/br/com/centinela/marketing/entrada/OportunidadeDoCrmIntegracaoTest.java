package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos;
import br.com.centinela.marketing.compartilhado.mensageria.TopologiaDeConsumo;
import br.com.centinela.marketing.compartilhado.plataforma.PedidosAPlataforma;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * E6 de ponta a ponta: o "CRM" publica {@code crm.oportunidade.criada} como {@code mq_crm} em
 * {@code crm.eventos}, no formato do {@code crm.asyncapi.yaml} e do {@code crm-stub}, e o consumidor real
 * da fila {@code marketing.crm-oportunidades} liga o lead, grava {@code handoff_crm} e pede o registro na
 * timeline da empresa em {@code identity.entrada}.
 */
@ExtendWith(OutputCaptureExtension.class)
class OportunidadeDoCrmIntegracaoTest extends IntegracaoDeTeste {

    private static final Duration ESPERA = Duration.ofSeconds(15);
    private static final String DLQ = TopologiaDeConsumo.dlq(TopologiaDeConsumo.FILA_CRM_OPORTUNIDADES);

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RabbitTemplate rabbitDaApi;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    private final UUID tenant = UUID.randomUUID();
    private final UUID empresa = UUID.randomUUID();
    private final UUID contato = UUID.randomUUID();

    private CachingConnectionFactory conexaoCrm;
    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate crm;
    private RabbitTemplate observador;
    private Queue pedidosAPlataforma;

    @BeforeEach
    void preparar() {
        rabbitDaApi.execute(canal -> null); // filas declaradas pela API
        conexaoCrm = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        conexaoCrm.setUsername("mq_crm");
        conexaoCrm.setPassword("mq_crm");
        conexaoCrm.setVirtualHost("plataforma");
        crm = new RabbitTemplate(conexaoCrm);
        conexaoAdmin = conexaoRabbitAdmin();
        RabbitAdmin admin = new RabbitAdmin(conexaoAdmin);
        admin.purgeQueue(DLQ, false);
        pedidosAPlataforma = new AnonymousQueue();
        admin.declareQueue(pedidosAPlataforma);
        admin.declareBinding(BindingBuilder.bind(pedidosAPlataforma)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_PLATAFORMA)).with("#"));
        observador = new RabbitTemplate(conexaoAdmin);
    }

    @AfterEach
    void fechar() {
        conexaoCrm.destroy();
        conexaoAdmin.destroy();
    }

    @Test
    void oportunidadeDeLeadComEnvioLigaOLeadRegistraHandoffEPedeATimeline(CapturedOutput saida) throws Exception {
        UUID envio = UUID.randomUUID();
        Lead doEnvio = leadDoEnvio(tenant, envio, contato, null);
        UUID oportunidade = UUID.randomUUID();
        Map<String, Object> envelope = oportunidade(tenant, oportunidade, "landing", envio.toString(), contato);

        processar(envelope);

        Lead lead = recarregar(doEnvio);
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(oportunidade);
        assertThat(lead.getCrmEmpresaId()).isEqualTo(empresa);
        assertThat(lead.getCrmContatoId()).isEqualTo(contato);
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(leadsDoTenant(tenant)).isEqualTo(1);

        assertThat(timelineDe(lead)).extracting(LeadEvento::getTipoEvento).containsExactly(TipoEventoLead.HANDOFF_CRM);
        assertThat(timelineDe(lead).get(0).getPayload())
                .containsEntry("fonte", TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA)
                .containsEntry("eventoId", envelope.get("id"))
                .containsEntry("oportunidadeId", oportunidade.toString())
                .containsEntry("identificadoPor", "envio")
                .containsEntry("responsavelNoCrm", "sem_vendedor")
                .doesNotContainKey("oportunidadeAnteriorId");

        Message pedido = pedidoDaTimeline(empresa);
        assertThat(pedido.getMessageProperties().getReceivedRoutingKey()).isEqualTo(PedidosAPlataforma.TIMELINE_REGISTRAR);
        JsonNode dados = json.readTree(pedido.getBody()).get("dados");
        assertThat(dados.get("tipoDoFato").asText()).isEqualTo("marketing.lead.oportunidade_vinculada");
        assertThat(dados.get("rota").asText()).isEqualTo("/modulos/marketing/leads/" + lead.getId());
        assertThat(json.readTree(pedido.getBody()).get("tenantId").asText()).isEqualTo(tenant.toString());
        assertThat(new String(pedido.getBody())).doesNotContain("joana@exemplo.com");

        assertThat(saida.getAll()).doesNotContain("joana@exemplo.com").doesNotContain("Proposta comercial");
    }

    @Test
    void semEnvioQueBataAchaPeloContatoDoCrmELevaOVendedorPeloPontoDaC1(CapturedOutput saida) throws Exception {
        UUID envioDoLead = UUID.randomUUID();
        UUID vendedor = UUID.randomUUID();
        Lead doEnvio = leadDoEnvio(tenant, envioDoLead, contato, vendedor);
        UUID oportunidade = UUID.randomUUID();

        // origemModuloId fora do formato de envio: só o contato liga
        processar(oportunidade(tenant, oportunidade, "landing", "form-abc123", contato));

        Lead lead = recarregar(doEnvio);
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(oportunidade);
        assertThat(lead.getEnvioId()).as("o envio do lead é da E5").isEqualTo(envioDoLead);
        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        assertThat(timelineDe(lead)).singleElement().satisfies(item -> {
            assertThat(item.getTipoEvento()).isEqualTo(TipoEventoLead.HANDOFF_CRM);
            assertThat(item.getPayload()).containsEntry("identificadoPor", "contato_crm")
                    .containsEntry("responsavelNoCrm", "pendente");
        });
        // Sem acento na comparação: a saída capturada depende da codificação do console
        assertThat(saida.getAll()).containsPattern("Vendedor " + vendedor + " n.{1,3}o levado .* oportunidade "
                + oportunidade + ": .*\\(C1, X2\\)");
    }

    @Test
    void oportunidadeAntesDoEnvioCriaOLeadPendenteNaEtapa3() throws Exception {
        UUID envio = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        Map<String, Object> envelope = oportunidade(tenant, oportunidade, "landing", envio.toString(), contato);

        processar(envelope);

        Lead lead = TenantContexto.calcular(tenant, () -> leads.findByEnvioId(envio)).orElseThrow();
        assertThat(lead.getEtapa()).isEqualTo(3);
        assertThat(lead.getOrigem()).isEqualTo(OrigemLead.LANDING_PAGE);
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(oportunidade);
        assertThat(lead.getCrmEmpresaId()).isEqualTo(empresa);
        assertThat(lead.getCrmContatoId()).isEqualTo(contato);
        assertThat(lead.leituraDoEnvioPendente()).as("o envio completa depois").isTrue();
        assertThat(lead.getCanal()).isNull();
        assertThat(lead.getFormularioId()).isNull();
        assertThat(lead.getEmail()).isNull();
        assertThat(lead.possuiIdentificadorDeContato()).as("contato só com a leitura do envio").isFalse();
        assertThat(lead.getPontuacao()).isNull();
        assertThat(lead.getCriadoPor()).isNull();

        List<LeadEvento> timeline = timelineDe(lead);
        assertThat(timeline).extracting(LeadEvento::getTipoEvento)
                .containsExactly(TipoEventoLead.ETAPA_ALTERADA, TipoEventoLead.HANDOFF_CRM);
        assertThat(timeline.get(0).getEtapaAnterior()).isNull();
        assertThat(timeline.get(0).getEtapaNova()).isEqualTo(3);
        assertThat(timeline.get(0).getPayload()).containsEntry("leadCriado", true);
        assertThat(timeline.get(1).getPayload()).containsEntry("leadCriado", true)
                .doesNotContainKey("identificadoPor");

        assertThat(pedidoDaTimeline(empresa)).isNotNull();

        // O envio da E5 acha o mesmo lead pela chave dele
        assertThat(TenantContexto.calcular(tenant, () -> leads.findFirstByCrmContatoIdOrderByCriadoEmAsc(contato)))
                .get().extracting(Lead::getId).isEqualTo(lead.getId());
    }

    @Test
    void oportunidadeQueNaoVemDoLandingEhIgnoradaSemDlq() throws Exception {
        Lead doEnvio = leadDoEnvio(tenant, UUID.randomUUID(), contato, null);
        Map<String, Object> manual = oportunidade(tenant, UUID.randomUUID(), "manual", null, contato);
        Map<String, Object> semOrigem = oportunidade(tenant, UUID.randomUUID(), null, null, contato);

        processar(manual, semOrigem);

        assertThat(processados(manual)).isEqualTo(1);
        assertThat(processados(semOrigem)).isEqualTo(1);
        assertThat(recarregar(doEnvio).getCrmOportunidadeId()).isNull();
        assertThat(timelineDe(doEnvio)).isEmpty();
        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        assertThat(idsNaDlq()).doesNotContain((String) manual.get("id"), (String) semOrigem.get("id"));
    }

    @Test
    void mesmaMensagemDuasVezesOuAMesmaOportunidadeDeNovoDaoUmEfeito() throws Exception {
        UUID envio = UUID.randomUUID();
        UUID oportunidade = UUID.randomUUID();
        Map<String, Object> envelope = oportunidade(tenant, oportunidade, "landing", envio.toString(), contato);
        Map<String, Object> reenvio = oportunidade(tenant, oportunidade, "landing", envio.toString(), contato);

        processar(envelope, envelope, reenvio);

        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        Lead lead = TenantContexto.calcular(tenant, () -> leads.findByEnvioId(envio)).orElseThrow();
        assertThat(timelineDe(lead)).filteredOn(item -> item.getTipoEvento() == TipoEventoLead.HANDOFF_CRM).hasSize(1);
        assertThat(processados(envelope)).isEqualTo(1);
        assertThat(processados(reenvio)).isEqualTo(1);
    }

    @Test
    void segundaOportunidadeDoMesmoLeadSubstituiAPrimeiraEGuardaAAnterior() throws Exception {
        UUID envio = UUID.randomUUID();
        Lead doEnvio = leadDoEnvio(tenant, envio, contato, null);
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();

        processar(oportunidade(tenant, primeira, "landing", envio.toString(), contato),
                oportunidade(tenant, segunda, "landing", UUID.randomUUID().toString(), contato));

        Lead lead = recarregar(doEnvio);
        assertThat(lead.getCrmOportunidadeId()).isEqualTo(segunda);
        assertThat(leadsDoTenant(tenant)).isEqualTo(1);
        List<LeadEvento> handoffs = timelineDe(lead);
        assertThat(handoffs).extracting(LeadEvento::getTipoEvento)
                .containsExactly(TipoEventoLead.HANDOFF_CRM, TipoEventoLead.HANDOFF_CRM);
        assertThat(handoffs.get(1).getPayload()).containsEntry("oportunidadeId", segunda.toString())
                .containsEntry("oportunidadeAnteriorId", primeira.toString());
    }

    @Test
    void mesmoEnvioEContatoEmOutroTenantNaoTocamNoLeadDesteTenant() throws Exception {
        UUID outroTenant = UUID.randomUUID();
        UUID envio = UUID.randomUUID();
        Lead doTenant = leadDoEnvio(tenant, envio, contato, null);
        UUID oportunidade = UUID.randomUUID();

        processar(oportunidade(outroTenant, oportunidade, "landing", envio.toString(), contato));

        assertThat(recarregar(doTenant).getCrmOportunidadeId()).isNull();
        assertThat(timelineDe(doTenant)).isEmpty();
        assertThat(leadsDoTenant(outroTenant)).isEqualTo(1);
        Lead doOutro = TenantContexto.calcular(outroTenant, () -> leads.findByEnvioId(envio)).orElseThrow();
        assertThat(doOutro.getId()).isNotEqualTo(doTenant.getId());
        assertThat(doOutro.getCrmOportunidadeId()).isEqualTo(oportunidade);
        assertThat(json.readTree(pedidoDaTimeline(empresa).getBody()).get("tenantId").asText())
                .isEqualTo(outroTenant.toString());
    }

    @Test
    void remetenteQueNaoEhOCrmVaiDiretoParaADlq() throws Exception {
        UUID envio = UUID.randomUUID();
        Map<String, Object> envelope = oportunidade(tenant, UUID.randomUUID(), "landing", envio.toString(), contato);
        RabbitTemplate admin = new RabbitTemplate(conexaoAdmin);

        admin.send(TopologiaDeConsumo.EXCHANGE_CRM, TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA,
                mensagem(envelope, "admin"));

        esperarAte(() -> idsNaDlqContem((String) envelope.get("id")));
        assertThat(processados(envelope)).isZero();
        assertThat(leadsDoTenant(tenant)).isZero();
    }

    // --- apoio ---------------------------------------------------------------------------------

    /** Lead que já recebeu o envio (E5): etapa 3, envio lido e os ids do CRM. */
    private Lead leadDoEnvio(UUID tenantDoLead, UUID envio, UUID contatoDoCrm, UUID vendedor) {
        return TenantContexto.calcular(tenantDoLead, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvio(envio, UUID.randomUUID(), CanalLead.FORMULARIO);
            lead.registrarRespostas(List.of(), false, OffsetDateTime.now(ZoneOffset.UTC));
            lead.atualizarContato("Joana", "joana@exemplo.com", null, null);
            lead.vincularCrm(empresa, contatoDoCrm);
            if (vendedor != null) {
                lead.atribuirVendedor(vendedor, OffsetDateTime.now(ZoneOffset.UTC));
            }
            return leads.save(lead);
        });
    }

    /** {@code crm.oportunidade.criada} como o {@code crm-stub} publica. */
    private Map<String, Object> oportunidade(UUID tenantDoEvento, UUID oportunidade, String origem,
            String origemModuloId, UUID contatoDoCrm) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("oportunidadeId", oportunidade.toString());
        dados.put("titulo", "Formulário: Proposta comercial");
        dados.put("empresaId", empresa.toString());
        dados.put("contatoId", contatoDoCrm == null ? null : contatoDoCrm.toString());
        dados.put("funilId", UUID.randomUUID().toString());
        dados.put("etapaId", UUID.randomUUID().toString());
        dados.put("tipo", "nova");
        dados.put("responsavelId", null);
        dados.put("valorImplantacao", null);
        dados.put("valorMrr", null);
        dados.put("origem", origem);
        dados.put("origemModuloId", origemModuloId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("tipo", TopologiaDeConsumo.CRM_OPORTUNIDADE_CRIADA);
        envelope.put("versao", 1);
        envelope.put("tenantId", tenantDoEvento.toString());
        envelope.put("moduloOrigem", "crm");
        envelope.put("ocorridoEm", "2026-11-06T13:02:11.000Z");
        envelope.put("usuarioId", null);
        envelope.put("correlacaoId", UUID.randomUUID().toString());
        envelope.put("dados", dados);
        return envelope;
    }

    /**
     * Publica as mensagens e um marcador (oportunidade de outra origem, noutro tenant) depois delas. Com um
     * consumidor só na fila, marcador processado quer dizer que as anteriores também foram.
     */
    @SafeVarargs
    private void processar(Map<String, Object>... envelopes) throws Exception {
        for (Map<String, Object> envelope : envelopes) {
            publicar(envelope);
        }
        Map<String, Object> marcador = oportunidade(UUID.randomUUID(), UUID.randomUUID(), "marcador", null, null);
        publicar(marcador);
        esperarAte(() -> processados(marcador) == 1);
    }

    private void publicar(Map<String, Object> envelope) throws Exception {
        crm.send(TopologiaDeConsumo.EXCHANGE_CRM, (String) envelope.get("tipo"), mensagem(envelope, "mq_crm"));
    }

    private Message mensagem(Map<String, Object> envelope, String userId) throws Exception {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setUserId(userId);
        propriedades.setMessageId((String) envelope.get("id"));
        return new Message(json.writeValueAsBytes(envelope), propriedades);
    }

    private int processados(Map<String, Object> envelope) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM marketing.eventos_processados WHERE evento_id = ?",
                Integer.class, UUID.fromString((String) envelope.get("id")));
        return total == null ? 0 : total;
    }

    private int leadsDoTenant(UUID tenantDoLead) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.leads WHERE tenant_id = ? AND deleted_at IS NULL", Integer.class,
                tenantDoLead);
        return total == null ? 0 : total;
    }

    private Lead recarregar(Lead lead) {
        return TenantContexto.calcular(lead.getTenantId(), () -> leads.findById(lead.getId())).orElseThrow();
    }

    private List<LeadEvento> timelineDe(Lead lead) {
        return TenantContexto.calcular(lead.getTenantId(), () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId()));
    }

    /** Pedido {@code identity.timeline.registrar} desta empresa; outras rotinas também publicam lá. */
    private Message pedidoDaTimeline(UUID empresaDoPedido) {
        Predicate<JsonNode> filtro = env -> PedidosAPlataforma.TIMELINE_REGISTRAR.equals(env.path("tipo").asText())
                && empresaDoPedido.toString().equals(env.at("/dados/empresaId").asText());
        long limite = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = observador.receive(pedidosAPlataforma.getName());
            if (mensagem == null) {
                dormir(100);
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
        throw new AssertionError("pedido identity.timeline.registrar da empresa " + empresaDoPedido + " não chegou");
    }

    /** Esvazia a DLQ e devolve os ids das mensagens que estavam lá. */
    private List<String> idsNaDlq() throws Exception {
        RabbitTemplate admin = new RabbitTemplate(conexaoAdmin);
        List<String> ids = new ArrayList<>();
        Message morta;
        while ((morta = admin.receive(DLQ, 200)) != null) {
            ids.add(json.readTree(morta.getBody()).path("id").asText());
        }
        return ids;
    }

    private boolean idsNaDlqContem(String id) {
        try {
            return idsNaDlq().contains(id);
        } catch (Exception falha) {
            return false;
        }
    }

    private static void esperarAte(BooleanSupplier condicao) throws InterruptedException {
        long limite = System.nanoTime() + ESPERA.toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("condição não atingida em " + ESPERA);
            }
            Thread.sleep(100);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
