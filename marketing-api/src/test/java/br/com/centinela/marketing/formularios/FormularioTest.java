package br.com.centinela.marketing.formularios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class FormularioTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void campoDeEscolhaPrecisaDeOpcoes() {
        assertThatThrownBy(() -> campo("cargo", TipoCampo.LISTA, 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(campo("cargo", TipoCampo.RADIO, 1, List.of("Decisor")).opcoes()).containsExactly("Decisor");
        assertThat(campo("cnpj", TipoCampo.CNPJ, 1, null).opcoes()).isEmpty();
    }

    @Test
    void naoPublicaFormularioAindaNaoSalvo() {
        Formulario formulario = new Formulario("Black Friday");

        assertThatThrownBy(() -> formulario.publicar(List.of(campo("nome", TipoCampo.TEXTO, 1, null)), AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void versaoRecusaChaveRepetidaEOrdenaOsCampos() {
        UUID formulario = UUID.randomUUID();
        Campo nome = campo("nome", TipoCampo.TEXTO, 2, null);
        Campo email = campo("email", TipoCampo.EMAIL, 1, null);

        assertThatThrownBy(() -> new FormularioVersao(formulario, 1, List.of(nome, campo("nome", TipoCampo.TEXTO, 3, null)), AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FormularioVersao(formulario, 1, List.of(), AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new FormularioVersao(formulario, 1, List.of(nome, email), AGORA).getCampos())
                .extracting(Campo::chave).containsExactly("email", "nome");
    }

    static Campo campo(String chave, TipoCampo tipo, int ordem, List<String> opcoes) {
        return new Campo(UUID.randomUUID(), chave, "Rótulo de " + chave, tipo, true, false, ordem, opcoes);
    }
}
