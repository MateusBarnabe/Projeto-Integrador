package br.com.centinela.marketing.meta;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.entrada.EntradaDoMeta;
import br.com.centinela.marketing.entrada.Procedencia;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.qualificacao.PontuacaoDeLeads;
import br.com.centinela.marketing.qualificacao.ResultadoPontuacao;
import br.com.centinela.marketing.rodizio.RodizioDeVendedores;

/**
 * Job 4.1, leads do Meta Ads (E4, norteador §4.1). O webhook só guardou o {@code leadgen_id} no
 * buffer; aqui cada linha pendente vira lead:
 *
 * <ol>
 * <li>trava a linha ({@code FOR UPDATE SKIP LOCKED}: outra execução ou outra instância pula) numa
 * transação própria;</li>
 * <li>busca o lead na Graph API com o token da página do tenant ({@link ClienteDaGraphApi}, 3 s);</li>
 * <li>grava o lead pelo fluxo de {@code entrada/} ({@link EntradaDoMeta}): reentrega atualiza, e-mail ou
 * telefone já conhecidos completam o lead existente, senão cria na etapa 0; a etapa nunca recua;</li>
 * <li>pontua com as regras {@code campo_meta} e {@code utm} ({@link PontuacaoDeLeads}) e, sendo MQL,
 * atribui pelo rodízio, como o envio do Landing faz;</li>
 * <li>marca a linha como processada.</li>
 * </ol>
 *
 * <p>Falha na Graph API ou na gravação soma {@code tentativas} da linha; na
 * {@code marketing.meta.leads.maximo-de-tentativas}-ésima (5), a linha vira {@code falha_permanente}, sai
 * da fila para investigação manual e não trava o resto do lote. Tenant sem o token da página (ou sem a
 * chave de cifra) <em>não</em> conta tentativa: não há o que chamar, e as linhas esperam a configuração.
 *
 * <p>Rotina sem usuário: percorre os tenants com linhas pendentes, até
 * {@code marketing.meta.leads.lote} (500) por tenant, e roda cada linha no contexto do tenant e na
 * própria transação. O log e a timeline levam só ids e contagens.
 */
@Service
public class LeadsDoMeta {

    private static final Logger LOG = LoggerFactory.getLogger(LeadsDoMeta.class);

    /** {@code fonte} da timeline para o que o job grava. */
    static final String FONTE = "job.leads_do_meta";

    private final BufferDoMeta buffer;
    private final LeadEntradaBufferRepository linhas;
    private final CredenciaisDoTenant credenciais;
    private final ClienteDaGraphApi graph;
    private final EntradaDoMeta entrada;
    private final PontuacaoDeLeads pontuacao;
    private final RodizioDeVendedores rodizio;
    private final MetaPropriedades propriedades;
    private final TransactionTemplate transacao;

    LeadsDoMeta(BufferDoMeta buffer, LeadEntradaBufferRepository linhas, CredenciaisDoTenant credenciais,
            ClienteDaGraphApi graph, EntradaDoMeta entrada, PontuacaoDeLeads pontuacao,
            RodizioDeVendedores rodizio, MetaPropriedades propriedades, TransactionTemplate transacao) {
        this.buffer = buffer;
        this.linhas = linhas;
        this.credenciais = credenciais;
        this.graph = graph;
        this.entrada = entrada;
        this.pontuacao = pontuacao;
        this.rodizio = rodizio;
        this.propriedades = propriedades;
        this.transacao = transacao;
    }

    /**
     * O que uma execução fez, somando os tenants.
     *
     * @param processados   linhas que viraram lead
     * @param falhas        linhas que falharam e voltam na próxima execução (ou viraram permanentes)
     * @param permanentes   linhas que atingiram o limite de tentativas agora
     * @param semCredencial tenants com linhas pendentes e sem o token da página do Meta
     */
    public record Resultado(int processados, int falhas, int permanentes, int semCredencial) {
    }

    enum Situacao {
        PROCESSADA, FALHOU, PERMANENTE, PULADA
    }

    public Resultado processar() {
        int processados = 0;
        int falhas = 0;
        int permanentes = 0;
        int semCredencial = 0;
        for (UUID tenant : buffer.tenantsComPendentes()) {
            String token = credenciais.doTenant(tenant).pageAccessToken();
            if (token == null) {
                // Nada a chamar: as linhas esperam o token em Configurações, sem gastar tentativa
                LOG.warn("Leads do Meta do tenant {} esperando: token da página não configurado", tenant);
                semCredencial++;
                continue;
            }
            List<UUID> pendentes;
            try {
                pendentes = TenantContexto.calcular(tenant, () -> buffer.pendentes(propriedades.leads().lote()));
            } catch (RuntimeException falha) {
                LOG.error("Leads do Meta: não listou as linhas do tenant {}: {}", tenant,
                        falha.getClass().getSimpleName());
                continue;
            }
            for (UUID linhaId : pendentes) {
                switch (processarUma(tenant, linhaId, token)) {
                    case PROCESSADA -> processados++;
                    case FALHOU -> falhas++;
                    case PERMANENTE -> {
                        falhas++;
                        permanentes++;
                    }
                    case PULADA -> { }
                }
            }
        }
        if (processados + falhas + semCredencial > 0) {
            LOG.info("Leads do Meta: {} processado(s), {} falha(s) ({} permanente(s)), {} tenant(s) sem token",
                    processados, falhas, permanentes, semCredencial);
        }
        return new Resultado(processados, falhas, permanentes, semCredencial);
    }

    private Situacao processarUma(UUID tenant, UUID linhaId, String token) {
        try {
            Situacao situacao = TenantContexto.calcular(tenant,
                    () -> transacao.execute(status -> processarNoTenant(linhaId, token)));
            return situacao == null ? Situacao.PULADA : situacao;
        } catch (RuntimeException falha) {
            // A gravação do lead desfez a transação inteira: a falha é contada à parte. Só o tipo no log,
            // porque a mensagem de erro do banco pode trazer valores do lead
            LOG.error("Linha {} do buffer do Meta falhou na gravação: {}", linhaId, falha.getClass().getSimpleName());
            return contarFalhaIsolada(tenant, linhaId);
        }
    }

    private Situacao processarNoTenant(UUID linhaId, String token) {
        if (!buffer.travarPendente(linhaId)) {
            return Situacao.PULADA;
        }
        LeadEntradaBuffer linha = linhas.findById(linhaId).orElse(null);
        if (linha == null) {
            return Situacao.PULADA;
        }
        LeadDoMeta lido;
        try {
            lido = graph.buscarLead(linha.getMetaLeadId(), token);
        } catch (FalhaNaGraphApi falha) {
            return contarFalha(linha, falha.motivo().name());
        }

        Procedencia procedencia = new Procedencia(FONTE, null);
        EntradaDoMeta.Resultado gravado = entrada.gravar(linha.getMetaLeadId(), lido.nome(), lido.email(),
                lido.telefone(), lido.respostas(), procedencia);
        Lead lead = gravado.lead();
        ResultadoPontuacao resultado = pontuacao.pontuar(lead, lido.respostas());
        rodizio.atribuir(lead);

        linha.marcarProcessado(OffsetDateTime.now(ZoneOffset.UTC));
        linhas.save(linha);
        LOG.info("Linha {} do buffer do Meta virou o lead {} (faixa {})", linhaId, lead.getId(),
                resultado.faixa().valor());
        return Situacao.PROCESSADA;
    }

    private Situacao contarFalha(LeadEntradaBuffer linha, String motivo) {
        boolean permanente = linha.registrarFalha(propriedades.leads().maximoDeTentativas());
        linhas.save(linha);
        if (permanente) {
            LOG.error("Linha {} do buffer do Meta marcada como falha permanente depois de {} tentativas ({})",
                    linha.getId(), linha.getTentativas(), motivo);
            return Situacao.PERMANENTE;
        }
        LOG.warn("Linha {} do buffer do Meta falhou ({}); tentativa {}", linha.getId(), motivo,
                linha.getTentativas());
        return Situacao.FALHOU;
    }

    private Situacao contarFalhaIsolada(UUID tenant, UUID linhaId) {
        try {
            Situacao situacao = TenantContexto.calcular(tenant, () -> transacao.execute(status -> {
                if (!buffer.travarPendente(linhaId)) {
                    return Situacao.PULADA;
                }
                LeadEntradaBuffer linha = linhas.findById(linhaId).orElse(null);
                return linha == null ? Situacao.PULADA : contarFalha(linha, "ERRO_DE_GRAVACAO");
            }));
            return situacao == null ? Situacao.PULADA : situacao;
        } catch (RuntimeException falha) {
            LOG.error("Linha {} do buffer do Meta: não conseguiu contar a falha: {}", linhaId,
                    falha.getClass().getSimpleName());
            return Situacao.PULADA;
        }
    }
}
