package br.com.centinela.marketing.qualificacao;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;

/**
 * Pontua um lead e grava o resultado (§9.3): {@code pontuacao}, {@code faixa_qualificacao},
 * {@code pontuacao_detalhe} e {@code qualificado_em}. Quando a faixa muda, inclusive na primeira
 * pontuação, registra {@code qualificacao_alterada} na timeline.
 *
 * <p>Chamado pelos consumidores depois de gravar o que chegou: E1 (chatbot, só UTM), E5 e job 4.6
 * (envio, com as respostas já copiadas em {@code dados_formulario}) e E4 (Meta, com as respostas do
 * formulário instantâneo). Roda na transação de quem chama e no tenant do contexto, que precisa ser o
 * do lead. Não lê nada do Landing.
 */
@Service
public class PontuacaoDeLeads {

    private final RegraPontuacaoRepository regras;
    private final QualificacaoConfiguracaoRepository configuracoes;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;

    public PontuacaoDeLeads(RegraPontuacaoRepository regras, QualificacaoConfiguracaoRepository configuracoes,
            LeadRepository leads, LeadEventoRepository eventos) {
        this.regras = regras;
        this.configuracoes = configuracoes;
        this.leads = leads;
        this.eventos = eventos;
    }

    /** Pontua com o que o lead já tem: respostas do envio, formulário e UTM. */
    @Transactional
    public ResultadoPontuacao pontuar(Lead lead) {
        return pontuar(lead, Map.of());
    }

    /**
     * Pontua um lead do Meta com as respostas do formulário instantâneo, pelo nome da pergunta
     * (valor em texto ou lista, como vem em {@code field_data} da Graph API).
     */
    @Transactional
    public ResultadoPontuacao pontuar(Lead lead, Map<String, ?> respostasMeta) {
        exigirLeadDoTenantAtual(lead);
        QualificacaoConfiguracao configuracao = configuracoes.findFirstBy()
                .orElseGet(QualificacaoConfiguracao::padrao);
        FaixaQualificacao faixaAnterior = lead.getFaixaQualificacao();
        Integer pontuacaoAnterior = lead.getPontuacao();

        ResultadoPontuacao resultado = MotorDePontuacao
                .calcular(EntradaPontuacao.doLead(lead, respostasMeta), regras.findByAtivoTrue(), configuracao)
                .comFaixaAnterior(faixaAnterior);

        lead.registrarPontuacao(resultado.pontuacao(), resultado.faixa(), resultado.detalhe(),
                OffsetDateTime.now(ZoneOffset.UTC));
        leads.save(lead);
        if (resultado.faixaMudou()) {
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.QUALIFICACAO_ALTERADA,
                    mudancaDeFaixa(faixaAnterior, pontuacaoAnterior, resultado)));
        }
        return resultado;
    }

    private static void exigirLeadDoTenantAtual(Lead lead) {
        if (lead == null || lead.getId() == null || lead.getTenantId() == null) {
            throw new IllegalStateException("Grave o lead antes de pontuar.");
        }
        if (!lead.getTenantId().equals(TenantContexto.exigir())) {
            throw new IllegalStateException("Lead de outro tenant.");
        }
    }

    // Sem dado pessoal: só faixas e pontos
    private static Map<String, Object> mudancaDeFaixa(FaixaQualificacao anterior, Integer pontuacaoAnterior,
            ResultadoPontuacao resultado) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("faixaAnterior", anterior == null ? null : anterior.valor());
        payload.put("faixaNova", resultado.faixa().valor());
        payload.put("pontuacaoAnterior", pontuacaoAnterior);
        payload.put("pontuacao", resultado.pontuacao());
        return payload;
    }
}
