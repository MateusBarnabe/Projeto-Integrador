package br.com.centinela.marketing.rodizio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.mensageria.PublicadorDeEventos;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.teste.IntegracaoDeTeste;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.VendedorRodizio;
import br.com.centinela.marketing.qualificacao.VendedorRodizioRepository;
import br.com.centinela.marketing.rodizio.ResultadoAtribuicao.Situacao;

/**
 * Rodízio (Q2) contra PostgreSQL e RabbitMQ reais: alternância entre 2 vendedores pelo que recebeu
 * há mais tempo, fora do horário e job 4.4, nutrição fora do rodízio, concorrência do job,
 * reatribuição manual e lead de outro tenant.
 *
 * <p>O horário é controlado pelos dias de atendimento do tenant: "aberto" é todo dia, das 00:00 às
 * 23:59:59; "fechado" é todo dia menos hoje, no fuso do módulo.
 */
class RodizioDeVendedoresTest extends IntegracaoDeTeste {

    @Autowired
    RodizioDeVendedores rodizio;

    @Autowired
    AtribuicaoPendente atribuicaoPendente;

    @Autowired
    HorarioDeAtendimento horario;

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    VendedorRodizioRepository vendedores;

    @Autowired
    QualificacaoConfiguracaoRepository configuracoes;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    WebApplicationContext contexto;

    private final UUID tenant = UUID.randomUUID();
    private MockMvc mvc;
    private CachingConnectionFactory conexaoAdmin;
    private RabbitTemplate observador;
    private Queue fila;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        conexaoAdmin = conexaoRabbitAdmin();
        RabbitAdmin admin = new RabbitAdmin(conexaoAdmin);
        fila = new AnonymousQueue();
        admin.declareQueue(fila);
        admin.declareBinding(BindingBuilder.bind(fila)
                .to(new TopicExchange(PublicadorDeEventos.EXCHANGE_PLATAFORMA)).with("identity.notificacao.criar"));
        observador = new RabbitTemplate(conexaoAdmin);
        configurar(true, EnumSet.allOf(DayOfWeek.class));
    }

    @AfterEach
    void fechar() {
        conexaoAdmin.destroy();
    }

    @Test
    void tresLeadsMqlEDoisVendedoresAlternamPeloQueRecebeuHaMaisTempo() throws Exception {
        UUID nunca = UUID.randomUUID();
        UUID ontem = UUID.randomUUID();
        UUID inativo = UUID.randomUUID();
        cadastrarVendedor(nunca, null, true);
        cadastrarVendedor(ontem, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1), true);
        cadastrarVendedor(inativo, null, false);

        Tentativa primeiro = criarEAtribuir(1, FaixaQualificacao.MQL_QUENTE, 85);
        Tentativa segundo = criarEAtribuir(1, FaixaQualificacao.MQL_MORNO, 50);
        Tentativa terceiro = criarEAtribuir(1, FaixaQualificacao.MQL_QUENTE, 90);

        assertThat(List.of(primeiro, segundo, terceiro)).extracting(Tentativa::situacao)
                .containsOnly(Situacao.ATRIBUIDO);
        assertThat(List.of(primeiro, segundo, terceiro)).extracting(Tentativa::vendedorId)
                .as("nunca recebeu vem primeiro, depois o de ontem, e volta").containsExactly(nunca, ontem, nunca);

        UUID leadDoSegundo = segundo.lead();
        Lead lido = noTenant(() -> leads.findById(leadDoSegundo)).orElseThrow();
        assertThat(lido.getVendedorId()).isEqualTo(ontem);
        assertThat(lido.getVendedorAtribuidoEm()).isNotNull();
        assertThat(noTenant(() -> vendedores.findByVendedorId(ontem)).orElseThrow().getUltimaAtribuicaoEm())
                .isCloseTo(OffsetDateTime.now(ZoneOffset.UTC), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        assertThat(noTenant(() -> vendedores.findByVendedorId(inativo)).orElseThrow().getUltimaAtribuicaoEm()).isNull();

        LeadEvento evento = vendedorAtribuido(leadDoSegundo).getFirst();
        assertThat(evento.getPayload()).containsEntry("vendedorId", ontem.toString())
                .containsEntry("forma", "rodizio").containsEntry("faixa", "mql_morno")
                .containsEntry("vendedorAnteriorId", null);

        JsonNode notificacao = receber(env -> env.at("/dados/rota").asText().equals("/leads/" + leadDoSegundo));
        assertThat(notificacao.at("/dados/usuarioId").asText()).isEqualTo(ontem.toString());
        assertThat(notificacao.at("/dados/categoria").asText()).isEqualTo("NOVO_LEAD");
        assertThat(notificacao.at("/dados/titulo").asText()).as("morno na etapa 1").isEqualTo("Novo lead");
        UUID leadDoTerceiro = terceiro.lead();
        assertThat(receber(env -> env.at("/dados/rota").asText().equals("/leads/" + leadDoTerceiro))
                .at("/dados/titulo").asText()).as("quente").isEqualTo("Novo lead prioritário");
    }

    @Test
    void leadComVendedorNaoVoltaParaORodizio() {
        UUID vendedor = UUID.randomUUID();
        UUID outro = UUID.randomUUID();
        cadastrarVendedor(vendedor, null, true);
        cadastrarVendedor(outro, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1), true);
        UUID lead = criarEAtribuir(3, FaixaQualificacao.MQL_QUENTE, 80).lead();

        ResultadoAtribuicao denovo = noTenant(() -> transacao.execute(s ->
                rodizio.atribuir(leads.findById(lead).orElseThrow())));

        assertThat(denovo.situacao()).isEqualTo(Situacao.JA_TINHA_VENDEDOR);
        assertThat(denovo.vendedorId()).isEqualTo(vendedor);
        assertThat(vendedorAtribuido(lead)).hasSize(1);
    }

    @Test
    void horarioComercialEhGravadoComoHoraLocalSemDeslocarPeloFusoDaJvm() {
        noTenant(() -> transacao.execute(s -> {
            QualificacaoConfiguracao configuracao = configuracoes.findFirstBy().orElseThrow();
            configuracao.definirAtendimento(LocalTime.of(8, 0), LocalTime.of(18, 0), EnumSet.of(DayOfWeek.MONDAY));
            return configuracoes.save(configuracao);
        }));

        assertThat(jdbc.queryForObject("SELECT horario_comercial_inicio || '-' || horario_comercial_fim"
                + " FROM marketing.qualificacao_configuracoes WHERE tenant_id = ?", String.class, tenant))
                .isEqualTo("08:00:00-18:00:00");
        assertThat(noTenant(() -> configuracoes.findFirstBy()).orElseThrow().getHorarioComercialInicio())
                .isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void nutricaoESemContatoNaoEntramNoRodizio() {
        UUID vendedor = UUID.randomUUID();
        cadastrarVendedor(vendedor, null, true);

        Tentativa nutricao = criarEAtribuir(3, FaixaQualificacao.NUTRICAO, 30);
        Lead doMetaSemContato = new Lead(OrigemLead.META_ADS, 0);
        doMetaSemContato.vincularMetaLead("leadgen-" + UUID.randomUUID());
        doMetaSemContato.registrarPontuacao(90, FaixaQualificacao.MQL_QUENTE, List.of(),
                OffsetDateTime.now(ZoneOffset.UTC));
        Tentativa semContato = criarEAtribuir(tenant, doMetaSemContato);

        assertThat(nutricao.situacao()).isEqualTo(Situacao.NAO_ELEGIVEL);
        assertThat(semContato.situacao()).isEqualTo(Situacao.NAO_ELEGIVEL);
        atribuicaoPendente.atribuirPendentes();
        assertThat(vendedorGravado(nutricao.lead())).as("o job também não pega nutrição").isNull();
        assertThat(vendedorGravado(semContato.lead())).isNull();
        assertThat(noTenant(() -> vendedores.findByVendedorId(vendedor)).orElseThrow().getUltimaAtribuicaoEm()).isNull();
    }

    @Test
    void foraDoHorarioFicaAguardandoEOJobAtribuiNoHorarioDeAtendimento() throws Exception {
        UUID vendedor = UUID.randomUUID();
        cadastrarVendedor(vendedor, null, true);
        DayOfWeek hoje = ZonedDateTime.now(horario.fuso()).getDayOfWeek();
        EnumSet<DayOfWeek> semHoje = EnumSet.complementOf(EnumSet.of(hoje));
        configurar(true, semHoje);

        Tentativa fora = criarEAtribuir(3, FaixaQualificacao.MQL_QUENTE, 80);
        UUID lead = fora.lead();

        assertThat(fora.situacao()).isEqualTo(Situacao.AGUARDANDO_FORA_DO_HORARIO);
        assertThat(fora.resultado().aguardandoVendedor()).isTrue();
        assertThat(RodizioDeVendedores.aguardandoVendedor(noTenant(() -> leads.findById(lead)).orElseThrow())).isTrue();
        atribuicaoPendente.atribuirPendentes();
        assertThat(vendedorGravado(lead)).as("ainda fora do horário").isNull();
        assertThat(receberOuNulo(env -> env.at("/dados/rota").asText().equals("/leads/" + lead), 1_000)).isNull();

        configurar(true, EnumSet.allOf(DayOfWeek.class));
        atribuicaoPendente.atribuirPendentes();

        assertThat(vendedorGravado(lead)).isEqualTo(vendedor);
        assertThat(vendedorAtribuido(lead)).singleElement()
                .satisfies(evento -> assertThat(evento.getPayload()).containsEntry("forma", "rodizio"));
        JsonNode notificacao = receber(env -> env.at("/dados/rota").asText().equals("/leads/" + lead));
        assertThat(notificacao.at("/dados/usuarioId").asText()).isEqualTo(vendedor.toString());
        assertThat(notificacao.at("/dados/titulo").asText()).isEqualTo("Novo lead prioritário");
        assertThat(notificacao.get("usuarioId").isNull()).as("rotina, sem usuário").isTrue();
    }

    @Test
    void rodizioDesligadoOuSemVendedorAtivoFicaAguardando() {
        configurar(false, EnumSet.allOf(DayOfWeek.class));
        cadastrarVendedor(UUID.randomUUID(), null, true);

        Tentativa desligado = criarEAtribuir(3, FaixaQualificacao.MQL_MORNO, 50);
        atribuicaoPendente.atribuirPendentes();

        assertThat(desligado.situacao()).isEqualTo(Situacao.AGUARDANDO_RODIZIO_DESLIGADO);
        assertThat(vendedorGravado(desligado.lead())).as("o job respeita o rodízio desligado").isNull();

        UUID semEscala = UUID.randomUUID();
        configurar(semEscala, true, EnumSet.allOf(DayOfWeek.class));
        TenantContexto.executar(semEscala, () -> vendedores.save(inativo(UUID.randomUUID())));
        Tentativa semVendedor = criarEAtribuir(semEscala, novoLead(3, FaixaQualificacao.MQL_QUENTE, 80));

        assertThat(semVendedor.situacao()).isEqualTo(Situacao.AGUARDANDO_SEM_VENDEDOR);
        atribuicaoPendente.atribuirPendentes();
        assertThat(vendedorGravado(semVendedor.lead())).isNull();
    }

    @Test
    void jobsSimultaneosAtribuemCadaLeadUmaVezSo() throws Exception {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        cadastrarVendedor(primeiro, null, true);
        cadastrarVendedor(segundo, null, true);
        configurar(false, EnumSet.allOf(DayOfWeek.class));
        List<UUID> pendentes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pendentes.add(criarEAtribuir(3, FaixaQualificacao.MQL_MORNO, 50).lead());
        }
        configurar(true, EnumSet.allOf(DayOfWeek.class));

        CountDownLatch largada = new CountDownLatch(1);
        CompletableFuture<?> a = CompletableFuture.runAsync(() -> esperarERodar(largada));
        CompletableFuture<?> b = CompletableFuture.runAsync(() -> esperarERodar(largada));
        largada.countDown();
        CompletableFuture.allOf(a, b).join();

        for (UUID lead : pendentes) {
            assertThat(vendedorGravado(lead)).isIn(primeiro, segundo);
            assertThat(vendedorAtribuido(lead)).as("um registro por lead").hasSize(1);
        }
        assertThat(pendentes.stream().map(this::vendedorGravado).filter(primeiro::equals).count())
                .as("os dois vendedores recebem").isBetween(1L, 5L);
    }

    // ---- PUT /api/marketing/leads/{id}/vendedor --------------------------------------------------

    @Test
    void reatribuicaoManualTrocaOVendedorRegistraENotifica() throws Exception {
        UUID atual = UUID.randomUUID();
        UUID novo = UUID.randomUUID();
        UUID gestora = UUID.randomUUID();
        cadastrarVendedor(atual, null, true);
        cadastrarVendedor(novo, OffsetDateTime.now(ZoneOffset.UTC), false);
        UUID lead = criarEAtribuir(3, FaixaQualificacao.MQL_QUENTE, 80).lead();
        assertThat(vendedorGravado(lead)).isEqualTo(atual);

        mvc.perform(reatribuir(lead, novo, tenant, gestora, "marketing.qualificacao.editar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.leadId").value(lead.toString()))
                .andExpect(jsonPath("$.data.vendedorId").value(novo.toString()))
                .andExpect(jsonPath("$.data.vendedorAtribuidoEm").isNotEmpty())
                .andExpect(jsonPath("$.errors").isEmpty());

        assertThat(vendedorGravado(lead)).isEqualTo(novo);
        LeadEvento manual = vendedorAtribuido(lead).getLast();
        assertThat(manual.getPayload()).containsEntry("forma", "manual")
                .containsEntry("vendedorId", novo.toString()).containsEntry("vendedorAnteriorId", atual.toString());
        assertThat(manual.getCriadoPor()).as("created_by é a gestora").isEqualTo(gestora);
        JsonNode notificacao = receber(env -> env.at("/dados/usuarioId").asText().equals(novo.toString()));
        assertThat(notificacao.at("/dados/rota").asText()).isEqualTo("/leads/" + lead);
        assertThat(notificacao.get("usuarioId").asText()).isEqualTo(gestora.toString());

        mvc.perform(reatribuir(lead, novo, tenant, gestora, "marketing.qualificacao.editar"))
                .andExpect(status().isOk());
        assertThat(vendedorAtribuido(lead)).as("mesmo vendedor não gera registro").hasSize(2);
    }

    @Test
    void leadDeOutroTenantResponde404() throws Exception {
        UUID vendedor = UUID.randomUUID();
        cadastrarVendedor(vendedor, null, true);
        UUID lead = criarEAtribuir(3, FaixaQualificacao.MQL_QUENTE, 80).lead();
        UUID outroTenant = UUID.randomUUID();
        TenantContexto.executar(outroTenant, () -> vendedores.save(new VendedorRodizio(vendedor)));

        mvc.perform(reatribuir(lead, vendedor, outroTenant, UUID.randomUUID(), "marketing.qualificacao.editar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("Lead não encontrado."));
        mvc.perform(reatribuir(UUID.randomUUID(), vendedor, tenant, UUID.randomUUID(), "marketing.qualificacao.editar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reatribuicaoExigePermissaoVendedorDaEscalaECorpoValido() throws Exception {
        UUID vendedor = UUID.randomUUID();
        cadastrarVendedor(vendedor, null, true);
        UUID lead = criarEAtribuir(3, FaixaQualificacao.MQL_QUENTE, 80).lead();

        mvc.perform(reatribuir(lead, vendedor, tenant, UUID.randomUUID(), "marketing.qualificacao.ver"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(reatribuir(lead, UUID.randomUUID(), tenant, UUID.randomUUID(), "marketing.qualificacao.editar"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].campo").value("vendedorId"))
                .andExpect(jsonPath("$.errors[0].codigo").value("VENDEDOR_FORA_DA_ESCALA"));
        mvc.perform(put("/api/marketing/leads/{id}/vendedor", lead)
                        .with(token(tenant, UUID.randomUUID(), "marketing.qualificacao.editar"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("vendedorId"));
    }

    // ---------------------------------------------------------------------------------------------

    private void configurar(boolean rodizioAtivo, Set<DayOfWeek> dias) {
        configurar(tenant, rodizioAtivo, dias);
    }

    private void configurar(UUID tenantDaConfiguracao, boolean rodizioAtivo, Set<DayOfWeek> dias) {
        TenantContexto.calcular(tenantDaConfiguracao, () -> transacao.execute(s -> {
            QualificacaoConfiguracao configuracao = configuracoes.findFirstBy()
                    .orElseGet(QualificacaoConfiguracao::padrao);
            configuracao.definirAtendimento(LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59), dias);
            configuracao.definirRodizio(rodizioAtivo, 15);
            return configuracoes.save(configuracao);
        }));
    }

    private void cadastrarVendedor(UUID vendedorId, OffsetDateTime ultimaAtribuicao, boolean ativo) {
        noTenant(() -> {
            VendedorRodizio vendedor = new VendedorRodizio(vendedorId);
            if (ultimaAtribuicao != null) {
                vendedor.registrarAtribuicao(ultimaAtribuicao);
            }
            if (!ativo) {
                vendedor.desativar();
            }
            return vendedores.save(vendedor);
        });
    }

    private static VendedorRodizio inativo(UUID vendedorId) {
        VendedorRodizio vendedor = new VendedorRodizio(vendedorId);
        vendedor.desativar();
        return vendedor;
    }

    private static Lead novoLead(int etapa, FaixaQualificacao faixa, int pontos) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, etapa);
        lead.atualizarContato("Ana Souza", "ana." + UUID.randomUUID() + "@exemplo.com", null, null);
        lead.registrarPontuacao(pontos, faixa, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
        return lead;
    }

    /** Lead criado pelo teste e o que o rodízio fez com ele. */
    record Tentativa(UUID lead, ResultadoAtribuicao resultado) {
        Situacao situacao() {
            return resultado.situacao();
        }

        UUID vendedorId() {
            return resultado.vendedorId();
        }
    }

    /** Cria o lead e chama o rodízio na mesma transação, como a E5: o job não vê o lead antes do commit. */
    private Tentativa criarEAtribuir(int etapa, FaixaQualificacao faixa, int pontos) {
        return criarEAtribuir(tenant, novoLead(etapa, faixa, pontos));
    }

    private Tentativa criarEAtribuir(UUID tenantDoLead, Lead novo) {
        return TenantContexto.calcular(tenantDoLead, () -> transacao.execute(s -> {
            Lead lead = leads.save(novo);
            return new Tentativa(lead.getId(), rodizio.atribuir(lead));
        }));
    }

    private UUID vendedorGravado(UUID leadId) {
        return jdbc.queryForObject("SELECT vendedor_id FROM marketing.leads WHERE id = ?", UUID.class, leadId);
    }

    private List<LeadEvento> vendedorAtribuido(UUID leadId) {
        UUID dono = jdbc.queryForObject("SELECT tenant_id FROM marketing.leads WHERE id = ?", UUID.class, leadId);
        return TenantContexto.calcular(dono, () -> eventos.findByLeadIdOrderBySequenciaAsc(leadId)).stream()
                .filter(evento -> evento.getTipoEvento() == TipoEventoLead.VENDEDOR_ATRIBUIDO)
                .toList();
    }

    private void esperarERodar(CountDownLatch largada) {
        try {
            largada.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        atribuicaoPendente.atribuirPendentes();
    }

    private <T> T noTenant(Supplier<T> acao) {
        return TenantContexto.calcular(tenant, acao);
    }

    private MockHttpServletRequestBuilder reatribuir(UUID lead, UUID vendedor, UUID tenantDoToken, UUID usuario,
            String permissao) {
        return put("/api/marketing/leads/{id}/vendedor", lead)
                .with(token(tenantDoToken, usuario, permissao))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"vendedorId\":\"" + vendedor + "\"}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID tenantDoToken,
            UUID usuario, String permissao) {
        return jwt().jwt(j -> j.subject(usuario.toString()).claim("tenant_id", tenantDoToken.toString()))
                .authorities(new SimpleGrantedAuthority(permissao));
    }

    private JsonNode receber(Predicate<JsonNode> filtro) {
        JsonNode mensagem = receberOuNulo(filtro, 5_000);
        assertThat(mensagem).as("notificação em identity.entrada").isNotNull();
        return mensagem;
    }

    /** {@code basicGet} em laço: um consumidor cancelado apagaria a fila auto-delete. */
    private JsonNode receberOuNulo(Predicate<JsonNode> filtro, long esperaMs) {
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
                JsonNode envelope = json.readTree(mensagem.getBody());
                if (filtro.test(envelope)) {
                    return envelope;
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }
}
