package br.com.centinela.marketing.entrada;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.centinela.marketing.compartilhado.mensageria.ConsumoDeEventos;
import br.com.centinela.marketing.compartilhado.mensageria.EventoInvalidoException;
import br.com.centinela.marketing.compartilhado.mensageria.EventoRecebido;
import br.com.centinela.marketing.entrada.IdentificacaoDeLeads.LeadIdentificado;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimento;
import br.com.centinela.marketing.leads.OrigemConsentimento;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.qualificacao.PontuacaoDeLeads;
import br.com.centinela.marketing.qualificacao.ResultadoPontuacao;

/**
 * {@code landing.chatbot.lead_capturado} (norteador §5, passo 2): o visitante deixou e-mail ou telefone,
 * com consentimento, no meio da conversa do chatbot. Na chegada:
 *
 * <ol>
 * <li>acha o lead por {@code sessao_id}, senão por e-mail, senão por telefone normalizados; senão, cria
 * ({@code origem = landing_page}, {@code canal = chatbot}, etapa 1);</li>
 * <li>grava contato, {@code formulario_id}, canal e UTM e sobe para a etapa 1 se estava abaixo. Se o lead
 * já passou da etapa 1 (o envio chegou antes), a captura é um fato atrasado e só completa o que falta;</li>
 * <li>grava o aceite em {@code lead_consentimentos} (idempotente) e pontua: sem respostas, só as regras
 * de {@code utm} somam.</li>
 * </ol>
 *
 * Roda dentro de {@link ConsumoDeEventos#consumir}, no tenant do envelope. O contato nunca vai para log.
 */
@Service
public class CapturaDoChatbot {

    /** Etapa do funil de quem deixou contato no chatbot (§2). */
    public static final int ETAPA = 1;

    private static final Logger LOG = LoggerFactory.getLogger(CapturaDoChatbot.class);

    private final ConsumoDeEventos consumo;
    private final IdentificacaoDeLeads identificacao;
    private final GravacaoDeLeads gravacao;
    private final RegistroDeConsentimentos consentimentos;
    private final PontuacaoDeLeads pontuacao;

    public CapturaDoChatbot(ConsumoDeEventos consumo, IdentificacaoDeLeads identificacao, GravacaoDeLeads gravacao,
            RegistroDeConsentimentos consentimentos, PontuacaoDeLeads pontuacao) {
        this.consumo = consumo;
        this.identificacao = identificacao;
        this.gravacao = gravacao;
        this.consentimentos = consentimentos;
        this.pontuacao = pontuacao;
    }

    /** {@code dados} de {@code landing.chatbot.lead_capturado} v1 ({@code landing.asyncapi.yaml}). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DadosDaCaptura(UUID sessaoId, UUID formularioId, UUID paginaId, Contato contato, Origem origem,
            Consentimento consentimento) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Contato(String nome, String email, String telefone) {
            @Override
            public String toString() {
                return "Contato[***]";
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Origem(String utmSource, String utmMedium, String utmCampaign) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Consentimento(Boolean aceito, OffsetDateTime aceitoEm) {
        }
    }

    @Transactional
    public void processar(EventoRecebido evento) {
        if (evento.versao() != 1) {
            // Mudança incompatível sobe a versão (§9.7): fica na DLQ até o consumidor aprender a nova
            throw new EventoInvalidoException("versão " + evento.versao() + " de " + evento.tipo()
                    + " não suportada (id " + evento.id() + ")");
        }
        DadosDaCaptura dados = consumo.dados(evento, DadosDaCaptura.class);
        exigirFormato(evento, dados);

        DadosDaCaptura.Contato contato = dados.contato();
        ChavesDeIdentidade chaves = ChavesDeIdentidade.daSessao(dados.sessaoId())
                .comContato(contato.email(), contato.telefone());
        if (!chaves.temContato()) {
            // O Landing só publica com e-mail ou telefone válido; sem contato não é lead (§2)
            throw new EventoInvalidoException("captura sem e-mail nem telefone utilizável (id " + evento.id() + ")");
        }
        Procedencia procedencia = Procedencia.doEvento(evento);

        Lead lead = identificacao.identificar(chaves)
                .map(achado -> atualizar(achado, dados, chaves, procedencia))
                .orElseGet(() -> criar(dados, chaves, procedencia));

        DadosDaCaptura.Consentimento aceite = dados.consentimento();
        boolean consentimentoNovo = consentimentos.registrar(lead, LeadConsentimento.doLanding(lead.getId(),
                OrigemConsentimento.CHATBOT_LEAD_CAPTURADO, true, aceite.aceitoEm(), null, dados.formularioId(),
                null, dados.sessaoId(), evento.id()));

        ResultadoPontuacao resultado = pontuacao.pontuar(lead);
        LOG.info("Captura do chatbot no lead {} (etapa {}, consentimento novo: {}, faixa {})", lead.getId(),
                lead.getEtapa(), consentimentoNovo, resultado.faixa().valor());
    }

    private Lead criar(DadosDaCaptura dados, ChavesDeIdentidade chaves, Procedencia procedencia) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, ETAPA);
        lead.vincularSessao(dados.sessaoId());
        lead.vincularFormulario(dados.formularioId(), CanalLead.CHATBOT);
        lead.atualizarContato(NormalizacaoDeContato.nome(dados.contato().nome()), chaves.email(), chaves.telefone(),
                null);
        DadosDaCaptura.Origem origem = origem(dados);
        lead.atualizarUtm(origem.utmSource(), origem.utmMedium(), origem.utmCampaign(), null, null);
        Lead criado = gravacao.criar(lead, procedencia);
        LOG.info("Lead {} criado pela captura do chatbot", criado.getId());
        return criado;
    }

    private Lead atualizar(LeadIdentificado achado, DadosDaCaptura dados, ChavesDeIdentidade chaves,
            Procedencia procedencia) {
        Lead lead = achado.lead();
        String nome = NormalizacaoDeContato.nome(dados.contato().nome());
        DadosDaCaptura.Origem origem = origem(dados);
        lead.vincularSessao(dados.sessaoId());
        if (lead.getEtapa() > ETAPA) {
            // O envio da mesma pessoa chegou antes: a captura só completa dados (§2)
            lead.completarContato(nome, chaves.email(), chaves.telefone(), null);
            lead.completarUtm(origem.utmSource(), origem.utmMedium(), origem.utmCampaign(), null, null);
        } else {
            lead.atualizarContato(nome, chaves.email(), chaves.telefone(), null);
            lead.atualizarUtm(origem.utmSource(), origem.utmMedium(), origem.utmCampaign(), null, null);
            lead.vincularFormulario(dados.formularioId(), CanalLead.CHATBOT);
        }
        gravacao.avancarEtapa(lead, ETAPA, procedencia);
        Lead gravado = gravacao.salvar(lead);
        LOG.info("Lead {} achado por {} na captura do chatbot", gravado.getId(), achado.por());
        return gravado;
    }

    private static DadosDaCaptura.Origem origem(DadosDaCaptura dados) {
        return dados.origem() != null ? dados.origem() : new DadosDaCaptura.Origem(null, null, null);
    }

    // Campos obrigatórios do schema DadosChatbotLeadCapturado; aceito é const true
    private static void exigirFormato(EventoRecebido evento, DadosDaCaptura dados) {
        String falta = null;
        if (dados == null) {
            falta = "dados";
        } else if (dados.sessaoId() == null) {
            falta = "sessaoId";
        } else if (dados.formularioId() == null) {
            falta = "formularioId";
        } else if (dados.contato() == null) {
            falta = "contato";
        } else if (dados.consentimento() == null || dados.consentimento().aceitoEm() == null) {
            falta = "consentimento.aceitoEm";
        } else if (!Boolean.TRUE.equals(dados.consentimento().aceito())) {
            falta = "consentimento.aceito = true";
        }
        if (falta != null) {
            throw new EventoInvalidoException("dados de " + evento.tipo() + " (id " + evento.id() + ") sem " + falta);
        }
    }
}
