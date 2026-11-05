package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** De onde veio um aceite ou revogação de consentimento (coluna {@code lead_consentimentos.origem}). */
public enum OrigemConsentimento implements ValorPersistido {

    FORMULARIO_RECEBIDO("landing.formulario.recebido"),
    CHATBOT_LEAD_CAPTURADO("landing.chatbot.lead_capturado"),
    DESCADASTRO("descadastro");

    private final String valor;

    OrigemConsentimento(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<OrigemConsentimento> {
        public Conversor() {
            super(OrigemConsentimento.class);
        }
    }
}
