package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Espelho da etapa do funil, calculado pelo banco (coluna gerada {@code leads.temperatura}). */
public enum Temperatura implements ValorPersistido {

    FRIO("frio"),
    MEDIO("medio"),
    QUENTE("quente");

    private final String valor;

    Temperatura(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<Temperatura> {
        public Conversor() {
            super(Temperatura.class);
        }
    }
}
