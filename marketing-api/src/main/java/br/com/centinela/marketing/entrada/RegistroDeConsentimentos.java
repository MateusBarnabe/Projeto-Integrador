package br.com.centinela.marketing.entrada;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Evidência de consentimento (norteador §3, {@code lead_consentimentos}): grava o aceite ou a
 * revogação só quando é um fato novo e mantém o estado atual do lead
 * ({@code consentimento_marketing}, {@code consentimento_em}) na mesma transação.
 *
 * <p>É idempotente: um registro que repete qualquer linha já gravada do lead
 * ({@link LeadConsentimento#repete}: mesmo aceite e momento, e o mesmo texto quando ele vem) não entra
 * de novo. Isso cobre a mesma captura recebida por eventos diferentes, a captura do chatbot e o envio
 * da mesma conversa e um aceite antigo que chega depois de uma revogação.
 *
 * <p>O estado do lead só muda quando o registro é o mais recente: um aceite atrasado não desfaz uma
 * revogação posterior. Texto do termo, IP e User-Agent nunca vão para a timeline nem para log.
 */
@Service
public class RegistroDeConsentimentos {

    private final LeadConsentimentoRepository consentimentos;
    private final LeadEventoRepository eventos;

    public RegistroDeConsentimentos(LeadConsentimentoRepository consentimentos, LeadEventoRepository eventos) {
        this.consentimentos = consentimentos;
        this.eventos = eventos;
    }

    /**
     * @param lead    lead já gravado, a quem o registro pertence
     * @param registro aceite do Landing ({@link LeadConsentimento#doLanding}) ou revogação
     * @return {@code true} se uma linha nova entrou
     */
    @Transactional
    public boolean registrar(Lead lead, LeadConsentimento registro) {
        Objects.requireNonNull(lead, "lead");
        Objects.requireNonNull(registro, "registro");
        if (!lead.getId().equals(registro.getLeadId())) {
            throw new IllegalArgumentException("Consentimento de outro lead.");
        }
        boolean repetido = consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId()).stream()
                .anyMatch(registro::repete);
        if (repetido) {
            return false;
        }
        LeadConsentimento gravado = consentimentos.save(registro);
        if (lead.getConsentimentoEm() == null || !gravado.getRegistradoEm().isBefore(lead.getConsentimentoEm())) {
            lead.registrarConsentimento(gravado.isAceito(), gravado.getRegistradoEm());
        }
        eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.CONSENTIMENTO_REGISTRADO, payload(gravado)));
        return true;
    }

    // Sem texto do termo, IP nem User-Agent (CLAUDE.md: evidência só em lead_consentimentos)
    private static Map<String, Object> payload(LeadConsentimento consentimento) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("consentimentoId", consentimento.getId().toString());
        payload.put("origem", consentimento.getOrigem().valor());
        payload.put("finalidade", consentimento.getFinalidade());
        payload.put("aceito", consentimento.isAceito());
        payload.put("registradoEm", consentimento.getRegistradoEm().toString());
        return payload;
    }
}
