package br.com.centinela.marketing.painel;

import java.util.List;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;

/** Filtro do painel inválido: vira {@code 400} com os erros por campo (§8.2). */
class FiltroInvalidoException extends RuntimeException {

    private final List<ErroCampo> erros;

    FiltroInvalidoException(List<ErroCampo> erros) {
        super("Filtro inválido.");
        this.erros = List.copyOf(erros);
    }

    List<ErroCampo> erros() {
        return erros;
    }
}
