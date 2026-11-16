package br.com.centinela.marketing.rastreamento;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * O que o script de rastreamento manda da landing page ({@code POST /public/marketing/visitas/{id}/sinais}),
 * sempre com os totais da visita até ali: o servidor guarda o maior tempo e a maior rolagem e junta as
 * listas, então reenviar não soma duas vezes.
 *
 * <p>Nada do que o visitante digita: os elementos clicados são identificadores de botões e links, e as
 * páginas são caminho e âncora, sem a consulta (que pode ter dado pessoal). {@link #limpo()} corta tudo
 * que passar dos limites.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SinaisDoVisitante(
        Integer tempoNaPaginaSegundos,
        Integer profundidadeRolagem,
        List<String> elementosClicados,
        List<String> paginasVisitadas,
        String idioma,
        String fuso,
        String tela) {

    /** Um dia: acima disso, a aba ficou esquecida aberta. */
    static final int TEMPO_MAXIMO = 86_400;
    static final int ITENS_MAXIMOS = 30;
    static final int TAMANHO_ITEM = 80;
    static final int TAMANHO_TEXTO = 64;

    /** Os mesmos sinais dentro dos limites: números no intervalo, listas curtas, textos curtos. */
    public SinaisDoVisitante limpo() {
        return new SinaisDoVisitante(
                limitar(tempoNaPaginaSegundos, TEMPO_MAXIMO),
                limitar(profundidadeRolagem, 100),
                itens(elementosClicados),
                paginas(paginasVisitadas),
                texto(idioma),
                texto(fuso),
                texto(tela));
    }

    private static Integer limitar(Integer valor, int maximo) {
        return valor == null ? null : Math.max(0, Math.min(valor, maximo));
    }

    private static List<String> itens(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        Set<String> unicos = new LinkedHashSet<>();
        for (String valor : valores) {
            String limpo = texto(valor, TAMANHO_ITEM);
            if (limpo != null && unicos.size() < ITENS_MAXIMOS) {
                unicos.add(limpo);
            }
        }
        return new ArrayList<>(unicos);
    }

    // Sem a consulta: a URL pode trazer e-mail ou outro dado pessoal num parâmetro
    private static List<String> paginas(List<String> valores) {
        if (valores == null) {
            return List.of();
        }
        List<String> semConsulta = new ArrayList<>();
        for (String valor : valores) {
            if (valor != null) {
                semConsulta.add(valor.replaceAll("\\?[^#]*", ""));
            }
        }
        return itens(semConsulta);
    }

    private static String texto(String valor) {
        return texto(valor, TAMANHO_TEXTO);
    }

    private static String texto(String valor, int tamanho) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.strip().replaceAll("\\p{Cntrl}", "");
        return limpo.length() > tamanho ? limpo.substring(0, tamanho) : limpo;
    }
}
