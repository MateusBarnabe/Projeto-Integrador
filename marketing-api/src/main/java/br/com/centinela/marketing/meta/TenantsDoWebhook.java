package br.com.centinela.marketing.meta;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import br.com.centinela.marketing.compartilhado.http.ClientesDeModulos;
import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo;

/**
 * Descobre a que tenant pertence o subdomínio do webhook (norteador §6, "Como o tenant do webhook é
 * resolvido"): {@code GET /api/identity/tenants/resolver?subdominio=} com o token de serviço e sem
 * {@code X-Tenant-Id}, porque a rota ainda não sabe o tenant. A resposta fica em cache curto, inclusive
 * a de subdomínio desconhecido (o webhook é público e qualquer um pode testar nomes).
 *
 * <p>Só o {@code 404} do identity vira "desconhecido". Qualquer outra falha (identity fora do ar,
 * timeout, token recusado) sobe como {@link FalhaNaChamadaDeModulo}, não entra no cache, e a rota
 * responde {@code 503} para o Meta reentregar.
 */
@Component
class TenantsDoWebhook {

    static final String CAMINHO = "/api/identity/tenants/resolver?subdominio={subdominio}";

    /** Mesmo formato do contrato do identity ({@code ^[a-z0-9-]{1,63}$}); outro nem é perguntado. */
    private static final Pattern SUBDOMINIO = Pattern.compile("^[a-z0-9-]{1,63}$");

    private final ClientesDeModulos clientes;
    private final Cache<String, Optional<UUID>> cache;

    TenantsDoWebhook(ClientesDeModulos clientes, MetaPropriedades propriedades) {
        this.clientes = clientes;
        Duration validade = propriedades.webhook().cacheTenant();
        this.cache = validade.isZero() ? null
                : Caffeine.newBuilder().expireAfterWrite(validade).maximumSize(1_000).build();
    }

    /** {@code data} de {@code TenantResolvido} no contrato do identity. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TenantResolvido(UUID id, String nome) {
    }

    /**
     * @return o tenant do subdomínio; vazio se o subdomínio é desconhecido ou inválido
     * @throws FalhaNaChamadaDeModulo se o identity não respondeu
     */
    Optional<UUID> resolver(String subdominio) {
        if (subdominio == null || !SUBDOMINIO.matcher(subdominio).matches()) {
            return Optional.empty();
        }
        if (cache == null) {
            return perguntarAoIdentity(subdominio);
        }
        Optional<UUID> guardado = cache.getIfPresent(subdominio);
        if (guardado != null) {
            return guardado;
        }
        Optional<UUID> resolvido = perguntarAoIdentity(subdominio);
        cache.put(subdominio, resolvido);
        return resolvido;
    }

    private Optional<UUID> perguntarAoIdentity(String subdominio) {
        try {
            TenantResolvido tenant = clientes.identity().get(null, CAMINHO, TenantResolvido.class, subdominio);
            return tenant == null ? Optional.empty() : Optional.ofNullable(tenant.id());
        } catch (FalhaNaChamadaDeModulo falha) {
            if (falha.naoEncontrado()) {
                return Optional.empty();
            }
            throw falha;
        }
    }
}
