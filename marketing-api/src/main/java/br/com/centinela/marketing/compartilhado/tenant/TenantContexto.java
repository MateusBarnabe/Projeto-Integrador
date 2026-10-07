package br.com.centinela.marketing.compartilhado.tenant;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Tenant da operação atual, sempre definido pelo token ou por uma rotina interna controlada. */
public final class TenantContexto {

    private static final ThreadLocal<UUID> ATUAL = new ThreadLocal<>();

    private TenantContexto() {
    }

    public static Optional<UUID> atual() {
        return Optional.ofNullable(ATUAL.get());
    }

    public static UUID exigir() {
        return atual().orElseThrow(() -> new IllegalStateException(
                "Nenhum tenant no contexto da operação."));
    }

    public static void definir(UUID tenant) {
        ATUAL.set(Objects.requireNonNull(tenant, "tenant"));
    }

    public static void limpar() {
        ATUAL.remove();
    }

    public static void executar(UUID tenant, Runnable acao) {
        calcular(tenant, () -> {
            acao.run();
            return null;
        });
    }

    public static <T> T calcular(UUID tenant, Supplier<T> acao) {
        UUID anterior = ATUAL.get();
        definir(tenant);
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                limpar();
            } else {
                definir(anterior);
            }
        }
    }
}