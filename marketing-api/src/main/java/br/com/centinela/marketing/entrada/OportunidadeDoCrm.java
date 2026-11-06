package br.com.centinela.marketing.entrada;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.centinela.marketing.compartilhado.mensageria.ConsumoDeEventos;
import br.com.centinela.marketing.compartilhado.mensageria.EventoInvalidoException;
import br.com.centinela.marketing.compartilhado.mensageria.EventoRecebido;
import br.com.centinela.marketing.crm.ResponsavelDaOportunidade;
import br.com.centinela.marketing.entrada.IdentificacaoDeLeads.LeadIdentificado;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.OrigemLead;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.notificacoes.FatoDaTimeline;
import br.com.centinela.marketing.notificacoes.NotificacoesDeLead;

/**
 * {@code crm.oportunidade.criada} (norteador §5, passo 4; §6, Handoff para o CRM): o Landing criou a
 * oportunidade do envio no CRM, e o Marketing liga o lead a ela. Na chegada:
 *
 * <ol>
 * <li>só a de {@code origem = landing} interessa; as outras (criadas por usuário no CRM, por exemplo)
 * ficam processadas sem efeito;</li>
 * <li>acha o lead por {@code envio_id = origemModuloId}, senão por {@code crm_contato_id = contatoId};</li>
 * <li>se não achar, o evento chegou antes do {@code landing.formulario.recebido}: cria o lead na etapa 3,
 * {@code origem = landing_page}, com {@code envio_id}, os ids do CRM e a oportunidade, ainda sem contato.
 * Com {@code envio_id} e {@code envio_lido_em} nulo, ele fica com a leitura do envio pendente: a E5 o acha
 * pelo {@code envio_id} e o completa, ou o job 4.6 lê o envio no Landing se o evento do envio se perder;</li>
 * <li>grava {@code crm_oportunidade_id} (uma oportunidade nova do mesmo lead substitui a anterior, que fica
 * no {@code payload}), completa {@code crm_empresa_id}/{@code crm_contato_id} que faltarem, registra
 * {@code handoff_crm} e pede o registro na timeline da empresa (N1);</li>
 * <li>se o lead já tem vendedor, pede que ele vá ao CRM como responsável ({@link ResponsavelDaOportunidade},
 * C1, hoje pendente).</li>
 * </ol>
 *
 * Roda dentro de {@link ConsumoDeEventos#consumir}, no tenant do envelope. O mesmo evento (mesmo {@code id})
 * não roda duas vezes; um evento novo com a mesma oportunidade já ligada não muda nada.
 */
@Service
public class OportunidadeDoCrm {

    /** {@code origem} das oportunidades criadas pelo Landing a partir de um envio (Contrato §12.7). */
    public static final String ORIGEM_LANDING = "landing";

    /** Quem tem oportunidade criada pelo Landing enviou o formulário (§2). */
    public static final int ETAPA = 3;

    private static final Logger LOG = LoggerFactory.getLogger(OportunidadeDoCrm.class);

    /** {@code origemModuloId} é texto no contrato; só um UUID canônico pode ser um {@code envioId}. */
    private static final Pattern UUID_CANONICO = Pattern
            .compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ConsumoDeEventos consumo;
    private final IdentificacaoDeLeads identificacao;
    private final GravacaoDeLeads gravacao;
    private final LeadEventoRepository eventos;
    private final NotificacoesDeLead notificacoes;
    private final ResponsavelDaOportunidade responsavel;

    public OportunidadeDoCrm(ConsumoDeEventos consumo, IdentificacaoDeLeads identificacao, GravacaoDeLeads gravacao,
            LeadEventoRepository eventos, NotificacoesDeLead notificacoes, ResponsavelDaOportunidade responsavel) {
        this.consumo = consumo;
        this.identificacao = identificacao;
        this.gravacao = gravacao;
        this.eventos = eventos;
        this.notificacoes = notificacoes;
        this.responsavel = responsavel;
    }

    /**
     * {@code dados} de {@code crm.oportunidade.criada} v1 ({@code crm.asyncapi.yaml}), só com o que o
     * Marketing usa. O {@code titulo} fica de fora: não é guardado nem vai para log.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DadosDaOportunidade(UUID oportunidadeId, UUID empresaId, UUID contatoId, UUID responsavelId,
            String origem, String origemModuloId) {
    }

    @Transactional
    public void processar(EventoRecebido evento) {
        if (evento.versao() != 1) {
            // Mudança incompatível sobe a versão (§9.7): fica na DLQ até o consumidor aprender a nova
            throw new EventoInvalidoException("versão " + evento.versao() + " de " + evento.tipo()
                    + " não suportada (id " + evento.id() + ")");
        }
        DadosDaOportunidade dados = consumo.dados(evento, DadosDaOportunidade.class);
        if (dados == null) {
            throw new EventoInvalidoException("dados de " + evento.tipo() + " (id " + evento.id() + ") vazios");
        }
        if (!daLanding(dados.origem())) {
            LOG.info("Oportunidade {} com origem {} não vem do Landing; ignorada (id {})", dados.oportunidadeId(),
                    dados.origem(), evento.id());
            return;
        }
        exigirFormato(evento, dados);

        UUID envioId = envioDe(dados.origemModuloId());
        ChavesDeIdentidade chaves = ChavesDeIdentidade.doEnvio(envioId).comContatoCrm(dados.contatoId());
        if (chaves.envioId() == null && chaves.crmContatoId() == null) {
            // Sem envio nem contato não há lead a ligar, agora ou depois
            LOG.warn("Oportunidade {} do Landing sem envio (origemModuloId) nem contato; ignorada (id {})",
                    dados.oportunidadeId(), evento.id());
            return;
        }
        Procedencia procedencia = Procedencia.doEvento(evento);

        Optional<LeadIdentificado> achado = identificacao.identificar(chaves);
        Lead lead;
        UUID anterior;
        boolean ligou;
        if (achado.isPresent()) {
            lead = achado.get().lead();
            anterior = lead.getCrmOportunidadeId();
            // Só completa: a empresa e o contato do lead são os do envio (E5), que manda neles
            lead.vincularCrm(lead.getCrmEmpresaId() == null ? dados.empresaId() : null,
                    lead.getCrmContatoId() == null ? dados.contatoId() : null);
            ligou = lead.vincularOportunidade(dados.oportunidadeId());
            gravacao.avancarEtapa(lead, ETAPA, procedencia);
            lead = gravacao.salvar(lead);
        } else {
            anterior = null;
            lead = gravacao.criar(pendenteDoEnvio(envioId, dados), procedencia);
            ligou = true;
            LOG.info("Lead {} criado pela oportunidade {}, antes do envio (envio {})", lead.getId(),
                    dados.oportunidadeId(), envioId);
        }

        if (!ligou) {
            LOG.info("Lead {} já ligado à oportunidade {}; nada a fazer (id {})", lead.getId(), dados.oportunidadeId(),
                    evento.id());
            return;
        }

        String situacaoDoResponsavel = levarVendedor(lead, dados);
        eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.HANDOFF_CRM,
                payloadDoHandoff(procedencia, dados, anterior, achado, situacaoDoResponsavel)));
        boolean naTimeline = notificacoes.registrarNaTimeline(lead, FatoDaTimeline.OPORTUNIDADE_VINCULADA);
        LOG.info("Lead {} ligado à oportunidade {} (achado por {}, responsável no CRM: {}, timeline: {})",
                lead.getId(), dados.oportunidadeId(), achado.map(a -> a.por().name()).orElse("criado"),
                situacaoDoResponsavel, naTimeline);
    }

    /**
     * Lead que só existe pelo CRM, enquanto o envio não chega: etapa 3, ids do CRM e, com {@code envioId},
     * a leitura do envio pendente. Contato, respostas, origem e consentimento vêm do envio (E5 ou job 4.6).
     */
    private static Lead pendenteDoEnvio(UUID envioId, DadosDaOportunidade dados) {
        Lead lead = new Lead(OrigemLead.LANDING_PAGE, ETAPA);
        if (envioId != null) {
            // Formulário e canal vêm no landing.formulario.recebido
            lead.vincularEnvio(envioId, null, null);
        }
        lead.vincularCrm(dados.empresaId(), dados.contatoId());
        lead.vincularOportunidade(dados.oportunidadeId());
        return lead;
    }

    /** C1: só quando o lead já tem vendedor e a oportunidade ainda não está com ele. */
    private String levarVendedor(Lead lead, DadosDaOportunidade dados) {
        if (lead.getVendedorId() == null) {
            return "sem_vendedor";
        }
        if (lead.getVendedorId().equals(dados.responsavelId())) {
            return ResponsavelDaOportunidade.Situacao.GRAVADO.valor();
        }
        return responsavel.levarVendedor(dados.oportunidadeId(), lead.getVendedorId()).valor();
    }

    private static Map<String, Object> payloadDoHandoff(Procedencia procedencia, DadosDaOportunidade dados,
            UUID anterior, Optional<LeadIdentificado> achado, String situacaoDoResponsavel) {
        Map<String, Object> payload = procedencia.payload();
        payload.put("oportunidadeId", dados.oportunidadeId().toString());
        if (anterior != null) {
            payload.put("oportunidadeAnteriorId", anterior.toString());
        }
        achado.ifPresentOrElse(a -> payload.put("identificadoPor", a.por().name().toLowerCase(Locale.ROOT)),
                () -> payload.put("leadCriado", true));
        payload.put("responsavelNoCrm", situacaoDoResponsavel);
        return payload;
    }

    private static boolean daLanding(String origem) {
        return origem != null && ORIGEM_LANDING.equalsIgnoreCase(origem.strip());
    }

    private static UUID envioDe(String origemModuloId) {
        if (origemModuloId == null || !UUID_CANONICO.matcher(origemModuloId.strip()).matches()) {
            return null;
        }
        return UUID.fromString(origemModuloId.strip());
    }

    // Campos obrigatórios do schema que o Marketing usa
    private static void exigirFormato(EventoRecebido evento, DadosDaOportunidade dados) {
        String falta = dados.oportunidadeId() == null ? "oportunidadeId" : dados.empresaId() == null ? "empresaId" : null;
        if (falta != null) {
            throw new EventoInvalidoException("dados de " + evento.tipo() + " (id " + evento.id() + ") sem " + falta);
        }
    }
}
