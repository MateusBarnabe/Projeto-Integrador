package br.com.centinela.marketing.entrada;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.Lead.IdentificadoresLiberados;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Merge de leads duplicados na leitura do envio (norteador §5, passo 3.4): depois de gravar o contato
 * lido no Landing, os leads do tenant com o mesmo e-mail ou telefone viram um só. O mais antigo fica;
 * cada um dos outros é marcado como excluído (soft delete) e a timeline dos dois registra
 * {@code leads_unidos}.
 *
 * <p>O que fica recebe do outro o que ainda não tem ({@link Lead#absorver}): sessão, envio e
 * respostas, ids do CRM e a oportunidade, vendedor, contato, UTM e toques, boas-vindas e opt-out. A
 * etapa sobe para a do outro, se for maior (nunca desce). Os consentimentos do outro são copiados
 * para o que fica, sem repetir linha ({@link RegistroDeConsentimentos}); as linhas originais e a
 * timeline do outro continuam nele, como histórico. Pontuação e vendedor são refeitos por quem chama.
 *
 * <p>Roda na transação e no tenant de quem chama. A timeline e o log levam só ids.
 */
@Component
class JuncaoDeLeads {

    private static final Logger LOG = LoggerFactory.getLogger(JuncaoDeLeads.class);

    static final String PAPEL_MANTIDO = "mantido";
    static final String PAPEL_UNIDO = "unido";

    private final IdentificacaoDeLeads identificacao;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;
    private final LeadConsentimentoRepository consentimentosGravados;
    private final RegistroDeConsentimentos consentimentos;
    private final GravacaoDeLeads gravacao;
    private final TravasDeLeads travas;

    JuncaoDeLeads(IdentificacaoDeLeads identificacao, LeadRepository leads, LeadEventoRepository eventos,
            LeadConsentimentoRepository consentimentosGravados, RegistroDeConsentimentos consentimentos,
            GravacaoDeLeads gravacao, TravasDeLeads travas) {
        this.identificacao = identificacao;
        this.leads = leads;
        this.eventos = eventos;
        this.consentimentosGravados = consentimentosGravados;
        this.consentimentos = consentimentos;
        this.gravacao = gravacao;
        this.travas = travas;
    }

    /**
     * Junta ao mais antigo os leads com o mesmo e-mail ou telefone de {@code lead} (já travado por quem
     * chama e com o contato do envio).
     *
     * @return o lead que fica: {@code lead} ou um mais antigo
     */
    Lead juntarDuplicados(Lead lead, Procedencia procedencia) {
        Map<UUID, Lead> porId = new LinkedHashMap<>();
        porId.put(lead.getId(), lead);
        identificacao.mesmoContato(lead.getEmail(), lead.getTelefone())
                .forEach(outro -> porId.putIfAbsent(outro.getId(), outro));
        if (porId.size() == 1) {
            return lead;
        }
        List<Lead> doMaisAntigo = new ArrayList<>(porId.values());
        doMaisAntigo.sort(Comparator.comparing(Lead::getCriadoEm).thenComparing(Lead::getId));

        Lead fica = doMaisAntigo.get(0);
        if (fica != lead) {
            travas.travar(fica);
        }
        for (Lead outro : doMaisAntigo.subList(1, doMaisAntigo.size())) {
            if (outro != lead) {
                travas.travar(outro);
            }
            juntar(fica, outro, procedencia);
        }
        return fica;
    }

    private void juntar(Lead fica, Lead outro, Procedencia procedencia) {
        List<LeadConsentimento> doOutro = new ArrayList<>(
                consentimentosGravados.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(outro.getId()));
        Collections.reverse(doOutro); // do mais antigo para o mais recente: o último vale como estado
        int etapaDoOutro = outro.getEtapa();

        IdentificadoresLiberados liberados = outro.liberarParaJuncao();
        // Grava antes: sessão, envio e Meta são únicos por tenant também nas linhas excluídas
        leads.saveAndFlush(outro);

        fica.absorver(outro, liberados);
        gravacao.avancarEtapa(fica, etapaDoOutro, procedencia);
        Lead gravado = gravacao.salvar(fica);
        for (LeadConsentimento consentimento : doOutro) {
            consentimentos.registrar(gravado, copia(consentimento, gravado.getId()));
        }

        eventos.save(new LeadEvento(gravado.getId(), TipoEventoLead.LEADS_UNIDOS,
                payload(procedencia, PAPEL_MANTIDO, outro.getId(), liberados)));
        eventos.save(new LeadEvento(outro.getId(), TipoEventoLead.LEADS_UNIDOS,
                payload(procedencia, PAPEL_UNIDO, gravado.getId(), liberados)));
        LOG.info("Lead {} unido ao lead {}, mais antigo, pelo mesmo contato", outro.getId(), gravado.getId());
    }

    private static LeadConsentimento copia(LeadConsentimento original, UUID leadId) {
        if (original.getOrigem() == OrigemConsentimento.DESCADASTRO) {
            return LeadConsentimento.revogacaoPorDescadastro(leadId, original.getRegistradoEm(), original.getIp(),
                    original.getUserAgent());
        }
        return LeadConsentimento.doLanding(leadId, original.getOrigem(), original.isAceito(),
                original.getRegistradoEm(), original.getTextoTermo(), original.getFormularioId(),
                original.getEnvioId(), original.getSessaoId(), original.getEventoId());
    }

    // Só ids: o contato que ligou os dois não entra na timeline
    private static Map<String, Object> payload(Procedencia procedencia, String papel, UUID outroLead,
            IdentificadoresLiberados liberados) {
        Map<String, Object> payload = procedencia.payload();
        payload.put("papel", papel);
        payload.put(PAPEL_MANTIDO.equals(papel) ? "leadUnidoId" : "leadMantidoId", outroLead.toString());
        payload.put("motivo", "mesmo_contato");
        if (liberados.envioId() != null) {
            payload.put("envioIdDoUnido", liberados.envioId().toString());
        }
        if (liberados.sessaoId() != null) {
            payload.put("sessaoIdDoUnido", liberados.sessaoId().toString());
        }
        return payload;
    }
}
