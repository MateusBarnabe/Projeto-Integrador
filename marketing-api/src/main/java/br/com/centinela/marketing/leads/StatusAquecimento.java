package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Situação do lead na cadência de aquecimento (job 4.2). */
public enum StatusAquecimento implements ValorPersistido {

    ATIVO("ativo"),
    ESGOTADO("esgotado"),
    OPT_OUT("opt_out");

    private final String valor;

    StatusAquecimento(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<StatusAquecimento> {
        public Conversor() {
            super(StatusAquecimento.class);
        }
    }
}
