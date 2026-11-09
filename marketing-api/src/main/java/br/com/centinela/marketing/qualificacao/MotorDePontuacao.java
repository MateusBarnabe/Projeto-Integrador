package br.com.centinela.marketing.qualificacao;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.com.centinela.marketing.leads.FaixaQualificacao;

/**
 * Cálculo da pontuação de um lead (§9.3), sem acesso a banco: recebe as regras e a entrada e devolve
 * a soma, a faixa e as regras que valeram. Quem grava é {@link PontuacaoDeLeads}.
 *
 * <ul>
 *   <li>Valem as regras ativas, não excluídas e do tenant do lead. O repositório já filtra tenant e
 *       {@code deleted_at}; o motor confere de novo para nunca somar regra alheia.</li>
 *   <li>{@code campo_formulario}: só as do {@code formulario_id} do lead; compara a resposta de mesmo
 *       {@code campoId}. {@code campo_meta}: só para lead do Meta; compara a resposta da pergunta de
 *       mesmo nome. {@code utm}: para todo lead. {@code comportamento}: só para lead com visita de
 *       anúncio ligada; compara a métrica da interação ({@link MetricaComportamento}).</li>
 *   <li>Cada critério conta uma vez, pela regra de mais pontos.</li>
 * </ul>
 *
 * Respostas e valores das regras são comparados sem diferença de maiúsculas, acentos e espaços nas
 * pontas. Resposta com várias opções (múltipla escolha) bate se uma delas bater.
 */
public final class MotorDePontuacao {

    // Na regra de UTM, a chave "direto" pede tráfego sem UTM; as outras são as da UTM, com ou sem "utm_"
    private static final String DIRETO = "direto";

    // Empate de pontos no mesmo critério: vale a regra de menor id, para o resultado não variar
    private static final Comparator<RegraPontuacao> MAIS_PONTOS_PRIMEIRO = Comparator
            .comparingInt(RegraPontuacao::getPontos).reversed()
            .thenComparing(regra -> String.valueOf(regra.getId()));

    private MotorDePontuacao() {
    }

    public static ResultadoPontuacao calcular(EntradaPontuacao entrada, Collection<RegraPontuacao> regras,
            QualificacaoConfiguracao configuracao) {
        Objects.requireNonNull(entrada, "entrada");
        Objects.requireNonNull(configuracao, "configuracao");

        Map<String, RegraPontuacao> vencedoraPorCriterio = new LinkedHashMap<>();
        (regras == null ? Stream.<RegraPontuacao>empty() : regras.stream())
                .filter(regra -> valeParaOLead(regra, entrada))
                .filter(regra -> bate(regra, entrada))
                .sorted(MAIS_PONTOS_PRIMEIRO)
                .forEach(regra -> vencedoraPorCriterio.putIfAbsent(normalizar(regra.getCriterio()), regra));

        List<ResultadoPontuacao.ItemPontuacao> itens = vencedoraPorCriterio.values().stream()
                .map(ResultadoPontuacao.ItemPontuacao::da)
                .toList();
        int pontuacao = itens.stream().mapToInt(ResultadoPontuacao.ItemPontuacao::pontos).sum();
        FaixaQualificacao faixa = configuracao.faixaPara(pontuacao);
        return new ResultadoPontuacao(pontuacao, faixa, null, itens);
    }

    private static boolean valeParaOLead(RegraPontuacao regra, EntradaPontuacao entrada) {
        if (regra == null || !regra.isAtivo() || regra.getExcluidoEm() != null
                || !entrada.tenantId().equals(regra.getTenantId())) {
            return false;
        }
        return switch (regra.getFonte()) {
            case CAMPO_FORMULARIO -> entrada.formularioId() != null
                    && entrada.formularioId().equals(regra.getFormularioId());
            case CAMPO_META -> entrada.leadDoMeta();
            case UTM -> true;
            case COMPORTAMENTO -> !entrada.interacao().isEmpty();
        };
    }

    private static boolean bate(RegraPontuacao regra, EntradaPontuacao entrada) {
        return switch (regra.getFonte()) {
            case CAMPO_FORMULARIO -> algumaIgual(entrada.respostasFormulario().get(regra.getCampoId()),
                    regra.getValores());
            case CAMPO_META -> algumaIgual(respostaMeta(entrada, regra.getCampoMeta()), regra.getValores());
            case UTM -> regra.getValores().stream()
                    .anyMatch(valor -> valor instanceof Map<?, ?> condicao && utmBate(condicao, entrada));
            case COMPORTAMENTO -> MetricaComportamento.de(regra.getCampoComportamento())
                    .map(metrica -> comportamentoBate(metrica, regra.getValores(), entrada))
                    .orElse(false);
        };
    }

    /**
     * Regra de comportamento (R1). Numérica: bate quando o valor do lead chega ao {@code minimo} de
     * alguma condição ({@code [{"minimo": 60}]}). Lista: bate quando algum item do lead contém um dos
     * valores. Texto: bate quando é igual a um dos valores.
     */
    private static boolean comportamentoBate(MetricaComportamento metrica, List<Object> valoresDaRegra,
            EntradaPontuacao entrada) {
        Object doLead = entrada.interacao().get(metrica.chave());
        if (doLead == null) {
            return false;
        }
        return switch (metrica.tipo()) {
            case NUMERO -> {
                Double medido = numero(doLead);
                yield medido != null && valoresDaRegra.stream()
                        .map(valor -> valor instanceof Map<?, ?> condicao ? numero(condicao.get("minimo")) : null)
                        .anyMatch(minimo -> minimo != null && medido >= minimo);
            }
            case LISTA -> {
                Set<String> itens = textos(doLead);
                Set<String> procurados = textos(valoresDaRegra);
                yield itens.stream().anyMatch(item -> procurados.stream().anyMatch(item::contains));
            }
            case TEXTO -> algumaIgual(doLead, valoresDaRegra);
        };
    }

    private static Double numero(Object valor) {
        if (valor instanceof Number numero) {
            return numero.doubleValue();
        }
        try {
            return valor == null ? null : Double.valueOf(valor.toString().strip());
        } catch (NumberFormatException naoNumerico) {
            return null;
        }
    }

    private static Object respostaMeta(EntradaPontuacao entrada, String pergunta) {
        String procurada = normalizar(pergunta);
        return entrada.respostasMeta().entrySet().stream()
                .filter(resposta -> normalizar(resposta.getKey()).equals(procurada))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean algumaIgual(Object resposta, List<Object> valoresDaRegra) {
        Set<String> respondidos = textos(resposta);
        return !respondidos.isEmpty() && textos(valoresDaRegra).stream().anyMatch(respondidos::contains);
    }

    /**
     * Uma condição da regra de UTM bate quando todas as chaves batem, ex.: {@code {"source": "google",
     * "medium": "cpc"}}. A chave pode trazer uma lista de valores aceitos. {@code {"direto": true}} é o
     * tráfego sem UTM; lead do Meta não conta como direto, porque veio de um anúncio. Chave
     * desconhecida ou condição vazia não pontua.
     */
    private static boolean utmBate(Map<?, ?> condicao, EntradaPontuacao entrada) {
        if (condicao.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> parte : condicao.entrySet()) {
            String chave = normalizar(String.valueOf(parte.getKey()));
            chave = chave.startsWith("utm_") ? chave.substring(4) : chave;
            if (DIRETO.equals(chave)) {
                boolean direto = !entrada.leadDoMeta() && entrada.utm().vazia();
                if (direto != Boolean.parseBoolean(String.valueOf(parte.getValue()))) {
                    return false;
                }
            } else if (!EntradaPontuacao.Utm.chaveConhecida(chave)
                    || !algumaIgual(entrada.utm().valor(chave), listaDe(parte.getValue()))) {
                return false;
            }
        }
        return true;
    }

    private static List<Object> listaDe(Object valor) {
        return valor instanceof Collection<?> varios ? List.copyOf(varios) : valor == null ? List.of() : List.of(valor);
    }

    private static Set<String> textos(Object valor) {
        Stream<?> valores = valor instanceof Collection<?> varios ? varios.stream() : Stream.of(valor);
        return valores.filter(Objects::nonNull)
                .map(item -> normalizar(item.toString()))
                .filter(texto -> !texto.isEmpty())
                .collect(Collectors.toSet());
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }
}
