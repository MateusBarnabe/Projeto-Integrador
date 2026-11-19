package br.com.centinela.marketing.painel;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Resumo do painel com cache local por tenant e filtros (norteador §9.1): as agregações são caras e o
 * painel é aberto por vários usuários. O TTL é curto ({@code marketing.painel.cache.ttl}), então o número
 * pode atrasar alguns segundos; o tempo real vem do fluxo SSE. Sem Redis nesta fase.
 */
@Service
public class PainelServico {

    private final ConsultasDoPainel consultas;
    private final Cache<ChaveDoPainel, PainelResumo> cache;

    PainelServico(ConsultasDoPainel consultas, PainelPropriedades propriedades) {
        this.consultas = consultas;
        PainelPropriedades.Cache ajuste = propriedades.cache();
        this.cache = ajuste.ttl().isZero() ? null : Caffeine.newBuilder()
                .expireAfterWrite(ajuste.ttl())
                .maximumSize(ajuste.tamanhoMaximo())
                .build();
    }

    /** Resumo do tenant do contexto para os filtros, do cache quando ainda vale. */
    PainelResumo obter(FiltroPainel filtro) {
        UUID tenant = TenantContexto.exigir();
        if (cache == null) {
            return consultas.resumir(tenant, filtro);
        }
        return cache.get(new ChaveDoPainel(tenant, filtro), chave -> consultas.resumir(tenant, filtro));
    }

    /** A chave sempre começa pelo tenant: um tenant nunca lê o resumo de outro. */
    private record ChaveDoPainel(UUID tenant, FiltroPainel filtro) {
    }
}
