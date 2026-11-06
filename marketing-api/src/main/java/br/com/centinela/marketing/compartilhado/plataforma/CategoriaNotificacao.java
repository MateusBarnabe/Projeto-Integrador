package br.com.centinela.marketing.compartilhado.plataforma;

/**
 * Categorias aceitas em {@code identity.notificacao.criar} ({@code DadosNotificacao.categoria} no
 * {@code contratos/identity.asyncapi.yaml} da plataforma, 0.3.0). O Marketing usa só
 * {@link #NOVO_LEAD}; as demais estão aqui para a lista bater com o contrato.
 */
public enum CategoriaNotificacao {
    TAREFA_VENCIDA,
    REUNIAO,
    PROPOSTA_ABERTA,
    PROPOSTA_EXPIRANDO,
    CONTRATO_ASSINADO,
    COBRANCA_PAGA,
    COBRANCA_VENCIDA,
    NOVA_INDICACAO,
    NOVO_LEAD,
    CHAMADO,
    LICENCA_A_VENCER,
    RENOVACAO
}
