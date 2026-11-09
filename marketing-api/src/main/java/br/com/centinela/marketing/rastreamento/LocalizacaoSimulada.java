package br.com.centinela.marketing.rastreamento;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simulador da {@link LocalizacaoPorIp} (mock, R1): devolve sempre a mesma cidade, configurada em
 * {@code marketing.rastreamento.localizacao-simulada} (padrão Goiânia, GO, BR), marcada como
 * simulada. Localmente todo clique vem de um IP interno, que nenhuma base GeoIP localizaria.
 *
 * <p>{@code marketing.rastreamento.localizacao-simulada.habilitada=false} desliga: a visita fica sem
 * localização. Substituir por uma implementação real é criar outra classe com {@code @Primary}.
 */
@Component
class LocalizacaoSimulada implements LocalizacaoPorIp {

    private final boolean habilitada;
    private final Localizacao localizacao;

    LocalizacaoSimulada(
            @Value("${marketing.rastreamento.localizacao-simulada.habilitada:true}") boolean habilitada,
            @Value("${marketing.rastreamento.localizacao-simulada.cidade:Goiânia}") String cidade,
            @Value("${marketing.rastreamento.localizacao-simulada.uf:GO}") String uf,
            @Value("${marketing.rastreamento.localizacao-simulada.pais:BR}") String pais) {
        this.habilitada = habilitada;
        this.localizacao = new Localizacao(cidade, uf, pais, true);
    }

    @Override
    public Optional<Localizacao> localizar(String ip) {
        return habilitada ? Optional.of(localizacao) : Optional.empty();
    }
}
