package br.com.centinela.marketing.notificacoes;

/**
 * Fatos do lead que o Marketing registra na timeline da empresa do CRM
 * ({@code identity.timeline.registrar}). O {@code tipoDoFato} segue {@code modulo.entidade.acao};
 * o texto é montado sem dado pessoal por {@link NotificacoesDeLead}.
 */
public enum FatoDaTimeline {

    /** O lead foi ligado à oportunidade criada pelo Landing (E6, {@code crm.oportunidade.criada}). */
    OPORTUNIDADE_VINCULADA("marketing.lead.oportunidade_vinculada", "Lead do Marketing ligado à oportunidade"),

    /** O lead recebeu vendedor, pelo rodízio ou na reatribuição manual. */
    VENDEDOR_ATRIBUIDO("marketing.lead.atribuido", "Lead do Marketing atribuído a um vendedor"),

    /** O lead entrou numa faixa MQL ou mudou de faixa. */
    LEAD_QUALIFICADO("marketing.lead.qualificado", "Lead do Marketing qualificado");

    private final String tipoDoFato;
    private final String resumo;

    FatoDaTimeline(String tipoDoFato, String resumo) {
        this.tipoDoFato = tipoDoFato;
        this.resumo = resumo;
    }

    public String tipoDoFato() {
        return tipoDoFato;
    }

    String resumo() {
        return resumo;
    }
}
