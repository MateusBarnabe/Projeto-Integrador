package br.com.centinela.marketing.rodizio;

import java.util.Objects;
import java.util.UUID;

/**
 * O que o rodízio fez com um lead.
 *
 * @param situacao   resultado da tentativa
 * @param vendedorId vendedor do lead depois da tentativa; nulo quando ficou sem vendedor
 */
public record ResultadoAtribuicao(Situacao situacao, UUID vendedorId) {

    public ResultadoAtribuicao {
        Objects.requireNonNull(situacao, "situacao");
    }

    public enum Situacao {
        /** Recebeu vendedor agora, pelo rodízio. */
        ATRIBUIDO,
        /** Já tinha vendedor (atribuído antes, por outra execução ou na reatribuição manual). */
        JA_TINHA_VENDEDOR,
        /** MQL com contato, mas o rodízio do tenant está desligado: espera a pessoa gestora. */
        AGUARDANDO_RODIZIO_DESLIGADO,
        /** MQL com contato fora do horário comercial: o job 4.4 atribui no próximo horário. */
        AGUARDANDO_FORA_DO_HORARIO,
        /** MQL com contato e nenhum vendedor ativo na escala. */
        AGUARDANDO_SEM_VENDEDOR,
        /** Não entra no rodízio: nutrição, ainda sem pontuação, sem contato ou anonimizado. */
        NAO_ELEGIVEL
    }

    static ResultadoAtribuicao de(Situacao situacao) {
        return new ResultadoAtribuicao(situacao, null);
    }

    public boolean atribuido() {
        return situacao == Situacao.ATRIBUIDO;
    }

    /** O lead ficou em "aguardando vendedor" (§9.3). */
    public boolean aguardandoVendedor() {
        return switch (situacao) {
            case AGUARDANDO_RODIZIO_DESLIGADO, AGUARDANDO_FORA_DO_HORARIO, AGUARDANDO_SEM_VENDEDOR -> true;
            default -> false;
        };
    }
}
