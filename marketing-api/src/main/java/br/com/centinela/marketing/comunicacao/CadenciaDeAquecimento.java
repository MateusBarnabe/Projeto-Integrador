package br.com.centinela.marketing.comunicacao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Job 4.2, comunicação de aquecimento (E9, norteador §4.2): envia a cadência de modelos
 * {@code aquecimento} para leads nas etapas frias (0 e 1) com {@code status_aquecimento = 'ativo'},
 * {@code consentimento_marketing = true} e e-mail preenchido. Confere o opt-out antes de enviar e
 * marca {@code esgotado} quando atinge {@code max_tentativas_aquecimento} ou o fim da sequência.
 */
@Service
public class CadenciaDeAquecimento {

    private static final Logger LOG = LoggerFactory.getLogger(CadenciaDeAquecimento.class);

    static final int LOTE = 100;

    private final TravasDeComunicacao travas;
    private final ModeloMensagemRepository modelos;
    private final CicloVidaConfiguracaoRepository configuracoes;
    private final LeadComunicacaoRepository comunicacoes;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;
    private final EnviadorDeEmail enviador;
    private final TransactionTemplate transacao;

    CadenciaDeAquecimento(TravasDeComunicacao travas, ModeloMensagemRepository modelos,
            CicloVidaConfiguracaoRepository configuracoes, LeadComunicacaoRepository comunicacoes,
            LeadRepository leads, LeadEventoRepository eventos, EnviadorDeEmail enviador,
            TransactionTemplate transacao) {
        this.travas = travas;
        this.modelos = modelos;
        this.configuracoes = configuracoes;
        this.comunicacoes = comunicacoes;
        this.leads = leads;
        this.eventos = eventos;
        this.enviador = enviador;
        this.transacao = transacao;
    }

    public record Resultado(int enviados, int esgotados, int falhas) {
    }

    enum Situacao {
        ENVIADO, ENVIADO_E_ESGOTADO, ESGOTADO, FALHOU, AGUARDANDO_PRAZO, PULADO
    }

    public Resultado executarCadencia() {
        int enviados = 0;
        int esgotados = 0;
        int falhas = 0;
        for (UUID tenant : travas.tenantsComAquecimentoAtivo()) {
            List<UUID> elegiveis;
            try {
                elegiveis = TenantContexto.calcular(tenant, () -> travas.leadsElegiveisParaAquecimento(LOTE));
            } catch (RuntimeException erro) {
                LOG.error("Aquecimento: não listou os leads do tenant {}: {}", tenant, erro.getClass().getSimpleName());
                continue;
            }
            for (UUID leadId : elegiveis) {
                switch (processarUm(tenant, leadId)) {
                    case ENVIADO -> enviados++;
                    case ENVIADO_E_ESGOTADO -> {
                        enviados++;
                        esgotados++;
                    }
                    case ESGOTADO -> esgotados++;
                    case FALHOU -> falhas++;
                    case AGUARDANDO_PRAZO, PULADO -> { }
                }
            }
        }
        if (enviados + esgotados + falhas > 0) {
            LOG.info("Cadência de aquecimento: {} enviado(s), {} esgotado(s), {} falha(s)", enviados, esgotados,
                    falhas);
        }
        return new Resultado(enviados, esgotados, falhas);
    }

    private Situacao processarUm(UUID tenant, UUID leadId) {
        try {
            Situacao situacao = TenantContexto.calcular(tenant, () -> transacao.execute(s -> processarNoTenant(leadId)));
            return situacao == null ? Situacao.PULADO : situacao;
        } catch (RuntimeException erro) {
            LOG.error("Aquecimento do lead {} falhou: {}", leadId, erro.getClass().getSimpleName());
            return Situacao.PULADO;
        }
    }

    private Situacao processarNoTenant(UUID leadId) {
        if (!travas.travarParaAquecimento(leadId)) {
            return Situacao.PULADO;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null || lead.isAnonimizado() || !lead.isConsentimentoMarketing()
                || lead.getStatusAquecimento() != StatusAquecimento.ATIVO
                || (lead.getEtapa() != 0 && lead.getEtapa() != 1)
                || lead.getEmail() == null || lead.getEmail().isBlank()) {
            return Situacao.PULADO;
        }

        CicloVidaConfiguracao ciclo = configuracoes.findFirstBy().orElseGet(CicloVidaConfiguracao::padrao);
        int maxTentativas = ciclo.getMaxTentativasAquecimento();
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);

        if (lead.getTentativasAquecimento() >= maxTentativas) {
            lead.esgotarAquecimento(agora);
            leads.save(lead);
            return Situacao.ESGOTADO;
        }

        List<ModeloMensagem> ativos = modelos
                .findByTipoAndAtivoTrueOrderByOrdemSequenciaAscCriadoEmAsc(TipoModeloMensagem.AQUECIMENTO);
        int proximaOrdem = lead.getTentativasAquecimento() + 1;
        Optional<ModeloMensagem> proximo = escolherModelo(ativos, lead.getEtapa(), proximaOrdem);
        if (proximo.isEmpty()) {
            lead.esgotarAquecimento(agora);
            leads.save(lead);
            return Situacao.ESGOTADO;
        }

        ModeloMensagem modelo = proximo.get();
        int diasEspera = modelo.getDiasAposAnterior() == null ? 0 : modelo.getDiasAposAnterior();
        OffsetDateTime referencia = lead.getUltimaComunicacaoEm() != null
                ? lead.getUltimaComunicacaoEm()
                : lead.getCriadoEm();
        if (diasEspera > 0 && referencia != null
                && referencia.plus(diasEspera, ChronoUnit.DAYS).isAfter(agora)) {
            return Situacao.AGUARDANDO_PRAZO;
        }

        boolean haSeguinte = escolherModelo(ativos, lead.getEtapa(), proximaOrdem + 1).isPresent();
        try {
            enviador.enviar(modelo, lead);
            LeadComunicacao comunicacao = comunicacoes.save(LeadComunicacao.enviada(lead.getId(), modelo.getId(), agora));
            lead.registrarEnvioAquecimento(agora, maxTentativas, haSeguinte);
            leads.save(lead);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tipo", TipoModeloMensagem.AQUECIMENTO.valor());
            payload.put("modeloId", modelo.getId().toString());
            payload.put("comunicacaoId", comunicacao.getId().toString());
            payload.put("ordemSequencia", proximaOrdem);
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.COMUNICACAO_ENVIADA, payload));
            return lead.getStatusAquecimento() == StatusAquecimento.ESGOTADO
                    ? Situacao.ENVIADO_E_ESGOTADO
                    : Situacao.ENVIADO;
        } catch (RuntimeException falhaEnvio) {
            LOG.warn("Falha no envio SMTP de aquecimento para o lead {}: {}", leadId,
                    falhaEnvio.getClass().getSimpleName());
            comunicacoes.save(LeadComunicacao.falhou(lead.getId(), modelo.getId(), agora));
            return Situacao.FALHOU;
        }
    }

    /** Prefere o modelo específico da etapa do lead (0 ou 1); senão, usa o que vale para qualquer etapa fria (nulo). */
    private static Optional<ModeloMensagem> escolherModelo(List<ModeloMensagem> ativos, int etapa, int ordem) {
        Optional<ModeloMensagem> especifico = ativos.stream()
                .filter(m -> m.getOrdemSequencia() != null && m.getOrdemSequencia() == ordem
                        && m.getEtapaAlvo() != null && m.getEtapaAlvo() == etapa)
                .findFirst();
        if (especifico.isPresent()) {
            return especifico;
        }
        return ativos.stream()
                .filter(m -> m.getOrdemSequencia() != null && m.getOrdemSequencia() == ordem
                        && m.getEtapaAlvo() == null)
                .findFirst();
    }
}
