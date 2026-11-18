package br.com.centinela.marketing.painel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de {@code GET /api/marketing/painel} (norteador §9.1), com o bloco {@code leadZero} da R6.
 * Percentuais em pontos, com até 4 casas; dinheiro com 2 casas (§8.7). Sem dado pessoal além do nome
 * dos leads recentes, que nunca vem de lead anonimizado.
 */
public record PainelResumo(
        Funil funil,
        Qualificacao qualificacao,
        List<PontoDaSerie> serieTemporal,
        TaxaConversao taxaConversao,
        List<Campanha> campanhas,
        List<Canal> canais,
        Distribuicao distribuicao,
        List<LeadRecente> leadsRecentes,
        LeadZero leadZero) {

    /** Leads por etapa atual do funil. A etapa 2 fica zerada até ter fonte. */
    public record Funil(long etapa0, long etapa1, long etapa2, long etapa3) {
    }

    /** {@code semPontuacao} são os leads com pontuação nula. */
    public record Qualificacao(long mqlQuente, long mqlMorno, long nutricao, long semPontuacao) {
    }

    public record PontoDaSerie(LocalDate data, long novosLeads, String origem) {
    }

    /**
     * @param etapa1ParaEtapa3 dos leads que passaram pela etapa 1, quantos chegaram à 3
     * @param leadsParaMql     MQL (quente ou morno) sobre todos os leads do filtro
     */
    public record TaxaConversao(double etapa1ParaEtapa3, double leadsParaMql) {
    }

    public record Campanha(String utmCampaign, String canal, long leads, long mql, long etapa3) {
    }

    /** {@code custoPorLead} e {@code custoPorMql} são estimativas; nulos sem orçamento ou sem leads. */
    public record Canal(String canal, BigDecimal orcamentoMensal, long leads, long mql, BigDecimal custoPorLead,
            BigDecimal custoPorMql) {
    }

    public record Distribuicao(List<PorVendedor> porVendedor, long aguardandoVendedor) {
    }

    /** {@code nome} é sempre nulo até a X7 (o identity ainda não lista os vendedores). */
    public record PorVendedor(UUID vendedorId, String nome, long atribuidos) {
    }

    public record LeadRecente(UUID id, String nome, int etapa, String faixa, String origem, String canal,
            OffsetDateTime criadoEm, UUID crmOportunidadeId) {
    }

    /**
     * O "lead 0" (R6): cliques no anúncio ainda sem contato, de {@code visitas_anuncio}.
     *
     * @param ativas        visitas ainda em andamento
     * @param taxaConversao convertidas sobre cliques, em pontos
     */
    public record LeadZero(long cliques, long ativas, long convertidas, long descartadas, double taxaConversao,
            List<PorCampanha> porCampanha, List<PorDispositivo> porDispositivo) {
    }

    public record PorCampanha(String utmCampaign, long cliques, long convertidas, long descartadas) {
    }

    public record PorDispositivo(String tipoDispositivo, long cliques, long convertidas) {
    }
}
