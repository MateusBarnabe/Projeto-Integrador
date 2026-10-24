package br.com.centinela.marketing.formularios;

import com.fasterxml.jackson.annotation.JsonValue;

/** Tipos de campo do formulário, os mesmos do Prompt Mestre (seção 56). */
public enum TipoCampo {

    TEXTO("texto"),
    EMAIL("email"),
    TELEFONE("telefone"),
    WHATSAPP("whatsapp"),
    CNPJ("cnpj"),
    CPF("cpf"),
    LISTA("lista"),
    CHECKBOX("checkbox"),
    RADIO("radio"),
    TEXTAREA("textarea"),
    DATA("data");

    private final String valor;

    TipoCampo(String valor) {
        this.valor = valor;
    }

    /** Valor gravado no JSON de {@code campos} e trocado com o front e o Landing. */
    @JsonValue
    public String valor() {
        return valor;
    }

    /** Lista, radio e checkbox só fazem sentido com opções para escolher. */
    public boolean exigeOpcoes() {
        return this == LISTA || this == RADIO || this == CHECKBOX;
    }
}
