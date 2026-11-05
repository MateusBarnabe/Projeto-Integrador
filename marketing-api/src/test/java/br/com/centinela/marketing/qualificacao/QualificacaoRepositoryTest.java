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
        UUID formulario = UUID.randomUUID();
        UUID outroFormulario = UUID.randomUUID();
        UUID campoCargo = UUID.randomUUID();
        TenantContexto.executar(tenant, () -> {
            regras.save(RegraPontuacao.porCampo("Cargo", formulario, campoCargo, List.of("Decisor", "Sócio"), 30));
            regras.save(RegraPontuacao.porCampo("Cargo", outroFormulario, UUID.randomUUID(), List.of("Gerente"), 30));
            regras.save(RegraPontuacao.porCampoMeta("Cargo", "qual_o_seu_cargo", List.of("Decisor"), 30));
            regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                    List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 20));
            RegraPontuacao inativa = RegraPontuacao.porCampo("Prazo", formulario, UUID.randomUUID(),
                    List.of("Imediato"), 25);
            inativa.desativar();
            regras.save(inativa);
        });

        TenantContexto.executar(tenant, () -> {
            assertThat(regras.findByAtivoTrue()).hasSize(4);
            assertThat(regras.findByAtivoTrueAndFormularioId(formulario))
                    .as("só as ativas do formulário")
                    .singleElement()
                    .satisfies(regra -> {
                        assertThat(regra.getCampoId()).isEqualTo(campoCargo);
                        assertThat(regra.getValores()).containsExactly("Decisor", "Sócio");
                    });
            assertThat(regras.findByAtivoTrueAndFonte(FonteRegra.CAMPO_META))
                    .extracting(RegraPontuacao::getCampoMeta).containsExactly("qual_o_seu_cargo");
            RegraPontuacao utm = regras.findByAtivoTrueAndFonte(FonteRegra.UTM).getFirst();
            assertThat(utm.getValores()).containsExactly(
                    Map.of("source", "google", "medium", "cpc"), Map.of("direto", true));
        });
        TenantContexto.executar(UUID.randomUUID(),
                () -> assertThat(regras.findByAtivoTrueAndFormularioId(formulario)).as("outro tenant").isEmpty());
    }

    @Test
    void bancoExigeOAlvoCertoParaCadaFonte() {
        String insert = """
                INSERT INTO marketing.regras_pontuacao
                    (id, tenant_id, criterio, fonte, formulario_id, campo_id, campo_meta, valores, pontos,
                     created_at, updated_at)
                VALUES (?, ?, 'Cargo', ?, ?, ?, ?, '["Decisor"]', 30, now(), now())
                """;
        UUID formulario = UUID.randomUUID();
        UUID campo = UUID.randomUUID();

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), tenant, "campo_formulario",
                formulario, null, null)).as("campo sem campo_id").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), tenant, "campo_meta",
                null, null, null)).as("meta sem pergunta").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), tenant, "utm",
                formulario, campo, null)).as("utm com campo").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insert, UUID.randomUUID(), tenant, "campo_formulario", formulario, campo, null))
                .isEqualTo(1);
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
