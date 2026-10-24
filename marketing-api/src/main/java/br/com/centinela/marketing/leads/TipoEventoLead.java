package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Tipos de registro da timeline do lead (coluna {@code lead_eventos.tipo_evento}). */
public enum TipoEventoLead implements ValorPersistido {

    ETAPA_ALTERADA("etapa_alterada"),
    FORMULARIO_ATUALIZADO("formulario_atualizado"),
    COMUNICACAO_ENVIADA("comunicacao_enviada"),
    CONSENTIMENTO_REGISTRADO("consentimento_registrado"),
    QUALIFICACAO_ALTERADA("qualificacao_alterada"),
    VENDEDOR_ATRIBUIDO("vendedor_atribuido"),
    HANDOFF_CRM("handoff_crm"),
    DESCADASTRADO("descadastrado"),
    ANONIMIZADO("anonimizado");

    private final String valor;

    TipoEventoLead(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<TipoEventoLead> {
        public Conversor() {
            super(TipoEventoLead.class);
        }
    }
}
