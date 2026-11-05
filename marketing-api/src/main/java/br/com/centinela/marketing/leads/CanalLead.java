package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/**
 * Como o visitante acessou o formulário do Landing (coluna {@code leads.canal}). São os mesmos
 * valores do campo {@code canal} de {@code landing.formulario.recebido}.
 */
public enum CanalLead implements ValorPersistido {

    FORMULARIO("formulario"),
    LANDING_PAGE("landing_page"),
    EMBED("embed"),
    CHATBOT("chatbot");

    private final String valor;

    CanalLead(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<CanalLead> {
        public Conversor() {
            super(CanalLead.class);
        }
    }
}
