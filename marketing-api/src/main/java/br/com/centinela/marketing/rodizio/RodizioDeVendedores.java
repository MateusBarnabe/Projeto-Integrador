package br.com.centinela.marketing.rodizio;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;
import br.com.centinela.marketing.notificacoes.NotificacoesDeLead;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;
import br.com.centinela.marketing.qualificacao.VendedorRodizio;
import br.com.centinela.marketing.qualificacao.VendedorRodizioRepository;
import br.com.centinela.marketing.rodizio.EscalaDoRodizio.VendedorDaEscala;
import br.com.centinela.marketing.rodizio.EscalaDoRodizio.VendedorGravado;
import br.com.centinela.marketing.rodizio.ResultadoAtribuicao.Situacao;

/**
 * Rodízio de vendedores (Q2, norteador §9.3). Entra o lead MQL (quente ou morno) com contato e sem
 * vendedor; recebe o vendedor ativo de {@code vendedores_rodizio} que recebeu lead há mais tempo
 * ({@code ultima_atribuicao_em} mais antiga, nunca atribuído primeiro). Grava {@code vendedor_id} e
 * {@code vendedor_atribuido_em}, registra {@code vendedor_atribuido} na timeline e notifica o
 * vendedor (N1; quente ou etapa 3 vai como prioritária). Com o rodízio desligado, fora do horário
 * comercial ou sem vendedor ativo, o lead fica "aguardando vendedor" e o job 4.4
 * ({@link AtribuicaoPendente}) tenta de novo.
 *
 * <p>Chamado pela E5 e pelo job 4.6 depois de pontuar ({@code PontuacaoDeLeads.pontuar}). Roda na
 * transação de quem chama, no tenant do lead. A escala vem do cadastro manual de
 * {@code vendedores_rodizio}: listar os vendedores no identity ainda não existe (X7).
 *
 * <p>Fora daqui, por depender do CRM (Grupo 6): levar o vendedor como responsável da oportunidade,
 * quando o lead já tem {@code crm_oportunidade_id} (§9.6, cliente C1); e o transbordo por
 * {@code minutos_transbordo}, que precisa do primeiro contato registrado no CRM (§4.4).
 */
@Service
public class RodizioDeVendedores {

    static final String FORMA_RODIZIO = "rodizio";
    static final String FORMA_MANUAL = "manual";

    private final EscalaDoRodizio escala;
    private final HorarioDeAtendimento horario;
    private final QualificacaoConfiguracaoRepository configuracoes;
    private final VendedorRodizioRepository vendedores;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;
    private final NotificacoesDeLead notificacoes;

    RodizioDeVendedores(EscalaDoRodizio escala, HorarioDeAtendimento horario,
            QualificacaoConfiguracaoRepository configuracoes, VendedorRodizioRepository vendedores,
            LeadRepository leads, LeadEventoRepository eventos, NotificacoesDeLead notificacoes) {
        this.escala = escala;
        this.horario = horario;
        this.configuracoes = configuracoes;
        this.vendedores = vendedores;
        this.leads = leads;
        this.eventos = eventos;
        this.notificacoes = notificacoes;
    }

    /**
     * Atribui um vendedor ao lead pelo rodízio, se ele entra no rodízio e ainda não tem vendedor.
     * Lead com vendedor não troca (a troca é a {@link #reatribuir reatribuição manual}).
     */
    @Transactional
    public ResultadoAtribuicao atribuir(Lead lead) {
        exigirLeadDoTenantAtual(lead);
        if (!entraNoRodizio(lead)) {
            return ResultadoAtribuicao.de(Situacao.NAO_ELEGIVEL);
        }
        if (lead.getVendedorId() != null) {
            return new ResultadoAtribuicao(Situacao.JA_TINHA_VENDEDOR, lead.getVendedorId());
        }
        // Trava o lead e confere o banco: o job pode ter atribuído depois que quem chama o leu
        Optional<VendedorGravado> gravado = escala.travarLead(lead.getId());
        if (gravado.isPresent() && gravado.get().vendedorId() != null) {
            lead.atribuirVendedor(gravado.get().vendedorId(), gravado.get().atribuidoEm());
            return new ResultadoAtribuicao(Situacao.JA_TINHA_VENDEDOR, lead.getVendedorId());
        }

        QualificacaoConfiguracao configuracao = configuracoes.findFirstBy()
                .orElseGet(QualificacaoConfiguracao::padrao);
        if (!configuracao.isRodizioAtivo()) {
            return ResultadoAtribuicao.de(Situacao.AGUARDANDO_RODIZIO_DESLIGADO);
        }
        if (!horario.aberto(configuracao)) {
            return ResultadoAtribuicao.de(Situacao.AGUARDANDO_FORA_DO_HORARIO);
        }
        Optional<VendedorDaEscala> proximo = escala.travarProximoVendedor();
        if (proximo.isEmpty()) {
            return ResultadoAtribuicao.de(Situacao.AGUARDANDO_SEM_VENDEDOR);
        }

        atribuirAgora(lead, proximo.get(), FORMA_RODIZIO);
        return new ResultadoAtribuicao(Situacao.ATRIBUIDO, lead.getVendedorId());
    }

    /**
     * Reatribuição manual ({@code PUT /api/marketing/leads/{id}/vendedor}): troca o vendedor de
     * qualquer lead do tenant por um vendedor cadastrado na escala, ativo ou não. Vale como
     * atribuição na fila do rodízio e notifica o novo vendedor.
     *
     * @throws LeadNaoEncontrado    lead inexistente, excluído ou de outro tenant
     * @throws VendedorForaDaEscala vendedor que não está em {@code vendedores_rodizio}
     */
    @Transactional
    public Lead reatribuir(UUID leadId, UUID vendedorId) {
        // Trava antes de ler: o lead lido já traz o que o job tiver atribuído antes da trava
        if (escala.travarLead(leadId).isEmpty()) {
            throw new LeadNaoEncontrado();
        }
        Lead lead = leads.findById(leadId).orElseThrow(LeadNaoEncontrado::new);
        VendedorRodizio vendedor = vendedores.findByVendedorId(vendedorId).orElseThrow(VendedorForaDaEscala::new);
        if (vendedorId.equals(lead.getVendedorId())) {
            return lead;
        }
        atribuirAgora(lead, new VendedorDaEscala(vendedor.getId(), vendedorId), FORMA_MANUAL);
        return lead;
    }

    /** Entra no rodízio: MQL (quente ou morno), com e-mail, telefone ou WhatsApp, e não anonimizado. */
    public static boolean entraNoRodizio(Lead lead) {
        FaixaQualificacao faixa = lead.getFaixaQualificacao();
        return (faixa == FaixaQualificacao.MQL_QUENTE || faixa == FaixaQualificacao.MQL_MORNO)
                && !lead.isAnonimizado()
                && (temTexto(lead.getEmail()) || temTexto(lead.getTelefone()) || temTexto(lead.getWhatsapp()));
    }

    /** "Aguardando vendedor" (§9.3): entra no rodízio e ainda não tem vendedor. */
    public static boolean aguardandoVendedor(Lead lead) {
        return lead.getVendedorId() == null && entraNoRodizio(lead);
    }

    private void atribuirAgora(Lead lead, VendedorDaEscala vendedor, String forma) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID anterior = lead.getVendedorId();
        lead.atribuirVendedor(vendedor.vendedorId(), agora);
        escala.registrarAtribuicao(vendedor.id(), agora);
        leads.save(lead);
        eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.VENDEDOR_ATRIBUIDO,
                vendedorAtribuido(vendedor.vendedorId(), anterior, forma, lead)));
        notificacoes.notificarNovoLead(lead);
    }

    // Sem dado pessoal: ids do identity, a forma e a faixa
    private static Map<String, Object> vendedorAtribuido(UUID vendedorId, UUID anterior, String forma, Lead lead) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("vendedorId", vendedorId.toString());
        payload.put("vendedorAnteriorId", anterior == null ? null : anterior.toString());
        payload.put("forma", forma);
        payload.put("faixa", lead.getFaixaQualificacao() == null ? null : lead.getFaixaQualificacao().valor());
        return payload;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static void exigirLeadDoTenantAtual(Lead lead) {
        if (lead == null || lead.getId() == null || lead.getTenantId() == null) {
            throw new IllegalStateException("Grave o lead antes de atribuir vendedor.");
        }
        if (!lead.getTenantId().equals(TenantContexto.exigir())) {
            throw new IllegalStateException("Lead de outro tenant.");
        }
    }

    /** Lead que não existe no tenant do token: a rota responde 404, nunca 403. */
    public static class LeadNaoEncontrado extends RuntimeException {
        LeadNaoEncontrado() {
            super("Lead não encontrado.");
        }
    }

    /** O vendedor informado não está cadastrado em {@code vendedores_rodizio}: a rota responde 422. */
    public static class VendedorForaDaEscala extends RuntimeException {
        VendedorForaDaEscala() {
            super("O vendedor não está cadastrado no rodízio.");
        }
    }
}
