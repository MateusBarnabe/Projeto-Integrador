package br.com.centinela.marketing.painel;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;

/**
 * Filtros de {@code GET /api/marketing/painel} (norteador §9.1). O tenant nunca é filtro: vem do token.
 *
 * <p>O {@code periodo} segue o formato do filtro da lista de leads ({@code 7d}, {@code 30d}, {@code 90d},
 * qualquer número de 1 a 366 dias, ou {@code AAAA-MM-DD,AAAA-MM-DD}). O intervalo em dias é relativo a
 * "agora", por isso só vira datas na hora da consulta ({@link #de} e {@link #ate}).
 *
 * @param periodo  texto do período, já normalizado (minúsculas, sem espaços)
 * @param origem   {@code meta_ads}, {@code landing_page} ou nulo (todas)
 * @param campanha {@code utm_campaign} em minúsculas, ou nulo
 */
record FiltroPainel(String periodo, String origem, String campanha) {

    static final String PERIODO_PADRAO = "30d";
    static final int DIAS_MAXIMOS = 366;
    static final int TAMANHO_MAXIMO_CAMPANHA = 255;
    static final Set<String> ORIGENS = Set.of("meta_ads", "landing_page");

    // O Periodo da lista de leads (leads/api) é privado ao pacote; este é o mesmo formato
    private static final Pattern DIAS = Pattern.compile("(\\d{1,3})d");
    private static final Pattern INTERVALO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}),(\\d{4}-\\d{2}-\\d{2})");

    static FiltroPainel interpretar(String periodo, String origem, String campanha) {
        List<ErroCampo> erros = new ArrayList<>();

        String periodoTexto = vazioParaNulo(periodo);
        periodoTexto = periodoTexto == null ? PERIODO_PADRAO : periodoTexto.toLowerCase(Locale.ROOT).replace(" ", "");
        if (intervalo(periodoTexto, OffsetDateTime.now(ZoneOffset.UTC)) == null) {
            erros.add(new ErroCampo("periodo", "CAMPO_INVALIDO",
                    "Use dias (7d, 30d, 90d) ou um intervalo AAAA-MM-DD,AAAA-MM-DD."));
        }

        String origemTexto = vazioParaNulo(origem);
        if (origemTexto != null) {
            origemTexto = origemTexto.toLowerCase(Locale.ROOT);
            if (!ORIGENS.contains(origemTexto)) {
                erros.add(new ErroCampo("origem", "CAMPO_INVALIDO",
                        "Valor desconhecido: " + origemTexto + ". Aceitos: meta_ads, landing_page."));
            }
        }

        String campanhaTexto = vazioParaNulo(campanha);
        if (campanhaTexto != null && campanhaTexto.length() > TAMANHO_MAXIMO_CAMPANHA) {
            erros.add(new ErroCampo("campanha", "CAMPO_INVALIDO",
                    "A campanha aceita até " + TAMANHO_MAXIMO_CAMPANHA + " caracteres."));
        }

        if (!erros.isEmpty()) {
            throw new FiltroInvalidoException(erros);
        }
        return new FiltroPainel(periodoTexto, origemTexto,
                campanhaTexto == null ? null : campanhaTexto.toLowerCase(Locale.ROOT));
    }

    /** Início do período, inclusivo. */
    OffsetDateTime de(OffsetDateTime agora) {
        return intervalo(periodo, agora)[0];
    }

    /** Fim do período, exclusivo; nulo nos períodos em dias (vão até agora). */
    OffsetDateTime ate(OffsetDateTime agora) {
        return intervalo(periodo, agora)[1];
    }

    /** {@code [de, ate]}, ou {@code null} se o texto não está em nenhum formato aceito. */
    private static OffsetDateTime[] intervalo(String texto, OffsetDateTime agora) {
        Matcher dias = DIAS.matcher(texto);
        if (dias.matches()) {
            int quantidade = Integer.parseInt(dias.group(1));
            if (quantidade < 1 || quantidade > DIAS_MAXIMOS) {
                return null;
            }
            return new OffsetDateTime[] { agora.minusDays(quantidade), null };
        }
        Matcher datas = INTERVALO.matcher(texto);
        if (datas.matches()) {
            try {
                LocalDate inicio = LocalDate.parse(datas.group(1));
                LocalDate fim = LocalDate.parse(datas.group(2));
                if (fim.isBefore(inicio)) {
                    return null;
                }
                return new OffsetDateTime[] { inicio.atStartOfDay().atOffset(ZoneOffset.UTC),
                        fim.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC) };
            } catch (DateTimeParseException excecao) {
                return null;
            }
        }
        return null;
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
