package br.com.centinela.marketing.qualificacao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.centinela.marketing.leads.FaixaQualificacao;

/**
 * Pontuação de um lead (§9.3).
 *
 * @param pontuacao     soma dos pontos, um por critério
 * @param faixa         faixa pelos limites do tenant
 * @param faixaAnterior faixa gravada no lead antes deste cálculo; nula na primeira pontuação
 * @param itens         regra que valeu em cada critério que pontuou
 */
public record ResultadoPontuacao(int pontuacao, FaixaQualificacao faixa, FaixaQualificacao faixaAnterior,
        List<ItemPontuacao> itens) {

    public ResultadoPontuacao {
        Objects.requireNonNull(faixa, "faixa");
        itens = itens == null ? List.of() : List.copyOf(itens);
    }

    ResultadoPontuacao comFaixaAnterior(FaixaQualificacao anterior) {
        return new ResultadoPontuacao(pontuacao, faixa, anterior, itens);
    }

    /** A faixa mudou (ou o lead foi pontuado pela primeira vez): vai para a timeline e para o SSE. */
    public boolean faixaMudou() {
        return faixa != faixaAnterior;
    }

    /** MQL quente ou morno: entra no rodízio (Q2). */
    public boolean mql() {
        return faixa == FaixaQualificacao.MQL_QUENTE || faixa == FaixaQualificacao.MQL_MORNO;
    }

    /** Conteúdo de {@code leads.pontuacao_detalhe}: as regras que valeram, sem as respostas do lead. */
    public List<Map<String, Object>> detalhe() {
        return itens.stream().map(ItemPontuacao::paraDetalhe).toList();
    }

    /**
     * Regra que valeu num critério.
     *
     * @param campoId   campo do Landing, nas regras de {@code campo_formulario}
     * @param campoMeta pergunta do Meta, nas regras de {@code campo_meta}
     */
    public record ItemPontuacao(UUID regraId, String criterio, FonteRegra fonte, int pontos, UUID campoId,
            String campoMeta, String campoComportamento) {

        public ItemPontuacao(UUID regraId, String criterio, FonteRegra fonte, int pontos, UUID campoId,
                String campoMeta) {
            this(regraId, criterio, fonte, pontos, campoId, campoMeta, null);
        }

        static ItemPontuacao da(RegraPontuacao regra) {
            return new ItemPontuacao(regra.getId(), regra.getCriterio(), regra.getFonte(), regra.getPontos(),
                    regra.getCampoId(), regra.getCampoMeta(), regra.getCampoComportamento());
        }

        Map<String, Object> paraDetalhe() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("regraId", regraId == null ? null : regraId.toString());
            item.put("criterio", criterio);
            item.put("fonte", fonte.valor());
            item.put("pontos", pontos);
            if (campoId != null) {
                item.put("campoId", campoId.toString());
            }
            if (campoMeta != null) {
                item.put("campoMeta", campoMeta);
            }
            if (campoComportamento != null) {
                item.put("campoComportamento", campoComportamento);
            }
            return item;
        }
    }
}
