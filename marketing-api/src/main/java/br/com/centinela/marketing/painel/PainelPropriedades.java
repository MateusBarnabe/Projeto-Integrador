package br.com.centinela.marketing.painel;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes do painel ({@code marketing.painel} no {@code application.yml}).
 *
 * @param cache cache das agregações do resumo
 * @param sse   fluxo de eventos em tempo real
 */
@ConfigurationProperties("marketing.painel")
public record PainelPropriedades(Cache cache, Sse sse) {

    public PainelPropriedades {
        cache = cache == null ? new Cache(null, 0) : cache;
        sse = sse == null ? new Sse(null, null, 0, null, 0, 0) : sse;
    }

    /**
     * @param ttl            quanto o resumo de um tenant e de um conjunto de filtros fica guardado; zero desliga o cache
     * @param tamanhoMaximo  quantos resumos o cache guarda, de todos os tenants
     */
    public record Cache(Duration ttl, long tamanhoMaximo) {

        public Cache {
            ttl = ttl == null || ttl.isNegative() ? Duration.ofSeconds(30) : ttl;
            tamanhoMaximo = tamanhoMaximo <= 0 ? 500 : tamanhoMaximo;
        }
    }

    /**
     * @param intervaloConsulta de quanto em quanto tempo cada conexão consulta o banco
     * @param intervaloPing     silêncio máximo antes do comentário {@code : ping}
     * @param limitePorTenant   conexões abertas ao mesmo tempo por tenant; além disso, {@code 429}
     * @param duracaoMaxima     teto da conexão, além da expiração do token
     * @param lote              eventos lidos por consulta
     * @param threads           threads que atendem todas as conexões
     */
    public record Sse(Duration intervaloConsulta, Duration intervaloPing, int limitePorTenant,
            Duration duracaoMaxima, int lote, int threads) {

        public Sse {
            intervaloConsulta = positivo(intervaloConsulta, Duration.ofSeconds(2));
            intervaloPing = positivo(intervaloPing, Duration.ofSeconds(15));
            limitePorTenant = limitePorTenant <= 0 ? 20 : limitePorTenant;
            duracaoMaxima = positivo(duracaoMaxima, Duration.ofMinutes(30));
            lote = lote <= 0 ? 200 : lote;
            threads = threads <= 0 ? 4 : threads;
        }

        private static Duration positivo(Duration valor, Duration padrao) {
            return valor == null || valor.isZero() || valor.isNegative() ? padrao : valor;
        }
    }
}
