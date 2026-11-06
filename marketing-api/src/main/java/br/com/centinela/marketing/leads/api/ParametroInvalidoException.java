package br.com.centinela.marketing.leads.api;

import java.util.List;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;

/** Filtro ou parâmetro de consulta inválido: vira {@code 400} com os erros por campo (§8.2). */
class ParametroInvalidoException extends RuntimeException {

    private final List<ErroCampo> erros;

    ParametroInvalidoException(String mensagem, List<ErroCampo> erros) {
        super(mensagem);
        this.erros = List.copyOf(erros);
    }

    ParametroInvalidoException(String mensagem, ErroCampo erro) {
        this(mensagem, List.of(erro));
    }

    List<ErroCampo> erros() {
        return erros;
    }
}
