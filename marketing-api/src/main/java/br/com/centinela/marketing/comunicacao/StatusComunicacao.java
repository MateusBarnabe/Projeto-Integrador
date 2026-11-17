package br.com.centinela.marketing.comunicacao;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Situação de um envio em {@code lead_comunicacoes} (norteador §3). */
public enum StatusComunicacao implements ValorPersistido {

    ENVIADO("enviado"),
    ENTREGUE("entregue"),
    ABERTO("aberto"),
    CLICADO("clicado"),
    FALHOU("falhou");

    private final String valor;

    StatusComunicacao(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<StatusComunicacao> {
        public Conversor() {
            super(StatusComunicacao.class);
        }
    }
}
