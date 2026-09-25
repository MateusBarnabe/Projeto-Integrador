package br.com.centinela.marketing.compartilhado.web;

import java.util.List;

/**
 * Envelope de toda resposta HTTP do módulo, inclusive de erro (Contrato §8.2).
 */
public record Resposta<T>(boolean success, T data, String message, List<ErroCampo> errors) {

    public static <T> Resposta<T> ok(T data) {
        return new Resposta<>(true, data, null, List.of());
    }

    public static <T> Resposta<T> erro(String message, List<ErroCampo> errors) {
        return new Resposta<>(false, null, message, errors);
    }

    public static <T> Resposta<T> erro(String message) {
        return erro(message, List.of());
    }
}
