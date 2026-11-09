package br.com.centinela.marketing.leads;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Tipos de registro da timeline do lead (coluna {@code lead_eventos.tipo_evento}). */
public enum TipoEventoLead implements ValorPersistido {

    ETAPA_ALTERADA("etapa_alterada"),
    RESPOSTAS_LIDAS("respostas_lidas"),
    COMUNICACAO_ENVIADA("comunicacao_enviada"),
    CONSENTIMENTO_REGISTRADO("consentimento_registrado"),
    QUALIFICACAO_ALTERADA("qualificacao_alterada"),
    VENDEDOR_ATRIBUIDO("vendedor_atribuido"),
    HANDOFF_CRM("handoff_crm"),
    DESCADASTRADO("descadastrado"),
    ANONIMIZADO("anonimizado"),
    // E5/E11 (V10): junção de duplicados e desistência da leitura do envio (job 4.6)
    LEADS_UNIDOS("leads_unidos"),
    RESPOSTAS_INDISPONIVEIS("respostas_indisponiveis"),
    // R1 (V11): a visita do anúncio foi ligada ao lead na leitura do envio
    VISITA_VINCULADA("visita_vinculada");

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
