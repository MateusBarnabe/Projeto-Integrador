package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Canal por onde o lead entrou (coluna {@code leads.origem}). */
public enum OrigemLead implements ValorPersistido {

    META_ADS("meta_ads"),
    LANDING_PAGE("landing_page"),
    MANUAL("manual");

    private final String valor;

    OrigemLead(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<OrigemLead> {
        public Conversor() {
            super(OrigemLead.class);
        }
    }
}
