package br.com.centinela.marketing.qualificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.centinela.marketing.compartilhado.dados.BancoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/** Confere a migration V6 e o mapeamento da qualificação contra o PostgreSQL real. */
class QualificacaoRepositoryTest extends BancoDeTeste {

    @Autowired
    RegraPontuacaoRepository regras;

    @Autowired
    QualificacaoConfiguracaoRepository configuracoes;

    @Autowired
    VendedorRodizioRepository vendedores;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();

    @Test
    void regrasGuardamValoresEmJsonEFiltramAsAtivas() {
        TenantContexto.executar(tenant, () -> {
            regras.save(RegraPontuacao.porCampo("Cargo", "cargo", List.of("Decisor", "Sócio"), 30));
            regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                    List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 20));
            RegraPontuacao inativa = RegraPontuacao.porCampo("Prazo", "prazo", List.of("Imediato"), 25);
            inativa.desativar();
            regras.save(inativa);
        });

        TenantContexto.executar(tenant, () -> {
            List<RegraPontuacao> ativas = regras.findByAtivoTrue();
            assertThat(ativas).extracting(RegraPontuacao::getCriterio)
                    .containsExactlyInAnyOrder("Cargo", "Origem do tráfego");
            RegraPontuacao utm = ativas.stream().filter(r -> r.getFonte() == FonteRegra.UTM).findFirst().orElseThrow();
            assertThat(utm.getValores()).containsExactly(
                    Map.of("source", "google", "medium", "cpc"), Map.of("direto", true));
            assertThat(regras.existsByCampoChaveAndAtivoTrue("cargo")).isTrue();
            assertThat(regras.existsByCampoChaveAndAtivoTrue("prazo")).as("regra inativa").isFalse();
        });
    }

    @Test
    void bancoExigeChaveNaRegraDeCampo() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO marketing.regras_pontuacao
                    (id, tenant_id, criterio, fonte, valores, pontos, created_at, updated_at)
                VALUES (?, ?, 'Cargo', 'campo_formulario', '["Decisor"]', 30, now(), now())
                """, UUID.randomUUID(), tenant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void configuracaoEhUmaPorTenantComDiasEmArray() {
        TenantContexto.executar(tenant, () -> {
            QualificacaoConfiguracao configuracao = QualificacaoConfiguracao.padrao();
            configuracao.definirAtendimento(LocalTime.of(9, 0), LocalTime.of(17, 30),
                    EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.MONDAY));
            configuracoes.save(configuracao);
        });

        TenantContexto.executar(tenant, () -> {
            QualificacaoConfiguracao lida = configuracoes.findFirstBy().orElseThrow();
            assertThat(lida.getDiasAtendimento()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
            assertThat(lida.getHorarioComercialFim()).isEqualTo(LocalTime.of(17, 30));
        });
        assertThat(jdbc.queryForObject(
                "SELECT dias_atendimento::text FROM marketing.qualificacao_configuracoes WHERE tenant_id = ?",
                String.class, tenant)).isEqualTo("{1,6}");
        assertThatThrownBy(() -> TenantContexto.executar(tenant,
                () -> configuracoes.saveAndFlush(QualificacaoConfiguracao.padrao())))
                .isInstanceOf(DataIntegrityViolationException.class);
        TenantContexto.executar(UUID.randomUUID(), () -> assertThat(configuracoes.findFirstBy()).isEmpty());
    }

    @Test
    void vendedorEhUnicoNaEscalaDoTenant() {
        UUID vendedor = UUID.randomUUID();
        OffsetDateTime agora = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        TenantContexto.executar(tenant, () -> {
            VendedorRodizio escalado = new VendedorRodizio(vendedor);
            escalado.registrarAtribuicao(agora);
            vendedores.save(escalado);
        });

        TenantContexto.executar(UUID.randomUUID(), () -> vendedores.save(new VendedorRodizio(vendedor)));
        assertThatThrownBy(() -> TenantContexto.executar(tenant,
                () -> vendedores.saveAndFlush(new VendedorRodizio(vendedor))))
                .isInstanceOf(DataIntegrityViolationException.class);
        TenantContexto.executar(tenant, () -> {
            assertThat(vendedores.findByVendedorId(vendedor)).get()
                    .extracting(VendedorRodizio::getUltimaAtribuicaoEm).isEqualTo(agora);
            assertThat(vendedores.findByAtivoTrue()).hasSize(1);
        });
    }
}
