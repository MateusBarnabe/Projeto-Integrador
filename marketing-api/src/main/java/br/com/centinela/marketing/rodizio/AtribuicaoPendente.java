package br.com.centinela.marketing.rodizio;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracaoRepository;

/**
 * Job 4.4, passo 1 (norteador §4.4): atribui pelo rodízio os leads que ficaram "aguardando
 * vendedor" (chegaram fora do horário comercial, sem vendedor ativo ou com o rodízio desligado), em
 * tenants com o rodízio ligado e dentro do horário de atendimento. Rotina sem usuário: percorre os
 * tenants com pendentes e roda cada um no próprio contexto de tenant e na própria transação, para a
 * falha de um não travar os outros.
 *
 * <p>Concorrência: os leads são puxados com {@code FOR UPDATE SKIP LOCKED} (outra execução, outra
 * instância ou o consumo do envio que estiver com o lead pula para o próximo) e o vendedor é travado
 * a cada atribuição ({@link EscalaDoRodizio}).
 *
 * <p>O passo 2, transbordo por {@code minutos_transbordo}, fica desligado até o CRM expor o primeiro
 * contato do vendedor (Grupo 6, norteador §11).
 */
@Service
public class AtribuicaoPendente {

    private static final Logger LOG = LoggerFactory.getLogger(AtribuicaoPendente.class);

    /** Leads por tenant em cada execução; o que sobrar fica para a próxima. */
    static final int LOTE = 100;

    private final EscalaDoRodizio escala;
    private final RodizioDeVendedores rodizio;
    private final HorarioDeAtendimento horario;
    private final QualificacaoConfiguracaoRepository configuracoes;
    private final LeadRepository leads;
    private final TransactionTemplate transacao;

    AtribuicaoPendente(EscalaDoRodizio escala, RodizioDeVendedores rodizio, HorarioDeAtendimento horario,
            QualificacaoConfiguracaoRepository configuracoes, LeadRepository leads, TransactionTemplate transacao) {
        this.escala = escala;
        this.rodizio = rodizio;
        this.horario = horario;
        this.configuracoes = configuracoes;
        this.leads = leads;
        this.transacao = transacao;
    }

    /** @return quantos leads receberam vendedor nesta execução, somando os tenants */
    public int atribuirPendentes() {
        int atribuidos = 0;
        for (UUID tenant : escala.tenantsComPendentes()) {
            try {
                Integer doTenant = TenantContexto.calcular(tenant, () -> transacao.execute(s -> atribuirNoTenant()));
                atribuidos += doTenant == null ? 0 : doTenant;
            } catch (RuntimeException falha) {
                // Só o tipo da falha: a mensagem de erro do banco pode trazer valores do lead
                LOG.error("Atribuição pendente falhou no tenant {}: {}", tenant, falha.getClass().getSimpleName());
            }
        }
        if (atribuidos > 0) {
            LOG.info("Atribuição pendente: {} lead(s) receberam vendedor", atribuidos);
        }
        return atribuidos;
    }

    private int atribuirNoTenant() {
        QualificacaoConfiguracao configuracao = configuracoes.findFirstBy()
                .orElseGet(QualificacaoConfiguracao::padrao);
        if (!configuracao.isRodizioAtivo() || !horario.aberto(configuracao)) {
            return 0;
        }
        int atribuidos = 0;
        for (UUID leadId : escala.travarPendentes(LOTE)) {
            Lead lead = leads.findById(leadId).orElse(null);
            if (lead == null) {
                continue;
            }
            ResultadoAtribuicao resultado = rodizio.atribuir(lead);
            if (resultado.atribuido()) {
                atribuidos++;
            } else if (resultado.aguardandoVendedor()) {
                break; // sem vendedor ativo: os próximos também ficariam aguardando
            }
        }
        return atribuidos;
    }
}
