package br.com.centinela.marketing.crm;

import java.util.UUID;

/**
 * Leva o vendedor do lead ao CRM como responsável da oportunidade criada pelo Landing (norteador §5,
 * passo 4.4; §6, Handoff para o CRM). É o ponto único da tarefa C1: hoje o CRM só tem o
 * {@code PUT /api/crm/oportunidades/{id}} completo, e a ação própria para o responsável, com
 * {@code servicos: [marketing]}, está pedida ao Grupo 6 (X2). Até lá, vale
 * {@link ResponsavelDaOportunidadePendente}; a C1 troca essa implementação pela que chama o CRM por
 * {@code ClientesDeModulos.crm()}, sem mexer em quem chama.
 *
 * <p>Quem chama: a ligação com a oportunidade (E6), quando o lead já tem vendedor. O rodízio (Q2, job
 * 4.4) deve chamar o mesmo ponto quando atribuir vendedor a um lead que já tem {@code crm_oportunidade_id}.
 *
 * <p>Roda no tenant do contexto e dentro da transação de quem chama. A implementação real não deve
 * lançar exceção por falha do CRM (a ligação do lead não pode ser desfeita por isso): faz a chamada
 * depois do commit ou devolve {@link Situacao#FALHOU} e deixa a nova tentativa para uma rotina.
 */
public interface ResponsavelDaOportunidade {

    /**
     * @param oportunidadeId oportunidade no CRM ({@code crm_oportunidade_id})
     * @param vendedorId     usuário do identity atribuído ao lead ({@code vendedor_id})
     */
    Situacao levarVendedor(UUID oportunidadeId, UUID vendedorId);

    /** Resultado, gravado no {@code payload} de {@code handoff_crm} ({@code responsavelNoCrm}). */
    enum Situacao {
        /** O CRM gravou o vendedor como responsável. */
        GRAVADO("gravado"),
        /** A ação do CRM ainda não existe (X2): nada foi enviado. */
        PENDENTE("pendente"),
        /** O CRM recusou ou não respondeu. */
        FALHOU("falhou");

        private final String valor;

        Situacao(String valor) {
            this.valor = valor;
        }

        public String valor() {
            return valor;
        }
    }
}
