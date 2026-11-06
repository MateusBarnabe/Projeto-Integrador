package br.com.centinela.marketing.leads.api;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.centinela.marketing.leads.Lead;

/**
 * Linha da lista de leads (norteador §9.2): nome, etapa, faixa, pontuação, origem e canal, campanha,
 * vendedor, oportunidade no CRM ("no CRM") e data de entrada. Lead anonimizado aparece sem nome e
 * sem e-mail, para as contagens baterem com o painel.
 */
public record ItemListaLead(UUID id, String nome, String email, int etapa, String temperatura,
        String faixaQualificacao, Integer pontuacao, String origem, String canal, String utmCampaign,
        UUID vendedorId, UUID crmOportunidadeId, boolean anonimizado, OffsetDateTime criadoEm) {

    static ItemListaLead de(Lead lead) {
        boolean anonimizado = lead.isAnonimizado();
        return new ItemListaLead(lead.getId(),
                anonimizado ? null : lead.getNome(),
                anonimizado ? null : lead.getEmail(),
                lead.getEtapa(), Valores.de(lead.getTemperatura()), Valores.de(lead.getFaixaQualificacao()),
                lead.getPontuacao(), Valores.de(lead.getOrigem()), Valores.de(lead.getCanal()),
                lead.getUtmCampaign(), lead.getVendedorId(), lead.getCrmOportunidadeId(), anonimizado,
                lead.getCriadoEm());
    }
}
