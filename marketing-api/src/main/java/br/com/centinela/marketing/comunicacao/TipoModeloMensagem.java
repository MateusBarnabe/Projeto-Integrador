package br.com.centinela.marketing.comunicacao;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Tipo de modelo de mensagem (norteador §3, {@code modelos_mensagem.tipo}). */
public enum TipoModeloMensagem implements ValorPersistido {

    BOAS_VINDAS("boas_vindas"),
    AQUECIMENTO("aquecimento");

    private final String valor;

    TipoModeloMensagem(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<TipoModeloMensagem> {
        public Conversor() {
            super(TipoModeloMensagem.class);
        }
    }
}
