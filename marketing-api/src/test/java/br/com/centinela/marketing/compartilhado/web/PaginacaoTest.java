package br.com.centinela.marketing.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PaginacaoTest {

    @Test
    void limitaTamanhoA100EImpedePaginaNegativa() {
        var pedido = Paginacao.pedido(-3, 500, "nome,asc", Map.of("nome", "nome"));

        assertThat(pedido.getPageNumber()).isZero();
        assertThat(pedido.getPageSize()).isEqualTo(100);
        assertThat(pedido.getSort().getOrderFor("nome").isAscending()).isTrue();
    }

    @Test
    void usaPadroesEOrdenacaoPermitidaQuandoParametroEInvalido() {
        var pedido = Paginacao.pedido(null, null, "campo-inexistente,asc", Map.of("criadoEm", "criadoEm"));

        assertThat(pedido.getPageNumber()).isZero();
        assertThat(pedido.getPageSize()).isEqualTo(Paginacao.TAMANHO_PADRAO);
        assertThat(pedido.getSort().getOrderFor("criadoEm").isAscending()).isTrue();
    }
}