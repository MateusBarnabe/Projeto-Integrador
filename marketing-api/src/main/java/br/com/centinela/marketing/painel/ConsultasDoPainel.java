package br.com.centinela.marketing.painel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.painel.PainelResumo.Campanha;
import br.com.centinela.marketing.painel.PainelResumo.Canal;
import br.com.centinela.marketing.painel.PainelResumo.Distribuicao;
import br.com.centinela.marketing.painel.PainelResumo.Funil;
import br.com.centinela.marketing.painel.PainelResumo.LeadRecente;
import br.com.centinela.marketing.painel.PainelResumo.LeadZero;
import br.com.centinela.marketing.painel.PainelResumo.PontoDaSerie;
import br.com.centinela.marketing.painel.PainelResumo.PorCampanha;
import br.com.centinela.marketing.painel.PainelResumo.PorDispositivo;
import br.com.centinela.marketing.painel.PainelResumo.PorVendedor;
import br.com.centinela.marketing.painel.PainelResumo.Qualificacao;
import br.com.centinela.marketing.painel.PainelResumo.TaxaConversao;

/**
 * Agregações de leitura do painel (A4 e R6), em SQL sobre {@code leads}, {@code lead_eventos},
 * {@code canais_anuncio} e {@code visitas_anuncio}. Toda consulta filtra {@code tenant_id} à mão
 * (o filtro do Hibernate não vale em SQL) e ignora o que foi excluído ({@code deleted_at}). Lead
 * anonimizado conta nos números e perde o nome nos recentes.
 */
@Component
class ConsultasDoPainel {

    static final int MAXIMO_RECENTES = 10;
    static final int MAXIMO_LINHAS_POR_CAMPANHA = 50;

    private static final String MQL = "('mql_quente', 'mql_morno')";

    /**
     * Canal de anúncio do lead: o Meta (origem do lead ou UTM de uma rede da Meta) ou o Google Ads (UTM
     * de busca ou display pagos). Nulo quando o lead não veio de um dos dois canais de {@code canais_anuncio}.
     */
    private static final String CANAL_DE_ANUNCIO = """
            CASE
                WHEN l.origem = 'meta_ads' OR lower(l.utm_source) IN ('meta', 'facebook', 'fb', 'instagram', 'ig')
                    THEN 'meta_ads'
                WHEN lower(l.utm_source) = 'google'
                     AND lower(l.utm_medium) IN ('cpc', 'ppc', 'paid', 'paidsearch', 'display')
                    THEN 'google_ads'
            END""";

    /** "Aguardando vendedor" (§9.3): MQL com contato, ativo, sem vendedor. O mesmo critério do job 4.4. */
    private static final String AGUARDANDO_VENDEDOR = """
            l.vendedor_id IS NULL AND l.faixa_qualificacao IN ('mql_quente', 'mql_morno') AND NOT l.anonimizado
            AND (btrim(coalesce(l.email, '')) <> '' OR btrim(coalesce(l.telefone, '')) <> ''
                 OR btrim(coalesce(l.whatsapp, '')) <> '')""";

    private final JdbcTemplate jdbc;

    ConsultasDoPainel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    PainelResumo resumir(UUID tenant, FiltroPainel filtro) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        Clausula leads = clausulaDeLeads(tenant, filtro, agora);

        Totais totais = totais(leads);
        long[] conversao = conversaoDaEtapa1(leads);

        return new PainelResumo(
                new Funil(totais.etapa0, totais.etapa1, totais.etapa2, totais.etapa3),
                new Qualificacao(totais.quente, totais.morno, totais.nutricao, totais.semPontuacao),
                serieTemporal(leads),
                new TaxaConversao(pontos(conversao[1], conversao[0]),
                        pontos(totais.quente + totais.morno, totais.total)),
                campanhas(leads),
                canais(tenant, leads),
                new Distribuicao(porVendedor(leads), totais.aguardando),
                leadsRecentes(leads),
                leadZero(tenant, filtro, agora));
    }

    // ---- leads ----

    private Totais totais(Clausula leads) {
        String sql = """
                SELECT count(*),
                       count(*) FILTER (WHERE l.etapa = 0), count(*) FILTER (WHERE l.etapa = 1),
                       count(*) FILTER (WHERE l.etapa = 2), count(*) FILTER (WHERE l.etapa = 3),
                       count(*) FILTER (WHERE l.faixa_qualificacao = 'mql_quente'),
                       count(*) FILTER (WHERE l.faixa_qualificacao = 'mql_morno'),
                       count(*) FILTER (WHERE l.faixa_qualificacao = 'nutricao'),
                       count(*) FILTER (WHERE l.pontuacao IS NULL),
                       count(*) FILTER (WHERE %s)
                  FROM marketing.leads l
                 WHERE %s
                """.formatted(AGUARDANDO_VENDEDOR, leads.sql());
        return jdbc.queryForObject(sql, (rs, n) -> new Totais(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9),
                rs.getLong(10)), leads.parametros());
    }

    /**
     * Conversão da captura no chatbot para o envio: dos leads do filtro que passaram pela etapa 1 (estão nela
     * ou têm o registro de entrada nela na timeline), quantos chegaram à etapa 3. A etapa nunca recua, então
     * "chegou à 3" é "está na 3". Lead que entrou direto na 3 não passou pela 1 e fica de fora.
     *
     * @return {@code [passaram pela etapa 1, chegaram à etapa 3]}
     */
    private long[] conversaoDaEtapa1(Clausula leads) {
        String sql = """
                SELECT count(*), count(*) FILTER (WHERE l.etapa = 3)
                  FROM marketing.leads l
                 WHERE %s
                   AND (l.etapa = 1 OR EXISTS (SELECT 1 FROM marketing.lead_eventos e
                                                WHERE e.tenant_id = l.tenant_id AND e.lead_id = l.id
                                                  AND e.deleted_at IS NULL AND e.etapa_nova = 1))
                """.formatted(leads.sql());
        return jdbc.queryForObject(sql, (rs, n) -> new long[] { rs.getLong(1), rs.getLong(2) }, leads.parametros());
    }

    private List<PontoDaSerie> serieTemporal(Clausula leads) {
        String sql = """
                SELECT (l.created_at AT TIME ZONE 'UTC')::date, l.origem, count(*)
                  FROM marketing.leads l
                 WHERE %s
                 GROUP BY 1, 2
                 ORDER BY 1, 2
                """.formatted(leads.sql());
        return jdbc.query(sql, (rs, n) -> new PontoDaSerie(rs.getObject(1, LocalDate.class), rs.getLong(3),
                rs.getString(2)), leads.parametros());
    }

    private List<Campanha> campanhas(Clausula leads) {
        String sql = """
                SELECT min(l.utm_campaign), coalesce(%s, l.origem) AS canal, count(*),
                       count(*) FILTER (WHERE l.faixa_qualificacao IN %s),
                       count(*) FILTER (WHERE l.etapa = 3)
                  FROM marketing.leads l
                 WHERE %s AND btrim(coalesce(l.utm_campaign, '')) <> ''
                 GROUP BY lower(l.utm_campaign), 2
                 ORDER BY 3 DESC, lower(l.utm_campaign), 2
                 LIMIT %d
                """.formatted(CANAL_DE_ANUNCIO, MQL, leads.sql(), MAXIMO_LINHAS_POR_CAMPANHA);
        return jdbc.query(sql, (rs, n) -> new Campanha(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getLong(4), rs.getLong(5)), leads.parametros());
    }

    /**
     * Canais de anúncio com orçamento ou com leads no filtro. O custo é o orçamento mensal informado dividido
     * pelos leads e pelos MQL do canal no período: uma estimativa, sem o gasto real do Ads Manager.
     */
    private List<Canal> canais(UUID tenant, Clausula leads) {
        Map<String, long[]> contagens = new HashMap<>();
        String sql = """
                SELECT canal, count(*), count(*) FILTER (WHERE faixa_qualificacao IN %s)
                  FROM (SELECT %s AS canal, l.faixa_qualificacao
                          FROM marketing.leads l
                         WHERE %s) filtrados
                 WHERE canal IS NOT NULL
                 GROUP BY canal
                """.formatted(MQL, CANAL_DE_ANUNCIO, leads.sql());
        jdbc.query(sql, (rs) -> {
            contagens.put(rs.getString(1), new long[] { rs.getLong(2), rs.getLong(3) });
        }, leads.parametros());

        Map<String, BigDecimal> orcamentos = new HashMap<>();
        jdbc.query("SELECT canal, orcamento_mensal FROM marketing.canais_anuncio WHERE tenant_id = ? "
                + "AND deleted_at IS NULL", (rs) -> {
                    orcamentos.put(rs.getString(1), rs.getBigDecimal(2));
                }, tenant);

        List<Canal> canais = new ArrayList<>();
        for (String canal : List.of("meta_ads", "google_ads")) {
            long[] contagem = contagens.getOrDefault(canal, new long[] { 0, 0 });
            BigDecimal orcamento = orcamentos.get(canal);
            if (contagens.containsKey(canal) || orcamentos.containsKey(canal)) {
                canais.add(new Canal(canal, orcamento, contagem[0], contagem[1],
                        custo(orcamento, contagem[0]), custo(orcamento, contagem[1])));
            }
        }
        return canais;
    }

    private List<PorVendedor> porVendedor(Clausula leads) {
        String sql = """
                SELECT l.vendedor_id, count(*)
                  FROM marketing.leads l
                 WHERE %s AND l.vendedor_id IS NOT NULL
                 GROUP BY l.vendedor_id
                 ORDER BY 2 DESC, 1
                """.formatted(leads.sql());
        // O nome do vendedor depende da X7 (identity listando os vendedores)
        return jdbc.query(sql, (rs, n) -> new PorVendedor(rs.getObject(1, UUID.class), null, rs.getLong(2)),
                leads.parametros());
    }

    private List<LeadRecente> leadsRecentes(Clausula leads) {
        String sql = """
                SELECT l.id, CASE WHEN l.anonimizado THEN NULL ELSE l.nome END, l.etapa, l.faixa_qualificacao,
                       l.origem, l.canal, l.created_at, l.crm_oportunidade_id
                  FROM marketing.leads l
                 WHERE %s
                 ORDER BY l.created_at DESC, l.id
                 LIMIT %d
                """.formatted(leads.sql(), MAXIMO_RECENTES);
        return jdbc.query(sql, (rs, n) -> new LeadRecente(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, OffsetDateTime.class), rs.getObject(8, UUID.class)), leads.parametros());
    }

    // ---- lead 0 (R6) ----

    /**
     * O "lead 0" no período e na campanha do filtro, pela data do clique. A origem não se aplica: a visita
     * ainda não é lead. As visitas descartadas já não têm o aparelho (a linha fica só para as contagens) e
     * entram como {@code desconhecido} nas quebras por dispositivo.
     */
    LeadZero leadZero(UUID tenant, FiltroPainel filtro, OffsetDateTime agora) {
        Clausula visitas = clausulaDeVisitas(tenant, filtro, agora);

        long[] totais = jdbc.queryForObject("""
                SELECT count(*), count(*) FILTER (WHERE v.situacao = 'ativa'),
                       count(*) FILTER (WHERE v.situacao = 'convertida'),
                       count(*) FILTER (WHERE v.situacao = 'descartada')
                  FROM marketing.visitas_anuncio v
                 WHERE %s
                """.formatted(visitas.sql()),
                (rs, n) -> new long[] { rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4) },
                visitas.parametros());

        List<PorCampanha> porCampanha = jdbc.query("""
                SELECT min(nullif(btrim(v.utm_campaign), '')), count(*),
                       count(*) FILTER (WHERE v.situacao = 'convertida'),
                       count(*) FILTER (WHERE v.situacao = 'descartada')
                  FROM marketing.visitas_anuncio v
                 WHERE %s
                 GROUP BY lower(nullif(btrim(v.utm_campaign), ''))
                 ORDER BY 2 DESC, 1 NULLS LAST
                 LIMIT %d
                """.formatted(visitas.sql(), MAXIMO_LINHAS_POR_CAMPANHA),
                (rs, n) -> new PorCampanha(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
                visitas.parametros());

        List<PorDispositivo> porDispositivo = jdbc.query("""
                SELECT coalesce(v.contexto_tecnico ->> 'tipoDispositivo', 'desconhecido'), count(*),
                       count(*) FILTER (WHERE v.situacao = 'convertida')
                  FROM marketing.visitas_anuncio v
                 WHERE %s
                 GROUP BY 1
                 ORDER BY 2 DESC, 1
                """.formatted(visitas.sql()),
                (rs, n) -> new PorDispositivo(rs.getString(1), rs.getLong(2), rs.getLong(3)),
                visitas.parametros());

        return new LeadZero(totais[0], totais[1], totais[2], totais[3], pontos(totais[2], totais[0]),
                porCampanha, porDispositivo);
    }

    /**
     * Quantas visitas o tenant tem e quando uma delas mudou por último. Quando a assinatura muda, o fluxo
     * SSE manda o bloco {@code lead_zero_atualizado}.
     */
    AssinaturaDeVisitas assinaturaDasVisitas(UUID tenant) {
        return jdbc.queryForObject("SELECT count(*), max(updated_at) FROM marketing.visitas_anuncio "
                + "WHERE tenant_id = ?", (rs, n) -> {
                    OffsetDateTime ultima = rs.getObject(2, OffsetDateTime.class);
                    return new AssinaturaDeVisitas(rs.getLong(1), ultima == null ? null : ultima.toInstant());
                }, tenant);
    }

    record AssinaturaDeVisitas(long total, Instant ultimaAlteracao) {
    }

    // ---- fluxo SSE ----

    /** Maior {@code sequencia} do tenant: de onde um fluxo novo, sem {@code Last-Event-ID}, começa. */
    long ultimaSequencia(UUID tenant) {
        Long maior = jdbc.queryForObject("SELECT max(sequencia) FROM marketing.lead_eventos WHERE tenant_id = ?",
                Long.class, tenant);
        return maior == null ? 0 : maior;
    }

    /**
     * Fatos da timeline do tenant depois de {@code sequencia}, na ordem, que viram mensagens SSE. Só ids,
     * etapas, faixa, pontos e a origem do lead: nenhum dado pessoal.
     */
    List<FatoDoPainel> fatosDepois(UUID tenant, long sequencia, int lote) {
        return jdbc.query("""
                SELECT e.sequencia, e.lead_id, e.tipo_evento, e.etapa_anterior, e.etapa_nova,
                       e.payload ->> 'faixaNova', e.payload ->> 'pontuacao', e.payload ->> 'vendedorId', l.origem
                  FROM marketing.lead_eventos e
                  LEFT JOIN marketing.leads l ON l.id = e.lead_id AND l.tenant_id = e.tenant_id
                 WHERE e.tenant_id = ? AND e.deleted_at IS NULL AND e.sequencia > ?
                   AND e.tipo_evento IN ('etapa_alterada', 'qualificacao_alterada', 'vendedor_atribuido')
                 ORDER BY e.sequencia
                 LIMIT ?
                """, (rs, n) -> new FatoDoPainel(rs.getLong(1), rs.getObject(2, UUID.class), rs.getString(3),
                inteiroOuNulo(rs, 4), inteiroOuNulo(rs, 5), rs.getString(6), numeroOuNulo(rs.getString(7)),
                rs.getString(8), rs.getString(9)), tenant, sequencia, lote);
    }

    /** Linha de {@code lead_eventos} no que importa ao painel. */
    record FatoDoPainel(long sequencia, UUID leadId, String tipoEvento, Integer etapaAnterior, Integer etapaNova,
            String faixa, Integer pontuacao, String vendedorId, String origem) {
    }

    // ---- filtros ----

    private record Clausula(String sql, Object[] parametros) {
    }

    private static Clausula clausulaDeLeads(UUID tenant, FiltroPainel filtro, OffsetDateTime agora) {
        StringBuilder sql = new StringBuilder("l.tenant_id = ? AND l.deleted_at IS NULL AND l.created_at >= ?");
        List<Object> parametros = new ArrayList<>(List.of(tenant, filtro.de(agora)));
        if (filtro.ate(agora) != null) {
            sql.append(" AND l.created_at < ?");
            parametros.add(filtro.ate(agora));
        }
        if (filtro.origem() != null) {
            sql.append(" AND l.origem = ?");
            parametros.add(filtro.origem());
        }
        if (filtro.campanha() != null) {
            sql.append(" AND lower(l.utm_campaign) = ?");
            parametros.add(filtro.campanha());
        }
        return new Clausula(sql.toString(), parametros.toArray());
    }

    private static Clausula clausulaDeVisitas(UUID tenant, FiltroPainel filtro, OffsetDateTime agora) {
        StringBuilder sql = new StringBuilder("v.tenant_id = ? AND v.deleted_at IS NULL AND v.clicado_em >= ?");
        List<Object> parametros = new ArrayList<>(List.of(tenant, filtro.de(agora)));
        if (filtro.ate(agora) != null) {
            sql.append(" AND v.clicado_em < ?");
            parametros.add(filtro.ate(agora));
        }
        if (filtro.campanha() != null) {
            sql.append(" AND lower(v.utm_campaign) = ?");
            parametros.add(filtro.campanha());
        }
        return new Clausula(sql.toString(), parametros.toArray());
    }

    private record Totais(long total, long etapa0, long etapa1, long etapa2, long etapa3, long quente, long morno,
            long nutricao, long semPontuacao, long aguardando) {
    }

    // ---- números ----

    /** Percentual em pontos, com até 4 casas (§8.7); zero quando não há base. */
    static double pontos(long parte, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.round(parte * 1_000_000.0 / total) / 10_000.0;
    }

    /** Orçamento dividido pela quantidade, com 2 casas; nulo sem orçamento ou sem base. */
    private static BigDecimal custo(BigDecimal orcamento, long quantidade) {
        if (orcamento == null || quantidade <= 0) {
            return null;
        }
        return orcamento.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP);
    }

    private static Integer inteiroOuNulo(ResultSet rs, int coluna) throws SQLException {
        int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }

    private static Integer numeroOuNulo(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return Integer.valueOf(texto.strip());
        } catch (NumberFormatException excecao) {
            return null;
        }
    }
}
