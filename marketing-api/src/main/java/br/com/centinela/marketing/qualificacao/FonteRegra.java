package br.com.centinela.marketing.qualificacao;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** De onde a regra de pontuação lê o valor: uma resposta do formulário ou a UTM de origem. */
public enum FonteRegra implements ValorPersistido {

    CAMPO_FORMULARIO("campo_formulario"),
    UTM("utm");

    private final String valor;

    FonteRegra(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<FonteRegra> {
        public Conversor() {
            super(FonteRegra.class);
        }
    }
}
