package br.com.centinela.marketing.qualificacao;

import java.util.Optional;

/**
 * Métrica da interação do visitante que uma regra de fonte {@code comportamento} lê (coluna
 * {@code regras_pontuacao.campo_comportamento}, R1). As numéricas comparam com um mínimo, em
 * {@code valores} como {@code [{"minimo": 60}]}; as de lista batem quando algum item contém um dos
 * valores ({@code ["precos"]} bate com a página {@code /#precos}); o tipo de dispositivo compara igual.
 */
public enum MetricaComportamento {

    /** Segundos com a página visível ({@code comportamento.tempoNaPaginaSegundos}). */
    TEMPO_NA_PAGINA("tempo_na_pagina", "tempoNaPaginaSegundos", Tipo.NUMERO),
    /** Maior rolagem, em pontos percentuais de 0 a 100 ({@code comportamento.profundidadeRolagem}). */
    PROFUNDIDADE_ROLAGEM("profundidade_rolagem", "profundidadeRolagem", Tipo.NUMERO),
    /** Páginas e seções visitadas ({@code comportamento.paginasVisitadas}). */
    PAGINA_VISITADA("pagina_visitada", "paginasVisitadas", Tipo.LISTA),
    /** Botões e links clicados, pelo identificador ({@code comportamento.elementosClicados}). */
    ELEMENTO_CLICADO("elemento_clicado", "elementosClicados", Tipo.LISTA),
    /** {@code smartphone}, {@code tablet} ou {@code desktop} ({@code contexto_tecnico.tipoDispositivo}). */
    TIPO_DISPOSITIVO("tipo_dispositivo", "tipoDispositivo", Tipo.TEXTO);

    enum Tipo {
        NUMERO, LISTA, TEXTO
    }

    private final String valor;
    private final String chave;
    private final Tipo tipo;

    MetricaComportamento(String valor, String chave, Tipo tipo) {
        this.valor = valor;
        this.chave = chave;
        this.tipo = tipo;
    }

    /** Nome gravado na regra. */
    public String valor() {
        return valor;
    }

    /** Chave no {@code comportamento} ou no {@code contexto_tecnico} do lead. */
    public String chave() {
        return chave;
    }

    Tipo tipo() {
        return tipo;
    }

    public static Optional<MetricaComportamento> de(String valor) {
        if (valor == null) {
            return Optional.empty();
        }
        for (MetricaComportamento metrica : values()) {
            if (metrica.valor.equals(valor.strip())) {
                return Optional.of(metrica);
            }
        }
        return Optional.empty();
    }
}
