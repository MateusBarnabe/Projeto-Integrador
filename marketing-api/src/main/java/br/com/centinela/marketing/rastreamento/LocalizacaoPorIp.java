package br.com.centinela.marketing.rastreamento;

import java.util.Optional;

/**
 * Localização aproximada (cidade, UF, país) do IP do clique no anúncio (R1). O IP não é guardado: só
 * a localização que sai dele, e só na visita.
 *
 * <p>A implementação padrão é a {@link LocalizacaoSimulada}. Com {@code RASTREAMENTO_GEOIP_ARQUIVO}
 * apontando para uma base GeoLite2 City existente, vale a {@link LocalizacaoPorGeoIp} (R4), marcada
 * como {@code @Primary}; quem usa não muda.
 */
public interface LocalizacaoPorIp {

    /**
     * @param cidade   nome da cidade
     * @param uf       sigla do estado
     * @param pais     código ISO do país
     * @param simulada {@code true} quando veio de um simulador, para a tela avisar
     */
    record Localizacao(String cidade, String uf, String pais, boolean simulada) {
    }

    Optional<Localizacao> localizar(String ip);
}
