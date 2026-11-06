package br.com.centinela.marketing.leads.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intervalo de entrada do lead ({@code created_at}), no filtro {@code periodo} da lista e do painel
 * (norteador §9.1: 7, 30 ou 90 dias ou intervalo customizado). Formatos aceitos:
 * <ul>
 * <li>{@code 7d}, {@code 30d}, {@code 90d} (qualquer número de dias de 1 a 366): os últimos N dias até agora;</li>
 * <li>{@code AAAA-MM-DD,AAAA-MM-DD}: do início do primeiro dia ao fim do último, em UTC.</li>
 * </ul>
 *
 * @param de início, inclusivo
 * @param ate fim, exclusivo
 */
record Periodo(OffsetDateTime de, OffsetDateTime ate) {

    static final int DIAS_MAXIMOS = 366;

    private static final Pattern DIAS = Pattern.compile("(\\d{1,3})d");
    private static final Pattern INTERVALO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}),(\\d{4}-\\d{2}-\\d{2})");

    /** @return o período, ou {@code null} quando o texto não está em nenhum dos formatos aceitos */
    static Periodo interpretar(String valor, OffsetDateTime agora) {
        String texto = valor.strip().toLowerCase();
        Matcher dias = DIAS.matcher(texto);
        if (dias.matches()) {
            int quantidade = Integer.parseInt(dias.group(1));
            if (quantidade < 1 || quantidade > DIAS_MAXIMOS) {
                return null;
            }
            return new Periodo(agora.minusDays(quantidade), null);
        }
        Matcher intervalo = INTERVALO.matcher(texto.replace(" ", ""));
        if (intervalo.matches()) {
            try {
                LocalDate inicio = LocalDate.parse(intervalo.group(1));
                LocalDate fim = LocalDate.parse(intervalo.group(2));
                if (fim.isBefore(inicio)) {
                    return null;
                }
                return new Periodo(inicio.atStartOfDay().atOffset(ZoneOffset.UTC),
                        fim.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
            } catch (DateTimeParseException excecao) {
                return null;
            }
        }
        return null;
    }
}
