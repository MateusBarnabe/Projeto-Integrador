package br.com.centinela.marketing.configuracoes;

import br.com.centinela.marketing.compartilhado.dados.ConversorDeValor;
import br.com.centinela.marketing.compartilhado.dados.ValorPersistido;

/** Canal de anúncio configurado pelo tenant (norteador §3, {@code canais_anuncio.canal}). */
public enum CanalAnuncioTipo implements ValorPersistido {

    GOOGLE_ADS("google_ads"),
    META_ADS("meta_ads");

    private final String valor;

    CanalAnuncioTipo(String valor) {
        this.valor = valor;
    }

    @Override
    public String valor() {
        return valor;
    }

    public static class Conversor extends ConversorDeValor<CanalAnuncioTipo> {
        public Conversor() {
            super(CanalAnuncioTipo.class);
        }
    }
}
