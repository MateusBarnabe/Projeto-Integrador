package br.com.centinela.marketing.compartilhado.web;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Cria pedidos paginados com limites e ordenação controlada pelo endpoint. */
public final class Paginacao {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    private Paginacao() {
    }

    public static PageRequest pedido(Integer pagina, Integer tamanho, String ordenar,
            Map<String, String> camposOrdenaveis) {
        int numeroPagina = Math.max(pagina == null ? 0 : pagina, 0);
        int tamanhoPagina = Math.min(Math.max(tamanho == null ? TAMANHO_PADRAO : tamanho, 1),
                TAMANHO_MAXIMO);
        return PageRequest.of(numeroPagina, tamanhoPagina, ordenar(ordenar, camposOrdenaveis));
    }

    private static Sort ordenar(String valor, Map<String, String> camposOrdenaveis) {
        String[] partes = (valor == null ? "" : valor).split(",");
        String campo = camposOrdenaveis.getOrDefault(partes[0].strip(), camposOrdenaveis.values().iterator().next());
        Sort.Direction direcao = partes.length > 1 && "asc".equalsIgnoreCase(partes[1].strip())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direcao, campo);
    }
}