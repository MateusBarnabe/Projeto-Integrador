package br.com.centinela.marketing.entrada;

import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;

/**
 * Gravação do lead na entrada (chatbot, envio, oportunidade, Meta) com o registro de
 * {@code etapa_alterada} na timeline. A etapa nunca volta (§2): quem chama sempre pede a etapa do seu
 * fato, e uma etapa menor ou igual à atual não muda nada. Roda na transação e no tenant de quem chama.
 */
@Service
public class GravacaoDeLeads {

    private final LeadRepository leads;
    private final LeadEventoRepository eventos;

    public GravacaoDeLeads(LeadRepository leads, LeadEventoRepository eventos) {
        this.leads = leads;
        this.eventos = eventos;
    }

    /**
     * Grava um lead novo, já preenchido por quem chama, e registra a entrada no funil
     * ({@code etapa_alterada} de nulo para a etapa dele).
     *
     * @return o lead gravado; use este, não o objeto passado
     */
    @Transactional
    public Lead criar(Lead novo, Procedencia procedencia) {
        Objects.requireNonNull(novo, "novo");
        if (novo.getId() != null) {
            throw new IllegalArgumentException("Lead já gravado; use salvar.");
        }
        Lead gravado = leads.save(novo);
        Map<String, Object> payload = procedencia.payload();
        payload.put("leadCriado", true);
        eventos.save(LeadEvento.etapaAlterada(gravado.getId(), null, gravado.getEtapa(), payload));
        return gravado;
    }

    /**
     * Sobe o lead para a etapa do fato, se ele estiver abaixo, e registra {@code etapa_alterada}.
     *
     * @return {@code true} se a etapa mudou
     */
    @Transactional
    public boolean avancarEtapa(Lead lead, int etapa, Procedencia procedencia) {
        int anterior = lead.getEtapa();
        if (!lead.avancarEtapa(etapa)) {
            return false;
        }
        eventos.save(LeadEvento.etapaAlterada(lead.getId(), anterior, lead.getEtapa(), procedencia.payload()));
        return true;
    }

    /** Grava as mudanças de um lead já existente. */
    @Transactional
    public Lead salvar(Lead lead) {
        return leads.save(lead);
    }
}
