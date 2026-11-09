package br.com.centinela.marketing.rastreamento;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/**
 * Situação da visita do anúncio (coluna {@code visitas_anuncio.situacao}): {@code ativa} enquanto o
 * visitante pode converter; {@code convertida} quando o envio dele foi ligado a um lead;
 * {@code descartada} quando passou do prazo sem converter (job 4.7), com os dados zerados.
 */
public enum SituacaoVisita implements ValorPersistido {

    ATIVA("ativa"),
    CONVERTIDA("convertida"),
    DESCARTADA("descartada");

    private final String valor;

    SituacaoVisita(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<SituacaoVisita> {
        public Conversor() {
            super(SituacaoVisita.class);
        }
    }
}
