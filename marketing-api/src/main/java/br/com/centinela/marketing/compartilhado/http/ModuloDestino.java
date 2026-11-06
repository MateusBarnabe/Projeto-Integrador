package br.com.centinela.marketing.compartilhado.http;

/** Módulos da plataforma que o Marketing chama por HTTP (norteador, seção 6). */
public enum ModuloDestino {

    IDENTITY("identity"),
    LANDING("landing"),
    CRM("crm");

    private final String codigo;

    ModuloDestino(String codigo) {
        this.codigo = codigo;
    }

    /** Código do módulo na plataforma, usado em log e mensagem de erro. */
    public String codigo() {
        return codigo;
    }
}
