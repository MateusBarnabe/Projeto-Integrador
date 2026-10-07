package br.com.centinela.marketing.compartilhado.tenant;

import java.util.Map;
import java.util.UUID;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/** Entrega ao Hibernate o tenant atual para preencher e filtrar entidades com {@code @TenantId}. */
@Component
public class ResolvedorDeTenant implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

    static final UUID NENHUM = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContexto.atual().orElse(NENHUM);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> propriedades) {
        propriedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}