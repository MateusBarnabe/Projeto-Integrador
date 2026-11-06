package br.com.centinela.marketing.leads.api;

import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Enums saem na API com o valor do norteador ({@code landing_page}, {@code mql_quente}), como no banco. */
final class Valores {

    private Valores() {
    }

    static String de(ValorPersistido valor) {
        return valor == null ? null : valor.valor();
    }
}
