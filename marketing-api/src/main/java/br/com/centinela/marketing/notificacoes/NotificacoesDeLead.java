package br.com.centinela.marketing.notificacoes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.plataforma.CategoriaNotificacao;
import br.com.centinela.marketing.compartilhado.plataforma.PedidosAPlataforma;
import br.com.centinela.marketing.compartilhado.plataforma.PedidosAPlataforma.Notificacao;
import br.com.centinela.marketing.compartilhado.plataforma.PedidosAPlataforma.RegistroDeTimeline;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;

/**
 * Pedidos à plataforma sobre um lead (N1, norteador §8): a notificação {@code NOVO_LEAD} para o
 * vendedor e o registro na timeline da empresa do CRM. Chamado pelo rodízio (Q2), pelo consumo do
 * envio (E5) e pela ligação com a oportunidade (E6).
 *
 * <p>Roda na transação de quem chama (ou abre uma) e no tenant do contexto, que precisa ser o do
 * lead: os pedidos só saem depois do commit. Título e texto não levam dado pessoal do lead (nome,
 * e-mail, telefone, respostas): só faixa, pontuação, etapa e campanha. Quem quiser o resto abre o
 * lead pela {@code rota}.
 */
@Service
public class NotificacoesDeLead {

    /** Rota da notificação, relativa ao front do módulo (Contrato §12.2). */
    static final String ROTA_DO_LEAD = "/leads/";

    /** Rota da timeline, com o prefixo do front, como no exemplo de {@code DadosTimeline.rota}. */
    static final String ROTA_DO_LEAD_NA_CASCA = "/modulos/marketing/leads/";

    static final String TITULO = "Novo lead";
    static final String TITULO_PRIORITARIO = "Novo lead prioritário";

    private final PedidosAPlataforma plataforma;
    private final DestinatarioSemVendedor semVendedor;

    public NotificacoesDeLead(PedidosAPlataforma plataforma, DestinatarioSemVendedor semVendedor) {
        this.plataforma = plataforma;
        this.semVendedor = semVendedor;
    }

    /**
     * Notifica o vendedor do lead com a categoria {@code NOVO_LEAD} e {@code rota: "/leads/{id}"}.
     * Sem vendedor, o destinatário sai de {@link DestinatarioSemVendedor} (hoje, ninguém).
     *
     * @return {@code true} se o pedido foi enfileirado para depois do commit
     */
    @Transactional
    public boolean notificarNovoLead(Lead lead) {
        exigirLeadDoTenantAtual(lead);
        Optional<UUID> destinatario = Optional.ofNullable(lead.getVendedorId())
                .or(() -> semVendedor.escolher(lead));
        if (destinatario.isEmpty()) {
            return false;
        }
        plataforma.criarNotificacao(new Notificacao(destinatario.get(), CategoriaNotificacao.NOVO_LEAD,
                prioritaria(lead) ? TITULO_PRIORITARIO : TITULO, descrever(lead), ROTA_DO_LEAD + lead.getId()));
        return true;
    }

    /**
     * Registra um fato do lead na timeline da empresa do CRM. Só existe empresa depois do envio
     * (etapa 3): lead sem {@code crm_empresa_id} não gera pedido.
     *
     * @return {@code true} se o pedido foi enfileirado para depois do commit
     */
    @Transactional
    public boolean registrarNaTimeline(Lead lead, FatoDaTimeline fato) {
        exigirLeadDoTenantAtual(lead);
        if (lead.getCrmEmpresaId() == null) {
            return false;
        }
        String texto = fato.resumo() + " (" + descrever(lead) + ")";
        plataforma.registrarNaTimeline(new RegistroDeTimeline(lead.getCrmEmpresaId(), fato.tipoDoFato(), texto,
                ROTA_DO_LEAD_NA_CASCA + lead.getId()));
        return true;
    }

    /**
     * Notificação prioritária: lead que enviou o formulário (etapa 3, norteador §8) ou MQL quente
     * (§9.3). O contrato do identity ainda não tem campo de prioridade, então ela vai no título.
     */
    public static boolean prioritaria(Lead lead) {
        return lead.getEtapa() == Lead.ETAPA_MAXIMA || lead.getFaixaQualificacao() == FaixaQualificacao.MQL_QUENTE;
    }

    /** Ex.: "MQL quente, 85 pontos · enviou o formulário · campanha black-friday". Sem dado pessoal. */
    static String descrever(Lead lead) {
        List<String> partes = new ArrayList<>();
        if (lead.getFaixaQualificacao() != null) {
            partes.add(nomeDaFaixa(lead.getFaixaQualificacao())
                    + (lead.getPontuacao() == null ? "" : ", " + lead.getPontuacao() + " pontos"));
        }
        partes.add(nomeDaEtapa(lead.getEtapa()));
        if (lead.getUtmCampaign() != null && !lead.getUtmCampaign().isBlank()) {
            partes.add("campanha " + lead.getUtmCampaign().strip());
        }
        return String.join(" · ", partes);
    }

    private static String nomeDaFaixa(FaixaQualificacao faixa) {
        return switch (faixa) {
            case MQL_QUENTE -> "MQL quente";
            case MQL_MORNO -> "MQL morno";
            case NUTRICAO -> "Nutrição";
        };
    }

    private static String nomeDaEtapa(int etapa) {
        return switch (etapa) {
            case 0 -> "lead do Meta Ads";
            case 1 -> "contato pelo chatbot";
            case 2 -> "preencheu parte do formulário";
            default -> "enviou o formulário";
        };
    }

    private static void exigirLeadDoTenantAtual(Lead lead) {
        if (lead == null || lead.getId() == null || lead.getTenantId() == null) {
            throw new IllegalStateException("Grave o lead antes de pedir algo à plataforma.");
        }
        if (!lead.getTenantId().equals(TenantContexto.exigir())) {
            throw new IllegalStateException("Lead de outro tenant.");
        }
    }
}
