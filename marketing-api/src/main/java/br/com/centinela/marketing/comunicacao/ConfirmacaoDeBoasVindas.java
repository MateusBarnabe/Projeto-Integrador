package br.com.centinela.marketing.comunicacao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.StatusAquecimento;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Job 4.5, confirmação de boas-vindas (E8, norteador §4.5): envia uma única vez por lead o modelo
 * {@code boas_vindas} ativo do tenant para quem enviou o formulário (etapa 3), tem e-mail, não está
 * anonimizado e não fez opt-out. Recebe mesmo sem {@code consentimento_marketing}, porque confirma o
 * pedido que a própria pessoa fez.
 *
 * <p>Em falha de SMTP, grava {@code lead_comunicacoes} com {@code status = 'falhou'} e tenta na
 * execução seguinte; depois de {@value #MAXIMO_FALHAS} falhas do mesmo lead, desiste.
 */
@Service
public class ConfirmacaoDeBoasVindas {

    private static final Logger LOG = LoggerFactory.getLogger(ConfirmacaoDeBoasVindas.class);

    public static final int MAXIMO_FALHAS = 3;
    static final int LOTE = 50;

    private final TravasDeComunicacao travas;
    private final ModeloMensagemRepository modelos;
    private final LeadComunicacaoRepository comunicacoes;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;
    private final EnviadorDeEmail enviador;
    private final TransactionTemplate transacao;

    ConfirmacaoDeBoasVindas(TravasDeComunicacao travas, ModeloMensagemRepository modelos,
            LeadComunicacaoRepository comunicacoes, LeadRepository leads, LeadEventoRepository eventos,
            EnviadorDeEmail enviador, TransactionTemplate transacao) {
        this.travas = travas;
        this.modelos = modelos;
        this.comunicacoes = comunicacoes;
        this.leads = leads;
        this.eventos = eventos;
        this.enviador = enviador;
        this.transacao = transacao;
    }

    public record Resultado(int enviados, int falhas) {
    }

    enum Situacao {
        ENVIADO, FALHOU, PULADO
    }

    public Resultado enviarPendentes() {
        int enviados = 0;
        int falhas = 0;
        for (UUID tenant : travas.tenantsComBoasVindasPendente(MAXIMO_FALHAS)) {
            List<UUID> pendentes;
            try {
                pendentes = TenantContexto.calcular(tenant,
                        () -> travas.leadsComBoasVindasPendente(MAXIMO_FALHAS, LOTE));
            } catch (RuntimeException erro) {
                LOG.error("Boas-vindas: não listou os leads do tenant {}: {}", tenant, erro.getClass().getSimpleName());
                continue;
            }
            for (UUID leadId : pendentes) {
                switch (enviarUm(tenant, leadId)) {
                    case ENVIADO -> enviados++;
                    case FALHOU -> falhas++;
                    case PULADO -> { }
                }
            }
        }
        if (enviados + falhas > 0) {
            LOG.info("Confirmação de boas-vindas: {} enviado(s), {} falha(s)", enviados, falhas);
        }
        return new Resultado(enviados, falhas);
    }

    private Situacao enviarUm(UUID tenant, UUID leadId) {
        try {
            Situacao situacao = TenantContexto.calcular(tenant, () -> transacao.execute(s -> enviarNoTenant(leadId)));
            return situacao == null ? Situacao.PULADO : situacao;
        } catch (RuntimeException erro) {
            LOG.error("Boas-vindas do lead {} falhou: {}", leadId, erro.getClass().getSimpleName());
            return Situacao.PULADO;
        }
    }

    private Situacao enviarNoTenant(UUID leadId) {
        if (!travas.travarParaBoasVindas(leadId, MAXIMO_FALHAS)) {
            return Situacao.PULADO;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.isAnonimizado() || lead.getBoasVindasEnviadaEm() != null
                || lead.getStatusAquecimento() == StatusAquecimento.OPT_OUT
                || lead.getEmail() == null || lead.getEmail().isBlank()) {
            return Situacao.PULADO;
        }
        ModeloMensagem modelo = modelos.findFirstByTipoAndAtivoTrue(TipoModeloMensagem.BOAS_VINDAS)
                .orElseGet(() -> modelos.save(ModeloMensagem.boasVindasPadrao()));
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            enviador.enviar(modelo, lead);
            LeadComunicacao comunicacao = comunicacoes.save(LeadComunicacao.enviada(lead.getId(), modelo.getId(), agora));
            lead.registrarBoasVindas(agora);
            leads.save(lead);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tipo", TipoModeloMensagem.BOAS_VINDAS.valor());
            payload.put("modeloId", modelo.getId().toString());
            payload.put("comunicacaoId", comunicacao.getId().toString());
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.COMUNICACAO_ENVIADA, payload));
            return Situacao.ENVIADO;
        } catch (RuntimeException falhaEnvio) {
            LOG.warn("Falha no envio SMTP de boas-vindas para o lead {}: {}", leadId,
                    falhaEnvio.getClass().getSimpleName());
            comunicacoes.save(LeadComunicacao.falhou(lead.getId(), modelo.getId(), agora));
            return Situacao.FALHOU;
        }
    }
}
