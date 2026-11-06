package br.com.centinela.marketing.entrada;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;

/**
 * Job 4.6, leitura pendente do envio (E11, norteador §4.6): relê {@code GET /api/landing/envios/{id}}
 * dos leads com {@code envio_id} e {@code envio_lido_em} nulo, que chegaram com o Landing fora do ar
 * (E5) ou foram criados pela oportunidade antes do envio (E6), e aplica o mesmo fluxo da chegada
 * ({@link LeituraDoEnvio#aplicar}): contato, merge de duplicados, respostas, origem, consentimento,
 * pontuação e rodízio. A falha conta; na {@value LeituraDoEnvio#MAXIMO_DE_FALHAS}ª seguida do mesmo lead,
 * o job desiste ({@link LeituraDoEnvio#registrarFalha}).
 *
 * <p>Rotina sem usuário: percorre os tenants com pendentes e roda cada lead no contexto do tenant e
 * na própria transação, para a falha de um não desfazer os outros. A chamada ao Landing (3 s no
 * máximo) é feita com a linha do lead travada por {@code FOR UPDATE SKIP LOCKED}: outra execução,
 * outra instância ou o consumo do mesmo envio pulam ou esperam por ele.
 */
@Service
public class LeituraPendenteDoEnvio {

    private static final Logger LOG = LoggerFactory.getLogger(LeituraPendenteDoEnvio.class);

    /** Leads por tenant em cada execução; o que sobrar fica para a próxima. */
    static final int LOTE = 50;

    /** {@code fonte} da timeline para o que o job grava. */
    static final String FONTE = "job.leitura_pendente_do_envio";

    private final TravasDeLeads travas;
    private final LeituraDoEnvio leitura;
    private final LeadRepository leads;
    private final TransactionTemplate transacao;

    LeituraPendenteDoEnvio(TravasDeLeads travas, LeituraDoEnvio leitura, LeadRepository leads,
            TransactionTemplate transacao) {
        this.travas = travas;
        this.leitura = leitura;
        this.leads = leads;
        this.transacao = transacao;
    }

    /** O que uma execução fez, somando os tenants. */
    public record Resultado(int lidos, int falhas, int desistencias) {
    }

    enum Situacao {
        LIDO, FALHOU, DESISTIU, PULADO
    }

    public Resultado lerPendentes() {
        int lidos = 0;
        int falhas = 0;
        int desistencias = 0;
        for (UUID tenant : travas.tenantsComLeituraPendente()) {
            List<UUID> pendentes;
            try {
                pendentes = TenantContexto.calcular(tenant, () -> travas.comLeituraPendente(LOTE));
            } catch (RuntimeException falha) {
                LOG.error("Leitura pendente: não listou os leads do tenant {}: {}", tenant,
                        falha.getClass().getSimpleName());
                continue;
            }
            for (UUID leadId : pendentes) {
                switch (lerUm(tenant, leadId)) {
                    case LIDO -> lidos++;
                    case FALHOU -> falhas++;
                    case DESISTIU -> desistencias++;
                    case PULADO -> { }
                }
            }
        }
        if (lidos + falhas + desistencias > 0) {
            LOG.info("Leitura pendente do envio: {} lido(s), {} falha(s), {} desistência(s)", lidos, falhas,
                    desistencias);
        }
        return new Resultado(lidos, falhas, desistencias);
    }

    private Situacao lerUm(UUID tenant, UUID leadId) {
        try {
            Situacao situacao = TenantContexto.calcular(tenant, () -> transacao.execute(s -> lerNoTenant(leadId)));
            return situacao == null ? Situacao.PULADO : situacao;
        } catch (RuntimeException falha) {
            // Só o tipo: a mensagem de erro do banco pode trazer valores do lead
            LOG.error("Leitura pendente do lead {} falhou: {}", leadId, falha.getClass().getSimpleName());
            return Situacao.PULADO;
        }
    }

    private Situacao lerNoTenant(UUID leadId) {
        if (!travas.travarComLeituraPendente(leadId)) {
            return Situacao.PULADO;
        }
        Lead lead = leads.findById(leadId).orElse(null);
        if (lead == null) {
            return Situacao.PULADO;
        }
        Procedencia procedencia = new Procedencia(FONTE, null);
        LeituraDoEnvio.Leitura lida = leitura.ler(lead.getEnvioId());
        if (lida.lido()) {
            leitura.aplicar(lead, lida.envio(), procedencia);
            return Situacao.LIDO;
        }
        return leitura.registrarFalha(lead, lida.motivo(), procedencia) ? Situacao.DESISTIU : Situacao.FALHOU;
    }
}
