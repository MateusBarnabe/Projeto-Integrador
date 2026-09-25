package br.com.centinela.marketing.compartilhado.web;

/**
 * Item de {@code errors[]} no envelope (Contrato §8.2).
 */
public record ErroCampo(String campo, String codigo, String detalhe) {
}
