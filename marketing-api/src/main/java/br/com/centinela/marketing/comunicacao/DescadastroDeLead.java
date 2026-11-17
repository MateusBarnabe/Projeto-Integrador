package br.com.centinela.marketing.comunicacao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Descadastro público pelo link do e-mail (E7, norteador §6). O {@code GET} nunca efetiva o opt-out
 * (leitores de e-mail abrem links automaticamente); só o {@code POST} grava o opt-out, a revogação em
 * {@code lead_consentimentos} (com IP e User-Agent) e o registro {@code descadastrado} na timeline.
 *
 * <p>Token desconhecido ou já anonimizado recebe resposta neutra idêntica, sem revelar se o lead existe.
 */
@Service
public class DescadastroDeLead {

    private final TravasDeComunicacao travas;
    private final LeadRepository leads;
    private final LeadConsentimentoRepository consentimentos;
    private final LeadEventoRepository eventos;
    private final TransactionTemplate transacao;

    DescadastroDeLead(TravasDeComunicacao travas, LeadRepository leads, LeadConsentimentoRepository consentimentos,
            LeadEventoRepository eventos, TransactionTemplate transacao) {
        this.travas = travas;
        this.leads = leads;
        this.consentimentos = consentimentos;
        this.eventos = eventos;
        this.transacao = transacao;
    }

    /** Efetiva o descadastro se o token corresponder a um lead ativo; idempotente para cliques repetidos. */
    public void efetivar(String token, String ip, String userAgent) {
        Optional<TravasDeComunicacao.LeadDoToken> encontrado = travas.leadPeloTokenDescadastro(token);
        if (encontrado.isEmpty()) {
            return;
        }
        UUID tenant = encontrado.get().tenantId();
        UUID leadId = encontrado.get().leadId();
        TenantContexto.executar(tenant, () -> transacao.executeWithoutResult(
                status -> efetivarNoTenant(leadId, ip, userAgent)));
    }

    private void efetivarNoTenant(UUID leadId, String ip, String userAgent) {
        if (!travas.travarParaDescadastro(leadId)) {
            return;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.isAnonimizado()) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (lead.registrarOptOut(agora)) {
            leads.save(lead);
            consentimentos.save(LeadConsentimento.revogacaoPorDescadastro(lead.getId(), agora, ip, userAgent));
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.DESCADASTRADO,
                    Map.of("origem", "descadastro")));
        }
    }
}
