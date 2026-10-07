package br.com.centinela.marketing.compartilhado.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Formato paginado público do módulo. */
public record Pagina<T>(List<T> itens, int pagina, int tamanho, long total) {

    public Pagina {
        itens = List.copyOf(itens);
    }

    public static <T, R> Pagina<R> de(Page<T> page, Function<T, R> conversor) {
        return new Pagina<>(page.getContent().stream().map(conversor).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}