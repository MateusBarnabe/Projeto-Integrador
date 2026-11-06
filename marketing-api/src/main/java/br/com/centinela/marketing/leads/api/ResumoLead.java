package br.com.centinela.marketing.leads.api;

import java.util.UUID;

import br.com.centinela.marketing.leads.Lead;

/**
 * Resumo do lead para outros módulos, no formato {@code ResumoLead} do {@code contratos/marketing.yaml}
 * (norteador §9.6). <strong>Sem dado pessoal:</strong> nome, e-mail, telefone, respostas e evidência
 * de consentimento ficam no detalhe, com {@code marketing.lead.ver}. Não acrescente campo aqui sem
 * mudar o contrato no infra.
 */
public record ResumoLead(UUID id, int etapa, String temperatura, String faixaQualificacao, Integer pontuacao,
        String origem, String utmCampaign, UUID vendedorId, UUID crmOportunidadeId) {

    static ResumoLead de(Lead lead) {
        return new ResumoLead(lead.getId(), lead.getEtapa(), Valores.de(lead.getTemperatura()),
                Valores.de(lead.getFaixaQualificacao()), lead.getPontuacao(), Valores.de(lead.getOrigem()),
                lead.getUtmCampaign(), lead.getVendedorId(), lead.getCrmOportunidadeId());
    }
}
