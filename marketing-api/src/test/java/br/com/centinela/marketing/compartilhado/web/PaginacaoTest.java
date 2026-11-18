package br.com.centinela.marketing.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PaginacaoTest {

    @Test
    void limitaTamanhoA100EImpedePaginaNegativa() {
        var pedido = Paginacao.pedido(-3, 500, "nome,asc", Map.of("nome", "nome"));

        assertThat(pedido.getPageNumber()).isZero();
        assertThat(pedido.getPageSize()).isEqualTo(100);
        Sort.Order ordem = pedido.getSort().getOrderFor("nome");
        assertNotNull(ordem);
        assertThat(ordem.isAscending()).isTrue();
    }

    @Test
    void usaPadroesEOrdenacaoPermitidaQuandoParametroEInvalido() {
        var pedido = Paginacao.pedido(null, null, "campo-inexistente,asc", Map.of("criadoEm", "criadoEm"));

        assertThat(pedido.getPageNumber()).isZero();
        assertThat(pedido.getPageSize()).isEqualTo(Paginacao.TAMANHO_PADRAO);
        Sort.Order ordem = pedido.getSort().getOrderFor("criadoEm");
        assertNotNull(ordem);
        assertThat(ordem.isAscending()).isTrue();
    }
}
