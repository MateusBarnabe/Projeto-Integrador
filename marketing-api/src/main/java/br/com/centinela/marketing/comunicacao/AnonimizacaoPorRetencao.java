package br.com.centinela.marketing.comunicacao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Job 4.3, anonimização por retenção LGPD (E10, norteador §4.3): seleciona leads não anonimizados
 * cujo prazo de retenção após {@code esgotado_em} ou {@code opt_out_em} venceu conforme
 * {@code ciclo_vida_configuracoes} do tenant, zera os dados pessoais identificáveis no lead e o
 * IP/User-Agent em {@code lead_consentimentos}, marca {@code anonimizado = true} e grava
 * {@code anonimizado} em {@code lead_eventos}. Nunca apaga a linha do lead.
 */
@Service
public class AnonimizacaoPorRetencao {

    private static final Logger LOG = LoggerFactory.getLogger(AnonimizacaoPorRetencao.class);

    static final int LOTE = 100;

    private final TravasDeComunicacao travas;
    private final CicloVidaConfiguracaoRepository configuracoes;
    private final LeadRepository leads;
    private final LeadConsentimentoRepository consentimentos;
    private final LeadEventoRepository eventos;
    private final TransactionTemplate transacao;

    AnonimizacaoPorRetencao(TravasDeComunicacao travas, CicloVidaConfiguracaoRepository configuracoes,
            LeadRepository leads, LeadConsentimentoRepository consentimentos, LeadEventoRepository eventos,
            TransactionTemplate transacao) {
        this.travas = travas;
        this.configuracoes = configuracoes;
        this.leads = leads;
        this.consentimentos = consentimentos;
        this.eventos = eventos;
        this.transacao = transacao;
    }

    public int anonimizarVencidos() {
        int totalAnonimizados = 0;
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        for (UUID tenant : travas.tenantsComLeadsParaRetencao()) {
            try {
                Integer noTenant = TenantContexto.calcular(tenant, () -> anonimizarNoTenant(agora));
                totalAnonimizados += noTenant == null ? 0 : noTenant;
            } catch (RuntimeException erro) {
                LOG.error("Anonimização falhou no tenant {}: {}", tenant, erro.getClass().getSimpleName());
            }
        }
        if (totalAnonimizados > 0) {
            LOG.info("Anonimização por retenção LGPD: {} lead(s) anonimizado(s)", totalAnonimizados);
        }
        return totalAnonimizados;
    }

    private int anonimizarNoTenant(OffsetDateTime agora) {
        CicloVidaConfiguracao ciclo = configuracoes.findFirstBy().orElseGet(CicloVidaConfiguracao::padrao);
        OffsetDateTime corteEsgotamento = agora.minusMonths(ciclo.getMesesRetencaoPosEsgotamento());
        OffsetDateTime corteOptOut = agora.minusMonths(ciclo.getMesesRetencaoPosOptout());

        List<UUID> vencidos = travas.leadsComPrazoDeRetencaoVencido(corteEsgotamento, corteOptOut, LOTE);
        int anonimizados = 0;
        for (UUID leadId : vencidos) {
            Boolean anonimizou = transacao.execute(s -> anonimizarUm(leadId, corteEsgotamento, corteOptOut));
            if (Boolean.TRUE.equals(anonimizou)) {
                anonimizados++;
            }
        }
        return anonimizados;
    }

    private boolean anonimizarUm(UUID leadId, OffsetDateTime corteEsgotamento, OffsetDateTime corteOptOut) {
        if (!travas.travarParaAnonimizacao(leadId, corteEsgotamento, corteOptOut)) {
            return false;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.isAnonimizado()) {
            return false;
        }
        String motivo = lead.getStatusAquecimento().valor();
        lead.anonimizar();
        leads.save(lead);

        List<LeadConsentimento> registros = consentimentos
                .findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(lead.getId());
        for (LeadConsentimento registro : registros) {
            if (registro.getIp() != null || registro.getUserAgent() != null) {
                registro.anonimizar();
                consentimentos.save(registro);
            }
        }

        eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.ANONIMIZADO,
                Map.of("motivo", motivo)));
        return true;
    }
}
