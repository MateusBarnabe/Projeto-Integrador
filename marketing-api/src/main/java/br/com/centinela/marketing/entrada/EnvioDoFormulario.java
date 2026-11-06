package br.com.centinela.marketing.entrada;

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
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * {@code landing.formulario.recebido} (E5, norteador §5, passo 3): o visitante enviou o formulário, por
 * qualquer canal, e o Landing já criou empresa, contato e oportunidade no CRM. O evento traz só ids
 * e canal; contato, respostas, origem e consentimento vêm da leitura do envio. Na chegada:
 *
 * <ol>
 * <li>lê {@code GET /api/landing/envios/{id}} ({@link LeituraDoEnvio#ler}), antes de travar qualquer
 * linha;</li>
 * <li>acha o lead por {@code envio_id} (a E6 cria o lead quando {@code crm.oportunidade.criada} chega
 * antes), {@code sessao_id} (quando o Landing mandar no evento), {@code crm_contato_id} e, com o envio
 * lido, e-mail ou telefone normalizados; senão, cria ({@code origem = landing_page}, etapa 3);</li>
 * <li>grava {@code envio_id}, {@code formulario_id}, {@code canal} e os ids do CRM e sobe para a etapa
 * 3 (nunca desce);</li>
 * <li>com o envio lido, {@link LeituraDoEnvio#aplicar}: merge de duplicados, respostas, origem,
 * consentimento, pontuação e rodízio. Sem ele (Landing fora do ar ou com erro), o lead fica gravado
 * com a leitura pendente ({@code envio_lido_em} nulo), a falha é contada e o job 4.6 lê depois. A
 * mensagem é processada do mesmo jeito: não vai para a DLQ.</li>
 * </ol>
 *
 * <p>Se o envio do lead já foi lido (o job chegou antes, ou o evento repetiu com outro id), só os ids
 * são completados. Roda dentro de {@link ConsumoDeEventos#consumir}, no tenant do envelope.
 */
@Service
public class EnvioDoFormulario {

    /** Etapa do funil de quem enviou o formulário (§2). */
    public static final int ETAPA = 3;

    private static final Logger LOG = LoggerFactory.getLogger(EnvioDoFormulario.class);

    private final ConsumoDeEventos consumo;
    private final LeituraDoEnvio leitura;
    private final IdentificacaoDeLeads identificacao;
    private final GravacaoDeLeads gravacao;
    private final TravasDeLeads travas;
    private final LeadRepository leads;

    public EnvioDoFormulario(ConsumoDeEventos consumo, LeituraDoEnvio leitura, IdentificacaoDeLeads identificacao,
            GravacaoDeLeads gravacao, TravasDeLeads travas, LeadRepository leads) {
        this.consumo = consumo;
        this.leitura = leitura;
        this.identificacao = identificacao;
        this.gravacao = gravacao;
        this.travas = travas;
        this.leads = leads;
    }

    /**
     * {@code dados} de {@code landing.formulario.recebido} v1 ({@code landing.asyncapi.yaml}).
     * {@code sessaoId} ainda não está no contrato (pedido ao Landing, X1): é lido quando vier.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DadosDoEnvio(UUID envioId, UUID formularioId, String canal, UUID empresaId, UUID contatoId,
            UUID sessaoId) {
    }

    @Transactional
    public void processar(EventoRecebido evento) {
        if (evento.versao() != 1) {
            // Mudança incompatível sobe a versão (§9.7): fica na DLQ até o consumidor aprender a nova
            throw new EventoInvalidoException("versão " + evento.versao() + " de " + evento.tipo()
                    + " não suportada (id " + evento.id() + ")");
        }
        DadosDoEnvio dados = consumo.dados(evento, DadosDoEnvio.class);
        exigirFormato(evento, dados);
        Procedencia procedencia = Procedencia.doEvento(evento);

        LeituraDoEnvio.Leitura lida = leitura.ler(dados.envioId());
        ChavesDeIdentidade chaves = ChavesDeIdentidade.doEnvio(dados.envioId())
                .comSessao(dados.sessaoId())
                .comContatoCrm(dados.contatoId());
        if (lida.lido()) {
            // Liga direto ao lead mais antigo de mesmo contato, sem criar um para juntar logo depois
            chaves = chaves.comContato(lida.envio().emailDoContato(), lida.envio().telefoneDoContato());
        }
        CanalLead canal = LeituraDoEnvio.canal(dados.canal(), null);

        Lead lead = identificacao.identificar(chaves)
                .map(achado -> atualizar(achado, dados, canal, procedencia))
                .orElseGet(() -> criar(dados, canal, procedencia));

        if (!lead.leituraDoEnvioPendente()) {
            LOG.info("Envio {} do lead {} já estava lido; só os ids foram completados", dados.envioId(),
                    lead.getId());
            return;
        }
        if (lida.lido()) {
            leitura.aplicar(lead, lida.envio(), procedencia);
        } else {
            leitura.registrarFalha(lead, lida.motivo(), procedencia);
            LOG.info("Lead {} gravado com a leitura do envio {} pendente ({})", lead.getId(), dados.envioId(),
                    lida.motivo());
        }
    }

    private Lead criar(DadosDoEnvio dados, CanalLead canal, Procedencia procedencia) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, ETAPA);
        lead.vincularEnvioDoFormulario(dados.envioId(), dados.formularioId(), canal);
        lead.vincularSessao(dados.sessaoId());
        lead.vincularCrm(dados.empresaId(), dados.contatoId());
        Lead criado = gravacao.criar(lead, procedencia);
        LOG.info("Lead {} criado pelo envio {}", criado.getId(), dados.envioId());
        return criado;
    }

    private Lead atualizar(LeadIdentificado achado, DadosDoEnvio dados, CanalLead canal, Procedencia procedencia) {
        Lead lead = achado.lead();
        // O job 4.6 ou a E6 podem estar com o lead: espera e relê antes de mudar
        travas.travar(lead);
        vincularSessaoSeLivre(lead, dados.sessaoId());
        lead.vincularEnvioDoFormulario(dados.envioId(), dados.formularioId(), canal);
        lead.vincularCrm(dados.empresaId(), dados.contatoId());
        gravacao.avancarEtapa(lead, ETAPA, procedencia);
        Lead gravado = gravacao.salvar(lead);
        LOG.info("Lead {} achado por {} no envio {}", gravado.getId(), achado.por(), dados.envioId());
        return gravado;
    }

    // A sessão é única por tenant: se ela já é de outro lead, o merge por contato é que os liga
    private void vincularSessaoSeLivre(Lead lead, UUID sessaoId) {
        if (sessaoId == null || sessaoId.equals(lead.getSessaoId())) {
            return;
        }
        boolean deOutro = leads.findBySessaoId(sessaoId).filter(outro -> !outro.getId().equals(lead.getId()))
                .isPresent();
        if (!deOutro) {
            lead.vincularSessao(sessaoId);
        }
    }

    // Campos obrigatórios do schema FormularioRecebido
    private static void exigirFormato(EventoRecebido evento, DadosDoEnvio dados) {
        String falta = null;
        if (dados == null) {
            falta = "dados";
        } else if (dados.envioId() == null) {
            falta = "envioId";
        } else if (dados.formularioId() == null) {
            falta = "formularioId";
        } else if (dados.canal() == null || dados.canal().isBlank()) {
            falta = "canal";
        }
        if (falta != null) {
            throw new EventoInvalidoException("dados de " + evento.tipo() + " (id " + evento.id() + ") sem " + falta);
        }
    }
}
