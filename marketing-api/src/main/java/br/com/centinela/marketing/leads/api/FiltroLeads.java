package br.com.centinela.marketing.leads.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;
import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.leads.CanalLead;
import br.com.centinela.marketing.leads.FaixaQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * Filtros de {@code GET /api/marketing/leads} (norteador §6 e §9.2). Cada filtro de valor fechado
 * aceita vários valores separados por vírgula ({@code etapa=0,1}); filtros diferentes se somam (E).
 * O tenant nunca é filtro: vem do token.
 *
 * @param etapas etapas do funil (0 a 3)
 * @param faixas faixas de qualificação
 * @param origens origens do lead
 * @param canais canais do envio no Landing
 * @param campanha {@code utm_campaign}, sem diferenciar maiúsculas
 * @param vendedorId vendedor atribuído
 * @param periodo data de entrada do lead
 * @param texto trecho do nome ou do e-mail
 */
record FiltroLeads(Set<Short> etapas, Set<FaixaQualificacao> faixas, Set<OrigemLead> origens,
        Set<CanalLead> canais, String campanha, UUID vendedorId, Periodo periodo, String texto) {

    static final int TAMANHO_MAXIMO_TEXTO = 100;

    static FiltroLeads interpretar(List<String> etapa, List<String> faixa, List<String> origem, List<String> canal,
            String campanha, String vendedorId, String periodo, String q) {
        List<ErroCampo> erros = new ArrayList<>();
        Set<Short> etapas = etapas(etapa, erros);
        Set<FaixaQualificacao> faixas = valores("faixa", faixa, FaixaQualificacao.class, erros);
        Set<OrigemLead> origens = valores("origem", origem, OrigemLead.class, erros);
        Set<CanalLead> canais = valores("canal", canal, CanalLead.class, erros);
        UUID vendedor = uuid("vendedorId", vendedorId, erros);
        Periodo intervalo = periodo(periodo, erros);
        String texto = texto(q, erros);
        if (!erros.isEmpty()) {
            throw new ParametroInvalidoException("Filtro inválido.", erros);
        }
        return new FiltroLeads(etapas, faixas, origens, canais, vazioParaNulo(campanha), vendedor, intervalo, texto);
    }

    private static Set<Short> etapas(List<String> valores, List<ErroCampo> erros) {
        Set<Short> etapas = new LinkedHashSet<>();
        for (String valor : partes(valores)) {
            try {
                int etapa = Integer.parseInt(valor);
                if (etapa < Lead.ETAPA_MINIMA || etapa > Lead.ETAPA_MAXIMA) {
                    throw new NumberFormatException();
                }
                etapas.add((short) etapa);
            } catch (NumberFormatException excecao) {
                erros.add(new ErroCampo("etapa", "CAMPO_INVALIDO", "Etapa do funil vai de 0 a 3: " + valor + "."));
            }
        }
        return Set.copyOf(etapas);
    }

    private static <E extends Enum<E> & ValorPersistido> Set<E> valores(String campo, List<String> valores,
            Class<E> tipo, List<ErroCampo> erros) {
        Set<E> encontrados = new LinkedHashSet<>();
        for (String valor : partes(valores)) {
            Arrays.stream(tipo.getEnumConstants())
                    .filter(constante -> constante.valor().equalsIgnoreCase(valor))
                    .findFirst()
                    .ifPresentOrElse(encontrados::add, () -> erros.add(new ErroCampo(campo, "CAMPO_INVALIDO",
                            "Valor desconhecido: " + valor + ". Aceitos: " + aceitos(tipo) + ".")));
        }
        return Set.copyOf(encontrados);
    }

    private static UUID uuid(String campo, String valor, List<ErroCampo> erros) {
        String texto = vazioParaNulo(valor);
        if (texto == null) {
            return null;
        }
        try {
            return UUID.fromString(texto);
        } catch (IllegalArgumentException excecao) {
            erros.add(new ErroCampo(campo, "CAMPO_INVALIDO", "Informe um UUID."));
            return null;
        }
    }

    private static Periodo periodo(String valor, List<ErroCampo> erros) {
        String texto = vazioParaNulo(valor);
        if (texto == null) {
            return null;
        }
        Periodo periodo = Periodo.interpretar(texto, OffsetDateTime.now(ZoneOffset.UTC));
        if (periodo == null) {
            erros.add(new ErroCampo("periodo", "CAMPO_INVALIDO",
                    "Use dias (7d, 30d, 90d) ou um intervalo AAAA-MM-DD,AAAA-MM-DD."));
        }
        return periodo;
    }

    private static String texto(String valor, List<ErroCampo> erros) {
        String texto = vazioParaNulo(valor);
        if (texto != null && texto.length() > TAMANHO_MAXIMO_TEXTO) {
            erros.add(new ErroCampo("q", "CAMPO_INVALIDO",
                    "A busca aceita até " + TAMANHO_MAXIMO_TEXTO + " caracteres."));
            return null;
        }
        return texto;
    }

    /** Aceita {@code etapa=0,1} e {@code etapa=0&etapa=1}; ignora itens vazios. */
    private static List<String> partes(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        return valores.stream()
                .flatMap(valor -> Arrays.stream(valor.split(",")))
                .map(String::strip)
                .filter(valor -> !valor.isEmpty())
                .toList();
    }

    private static <E extends Enum<E> & ValorPersistido> String aceitos(Class<E> tipo) {
        return Arrays.stream(tipo.getEnumConstants()).map(ValorPersistido::valor).collect(Collectors.joining(", "));
    }

    static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
