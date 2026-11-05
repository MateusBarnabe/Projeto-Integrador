package br.com.centinela.marketing.leads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.centinela.marketing.compartilhado.dados.BancoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/** Confere as migrations V2 e V8 e o mapeamento das entidades de lead contra o PostgreSQL real. */
class LeadRepositoryTest extends BancoDeTeste {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    LeadRepository leads;

    @Autowired
    LeadEventoRepository eventos;

    @Autowired
    LeadConsentimentoRepository consentimentos;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();
    private final UUID outroTenant = UUID.randomUUID();

    @Test
    void gravaLeadComSeteColunasETemperaturaGeradaPelaEtapa() {
        Lead lead = novoLead(UUID.randomUUID(), 2);
        lead.atualizarContato("Ana", "ana@exemplo.com", null, null);

        Lead salvo = TenantContexto.calcular(tenant, () -> leads.save(lead));

        Lead lido = TenantContexto.calcular(tenant, () -> leads.findById(salvo.getId())).orElseThrow();
        assertThat(lido.getTenantId()).isEqualTo(tenant);
        assertThat(lido.getCriadoEm()).isNotNull();
        assertThat(lido.getAtualizadoEm()).isNotNull();
        assertThat(lido.getCriadoPor()).as("gravação sem usuário, como numa rotina").isNull();
        assertThat(lido.getOrigem()).isEqualTo(OrigemLead.LANDING_PAGE);
        assertThat(lido.getTemperatura()).isEqualTo(Temperatura.MEDIO);
        assertThat(lido.getStatusAquecimento()).isEqualTo(StatusAquecimento.ATIVO);
        assertThat(lido.isConsentimentoMarketing()).isFalse();
    }

    @Test
    void temperaturaAcompanhaAEtapaAoAvancar() {
        Lead salvo = TenantContexto.calcular(tenant, () -> leads.save(novoLead(UUID.randomUUID(), 1)));
        assertThat(salvo.getTemperatura()).isEqualTo(Temperatura.FRIO);

        Lead atualizado = TenantContexto.calcular(tenant, () -> {
            Lead lead = leads.findById(salvo.getId()).orElseThrow();
            lead.avancarEtapa(3);
            return leads.saveAndFlush(lead);
        });

        assertThat(atualizado.getTemperatura()).isEqualTo(Temperatura.QUENTE);
    }

    @Test
    void leadDeOutroTenantNaoAparece() {
        UUID sessao = UUID.randomUUID();
        Lead salvo = TenantContexto.calcular(tenant, () -> leads.save(novoLead(sessao, 1)));

        TenantContexto.executar(outroTenant, () -> {
            assertThat(leads.findById(salvo.getId())).isEmpty();
            assertThat(leads.findBySessaoId(sessao)).isEmpty();
        });
        TenantContexto.executar(tenant, () ->
                assertThat(leads.findBySessaoId(sessao)).map(Lead::getId).contains(salvo.getId()));
    }

    @Test
    void sessaoDoChatbotEhUnicaPorTenant() {
        UUID sessao = UUID.randomUUID();
        TenantContexto.executar(tenant, () -> leads.save(novoLead(sessao, 1)));

        TenantContexto.executar(outroTenant, () -> leads.save(novoLead(sessao, 1)));
        assertThatThrownBy(() -> TenantContexto.executar(tenant, () -> leads.saveAndFlush(novoLead(sessao, 2))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void envioEhUnicoPorTenantEGuardaRespostasECanal() {
        UUID envio = UUID.randomUUID();
        UUID formulario = UUID.randomUUID();
        UUID contato = UUID.randomUUID();
        Lead salvo = TenantContexto.calcular(tenant, () -> {
            Lead lead = new Lead(OrigemLead.LANDING_PAGE, 3);
            lead.vincularEnvio(envio, formulario, CanalLead.LANDING_PAGE);
            lead.vincularCrm(UUID.randomUUID(), contato);
            return leads.save(lead);
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM marketing.leads WHERE envio_id IS NOT NULL AND envio_lido_em IS NULL AND id = ?",
                Integer.class, salvo.getId())).as("leitura pendente").isEqualTo(1);

        TenantContexto.executar(tenant, () -> {
            Lead lead = leads.findByEnvioId(envio).orElseThrow();
            lead.registrarRespostas(List.of(Map.of("campoId", UUID.randomUUID().toString(), "rotulo", "Cargo",
                    "tipo", "LISTA_OPCOES", "valor", "Decisor")), false, AGORA);
            leads.saveAndFlush(lead);
        });

        Lead lido = TenantContexto.calcular(tenant, () -> leads.findFirstByCrmContatoIdOrderByCriadoEmAsc(contato))
                .orElseThrow();
        assertThat(lido.getId()).isEqualTo(salvo.getId());
        assertThat(lido.getCanal()).isEqualTo(CanalLead.LANDING_PAGE);
        assertThat(lido.getFormularioId()).isEqualTo(formulario);
        assertThat(lido.getEnvioLidoEm()).isEqualTo(AGORA);
        assertThat(lido.getDadosFormulario()).singleElement()
                .satisfies(resposta -> assertThat(resposta).containsEntry("rotulo", "Cargo"));
        TenantContexto.executar(outroTenant, () -> assertThat(leads.findByEnvioId(envio)).isEmpty());

        assertThatThrownBy(() -> TenantContexto.executar(tenant, () -> {
            Lead repetido = new Lead(OrigemLead.LANDING_PAGE, 3);
            repetido.vincularEnvio(envio, formulario, CanalLead.FORMULARIO);
            leads.saveAndFlush(repetido);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaCanalEOrigemDeConsentimentoForaDoContrato() {
        Lead lead = TenantContexto.calcular(tenant, () -> leads.save(novoLead(UUID.randomUUID(), 1)));

        assertThatThrownBy(() -> jdbc.update("UPDATE marketing.leads SET canal = 'whatsapp' WHERE id = ?",
                lead.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO marketing.lead_consentimentos
                    (id, tenant_id, lead_id, finalidade, aceito, registrado_em, origem, created_at, updated_at)
                VALUES (?, ?, ?, 'marketing', true, now(), 'landing.contato.informado', now(), now())
                """, UUID.randomUUID(), tenant, lead.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO marketing.lead_eventos (id, tenant_id, lead_id, tipo_evento, created_at, updated_at)
                VALUES (?, ?, ?, 'formulario_atualizado', now(), now())
                """, UUID.randomUUID(), tenant, lead.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leadDoMetaEhUnicoPorTenant() {
        TenantContexto.executar(tenant, () -> leads.save(leadDoMeta("leadgen-1")));

        TenantContexto.executar(outroTenant, () -> leads.save(leadDoMeta("leadgen-1")));
        assertThatThrownBy(() -> TenantContexto.executar(tenant, () -> leads.saveAndFlush(leadDoMeta("leadgen-1"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        TenantContexto.executar(tenant, () -> assertThat(leads.findByMetaLeadId("leadgen-1")).isPresent());
    }

    @Test
    void excluirFazSoftDeleteEEscondeOLead() {
        Lead salvo = TenantContexto.calcular(tenant, () -> leads.save(novoLead(UUID.randomUUID(), 1)));

        TenantContexto.executar(tenant, () -> leads.deleteById(salvo.getId()));

        Integer linhas = jdbc.queryForObject(
                "SELECT count(*) FROM marketing.leads WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, salvo.getId());
        assertThat(linhas).as("linha continua no banco, com deleted_at").isEqualTo(1);
        TenantContexto.executar(tenant, () -> assertThat(leads.findById(salvo.getId())).isEmpty());
    }

    @Test
    void timelineSaiNaOrdemDaSequencia() {
        Lead lead = TenantContexto.calcular(tenant, () -> leads.save(novoLead(UUID.randomUUID(), 1)));

        TenantContexto.executar(tenant, () -> {
            eventos.save(LeadEvento.etapaAlterada(lead.getId(), 0, 1, Map.of("fonte", "landing")));
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.CONSENTIMENTO_REGISTRADO, null));
            eventos.save(LeadEvento.etapaAlterada(lead.getId(), 1, 2, null));
        });

        List<LeadEvento> timeline = TenantContexto.calcular(tenant,
                () -> eventos.findByLeadIdOrderBySequenciaAsc(lead.getId()));
        assertThat(timeline).extracting(LeadEvento::getTipoEvento).containsExactly(
                TipoEventoLead.ETAPA_ALTERADA, TipoEventoLead.CONSENTIMENTO_REGISTRADO, TipoEventoLead.ETAPA_ALTERADA);
        assertThat(timeline).extracting(LeadEvento::getSequencia).isSorted().doesNotContainNull();
        assertThat(timeline.get(0).getPayload()).containsEntry("fonte", "landing");
        assertThat(timeline.get(2).getEtapaAnterior()).isEqualTo(1);
        assertThat(timeline.get(2).getEtapaNova()).isEqualTo(2);
    }

    @Test
    void consentimentoGuardaEvidenciaEOMaisRecenteVemPrimeiro() {
        Lead lead = TenantContexto.calcular(tenant, () -> leads.save(novoLead(UUID.randomUUID(), 1)));
        UUID formulario = UUID.randomUUID();

        String texto = "Autorizo o uso dos meus dados para contato comercial. ".repeat(10);

        TenantContexto.executar(tenant, () -> {
            consentimentos.save(LeadConsentimento.doLanding(lead.getId(), OrigemConsentimento.FORMULARIO_RECEBIDO,
                    true, AGORA, texto, formulario, UUID.randomUUID(), null, UUID.randomUUID()));
            consentimentos.save(LeadConsentimento.revogacaoPorDescadastro(lead.getId(), AGORA.plusDays(3),
                    "2001:db8::1", "Mozilla/5.0"));
        });

        LeadConsentimento ultimo = TenantContexto.calcular(tenant,
                () -> consentimentos.findFirstByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()))
                .orElseThrow();
        assertThat(ultimo.getOrigem()).isEqualTo(OrigemConsentimento.DESCADASTRO);
        assertThat(ultimo.isAceito()).isFalse();
        assertThat(TenantContexto.calcular(tenant,
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId())))
                .extracting(LeadConsentimento::getFormularioId)
                .containsExactly(null, formulario);
        assertThat(TenantContexto.calcular(tenant,
                () -> consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId())).get(1)
                .getTextoTermo()).as("texto longo cabe na coluna text").isEqualTo(texto);
    }

    @Test
    void eventoNaoPodeApontarParaLeadInexistente() {
        assertThatThrownBy(() -> TenantContexto.executar(tenant, () -> eventos.saveAndFlush(
                new LeadEvento(UUID.randomUUID(), TipoEventoLead.HANDOFF_CRM, null))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Lead novoLead(UUID sessao, int etapa) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, etapa);
        lead.vincularSessao(sessao);
        return lead;
    }

    private static Lead leadDoMeta(String metaLeadId) {
        Lead lead = new Lead(OrigemLead.META_ADS, 0);
        lead.vincularMetaLead(metaLeadId);
        return lead;
    }
}
