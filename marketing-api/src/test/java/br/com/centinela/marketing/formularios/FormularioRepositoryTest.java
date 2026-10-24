package br.com.centinela.marketing.formularios;

import static br.com.centinela.marketing.formularios.FormularioTest.campo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.dados.BancoDeTeste;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/** Confere a migration V5 e o mapeamento dos formulários contra o PostgreSQL real. */
class FormularioRepositoryTest extends BancoDeTeste {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    FormularioRepository formularios;

    @Autowired
    FormularioVersaoRepository versoes;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID tenant = UUID.randomUUID();

    @Test
    void publicarCriaNovaVersaoSemAlterarAAnterior() {
        UUID id = TenantContexto.calcular(tenant, () -> formularios.save(new Formulario("Black Friday")).getId());
        List<Campo> primeiros = List.of(
                campo("nome", TipoCampo.TEXTO, 1, null),
                campo("cargo", TipoCampo.LISTA, 2, List.of("Decisor", "Analista")));
        List<Campo> segundos = List.of(
                campo("nome", TipoCampo.TEXTO, 1, null),
                campo("orcamento", TipoCampo.RADIO, 2, List.of("Até 10 mil", "Acima de 10 mil")));

        publicar(id, primeiros);
        publicar(id, segundos);

        TenantContexto.executar(tenant, () -> {
            assertThat(formularios.findById(id)).get().extracting(Formulario::getVersaoAtiva).isEqualTo(2);
            assertThat(versoes.findByFormularioIdOrderByVersaoAsc(id)).extracting(FormularioVersao::getVersao)
                    .containsExactly(1, 2);
            FormularioVersao v1 = versoes.findByFormularioIdAndVersao(id, 1).orElseThrow();
            assertThat(v1.getCampos()).isEqualTo(primeiros);
            assertThat(v1.getCampos().get(1).tipo()).isEqualTo(TipoCampo.LISTA);
        });
        String json = jdbc.queryForObject(
                "SELECT campos::text FROM marketing.formulario_versoes WHERE formulario_id = ? AND versao = 1",
                String.class, id);
        assertThat(json).contains("\"tipo\": \"lista\"").contains("\"chave\": \"cargo\"");
    }

    @Test
    void bancoRecusaAlterarCamposDeVersaoPublicada() {
        UUID id = TenantContexto.calcular(tenant, () -> formularios.save(new Formulario("Consultoria")).getId());
        publicar(id, List.of(campo("nome", TipoCampo.TEXTO, 1, null)));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE marketing.formulario_versoes SET campos = '[]'::jsonb WHERE formulario_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("não pode mudar");
        jdbc.update("UPDATE marketing.formulario_versoes SET deleted_at = now() WHERE formulario_id = ?", id);
    }

    @Test
    void versaoEhUnicaPorFormulario() {
        UUID id = TenantContexto.calcular(tenant, () -> formularios.save(new Formulario("Duplicado")).getId());
        publicar(id, List.of(campo("nome", TipoCampo.TEXTO, 1, null)));

        assertThatThrownBy(() -> TenantContexto.executar(tenant, () -> versoes.saveAndFlush(
                new FormularioVersao(id, 1, List.of(campo("email", TipoCampo.EMAIL, 1, null)), AGORA))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void formularioDeOutroTenantNaoAparece() {
        UUID id = TenantContexto.calcular(tenant, () -> formularios.save(new Formulario("Só meu")).getId());
        publicar(id, List.of(campo("nome", TipoCampo.TEXTO, 1, null)));

        TenantContexto.executar(UUID.randomUUID(), () -> {
            assertThat(formularios.findById(id)).isEmpty();
            assertThat(versoes.findByFormularioIdAndVersao(id, 1)).isEmpty();
        });
    }

    @Test
    void excluirFormularioFazSoftDelete() {
        UUID id = TenantContexto.calcular(tenant, () -> formularios.save(new Formulario("Antigo")).getId());

        TenantContexto.executar(tenant, () -> formularios.deleteById(id));

        TenantContexto.executar(tenant, () -> assertThat(formularios.findById(id)).isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marketing.formularios WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    /** Publica numa transação só, como fará o serviço da A1: grava a versão e a versão ativa juntas. */
    private void publicar(UUID formularioId, List<Campo> campos) {
        TenantContexto.executar(tenant, () -> transacao.executeWithoutResult(status -> {
            Formulario formulario = formularios.findById(formularioId).orElseThrow();
            versoes.save(formulario.publicar(campos, AGORA));
        }));
    }
}
