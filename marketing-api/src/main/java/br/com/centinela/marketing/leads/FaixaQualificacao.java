package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Faixa definida pela pontuação do lead (9.3). */
public enum FaixaQualificacao implements ValorPersistido {

    MQL_QUENTE("mql_quente"),
    MQL_MORNO("mql_morno"),
    NUTRICAO("nutricao");

    private final String valor;

    FaixaQualificacao(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<FaixaQualificacao> {
        public Conversor() {
            super(FaixaQualificacao.class);
        }
    }
}
