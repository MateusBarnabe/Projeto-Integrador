package br.com.centinela.marketing.comunicacao;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Canal da comunicação com o lead (norteador §3: {@code email} nesta fase). */
public enum CanalComunicacao implements ValorPersistido {

    EMAIL("email");

    private final String valor;

    CanalComunicacao(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<CanalComunicacao> {
        public Conversor() {
            super(CanalComunicacao.class);
        }
    }
}
