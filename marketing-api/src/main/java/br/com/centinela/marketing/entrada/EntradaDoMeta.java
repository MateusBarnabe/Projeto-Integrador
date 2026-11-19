package br.com.centinela.marketing.entrada;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.entrada.ChavesDeIdentidade.Chave;
import br.com.centinela.marketing.entrada.IdentificacaoDeLeads.LeadIdentificado;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Gravação do lead do Meta Ads na entrada (E4, norteador §4.1 e §5, passo 1). O job 4.1 já leu o lead
 * na Graph API; aqui ele vira (ou completa) uma linha de {@code leads}, sempre dentro do tenant e da
 * transação de quem chama:
 *
 * <ol>
 * <li>acha o lead pela identidade: {@code meta_lead_id} (reentrega do mesmo lead), depois e-mail e
 * telefone normalizados ({@link IdentificacaoDeLeads});</li>
 * <li>achado por {@code meta_lead_id}, atualiza o contato (o Meta é o fato mais recente dele); achado por
 * e-mail ou telefone, só completa o que falta e liga o {@code meta_lead_id}, sem sobrescrever dado já
 * preenchido nem rebaixar a etapa: o Meta só completa dados e registra o evento;</li>
 * <li>não achou: cria na etapa 0, {@code origem = meta_ads}, sem consentimento de marketing (o Meta não
 * entrega o aceite, X11);</li>
 * <li>guarda as respostas do formulário instantâneo no lead ({@code respostas_meta}), para uma nova
 * pontuação continuar somando as regras {@code campo_meta}, e registra {@code respostas_lidas} na
 * timeline, sem as respostas.</li>
 * </ol>
 *
 * <p>Pontuar e atribuir vendedor ficam com quem chama. A timeline e o log levam só ids e contagens.
 */
@Service
public class EntradaDoMeta {

    /** Etapa do funil de quem chegou pelo anúncio do Meta (§2). */
    public static final int ETAPA = 0;

    private static final Logger LOG = LoggerFactory.getLogger(EntradaDoMeta.class);

    private final IdentificacaoDeLeads identificacao;
    private final GravacaoDeLeads gravacao;
    private final TravasDeLeads travas;
    private final LeadEventoRepository eventos;

    EntradaDoMeta(IdentificacaoDeLeads identificacao, GravacaoDeLeads gravacao, TravasDeLeads travas,
            LeadEventoRepository eventos) {
        this.identificacao = identificacao;
        this.gravacao = gravacao;
        this.travas = travas;
        this.eventos = eventos;
    }

    /**
     * @param lead  o lead gravado; use este
     * @param criado {@code true} se o lead é novo; {@code false} se já existia
     * @param por   a chave que o achou, ou nulo se foi criado
     */
    public record Resultado(Lead lead, boolean criado, Chave por) {
    }

    /**
     * @param leadgenId id do lead no Meta ({@code leadgen_id})
     * @param nome      nome lido, em qualquer formato
     * @param email     e-mail lido, em qualquer formato
     * @param telefone  telefone lido, em qualquer formato
     * @param respostas respostas do formulário instantâneo, sem os campos de contato
     */
    @Transactional
    public Resultado gravar(String leadgenId, String nome, String email, String telefone,
            Map<String, Object> respostas, Procedencia procedencia) {
        Objects.requireNonNull(leadgenId, "leadgenId");
        Objects.requireNonNull(procedencia, "procedencia");
        ChavesDeIdentidade chaves = ChavesDeIdentidade.doMeta(leadgenId).comContato(email, telefone);
        String nomeLimpo = NormalizacaoDeContato.nome(nome);

        LeadIdentificado achado = identificacao.identificar(chaves).orElse(null);
        Lead lead;
        boolean criado = achado == null;
        if (criado) {
            Lead novo = new Lead(OrigemLead.META_ADS, ETAPA);
            novo.vincularMetaLead(chaves.metaLeadId());
            novo.atualizarContato(nomeLimpo, chaves.email(), chaves.telefone(), null);
            novo.registrarRespostasMeta(respostas);
            lead = gravacao.criar(novo, procedencia);
        } else {
            lead = achado.lead();
            // Outra rotina (envio do Landing, rodízio) pode estar com o mesmo lead: trava e relê antes de mudar
            travas.travar(lead);
            if (achado.por() == Chave.META_LEAD) {
                lead.atualizarContato(nomeLimpo, chaves.email(), chaves.telefone(), null);
            } else {
                lead.completarContato(nomeLimpo, chaves.email(), chaves.telefone(), null);
                if (lead.getMetaLeadId() == null) {
                    // Um segundo formulário do mesmo contato não troca o lead do Meta que já está ligado
                    lead.vincularMetaLead(chaves.metaLeadId());
                }
            }
            lead.registrarRespostasMeta(respostas);
            // A etapa nunca recua: o lead que já passou da 0 continua onde está
            gravacao.avancarEtapa(lead, ETAPA, procedencia);
            lead = gravacao.salvar(lead);
        }

        eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.RESPOSTAS_LIDAS,
                respostasLidas(procedencia, leadgenId, respostas, criado ? null : achado.por())));
        LOG.info("Lead {} do Meta {} ({} resposta(s))", lead.getId(),
                criado ? "criado" : "achado por " + achado.por(), respostas == null ? 0 : respostas.size());
        return new Resultado(lead, criado, criado ? null : achado.por());
    }

    // Sem as respostas nem o contato: só de onde vieram e quantas
    private static Map<String, Object> respostasLidas(Procedencia procedencia, String leadgenId,
            Map<String, Object> respostas, Chave por) {
        Map<String, Object> payload = procedencia.payload();
        payload.put("origem", OrigemLead.META_ADS.valor());
        payload.put("metaLeadId", leadgenId);
        payload.put("respostas", respostas == null ? 0 : respostas.size());
        payload.put("achadoPor", por == null ? null : por.name().toLowerCase(Locale.ROOT));
        return payload;
    }
}
