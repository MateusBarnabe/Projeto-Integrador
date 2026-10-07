package br.com.centinela.marketing.compartilhado.dados;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;
import org.springframework.data.domain.Persistable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import br.com.centinela.marketing.compartilhado.seguranca.ContextoSeguranca;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;

/**
 * Campos e callbacks comuns a todas as entidades do Marketing.
 *
 * Entidades concretas devem acrescentar {@code @SQLDelete} com o próprio nome de tabela;
 * {@code @SQLRestriction} nesta classe esconde registros com soft delete.
 */
@MappedSuperclass
@SQLRestriction("deleted_at IS NULL")
public abstract class EntidadeBase implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime atualizadoEm;

    @Column(name = "deleted_at")
    private OffsetDateTime excluidoEm;

    @Column(name = "created_by", updatable = false)
    private UUID criadoPor;

    @Column(name = "updated_by")
    private UUID atualizadoPor;

    @Transient
    private boolean novo;

    protected EntidadeBase() {
    }

    @PrePersist
    void prepararCriacao() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        id = id == null ? UUID.randomUUID() : id;
        tenantId = TenantContexto.exigir();
        criadoEm = agora;
        atualizadoEm = agora;
        criadoPor = usuarioAtualOuNulo();
        atualizadoPor = criadoPor;
        novo = true;
    }

    @PreUpdate
    void prepararAtualizacao() {
        atualizadoEm = OffsetDateTime.now(ZoneOffset.UTC);
        atualizadoPor = usuarioAtualOuNulo();
    }

    public void marcarExcluida() {
        excluidoEm = OffsetDateTime.now(ZoneOffset.UTC);
        prepararAtualizacao();
    }

    private UUID usuarioAtualOuNulo() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)
                || token.getToken().getSubject().startsWith("svc:")) {
            return null;
        }
        return ContextoSeguranca.exigirUsuarioAtual().usuarioId();
    }

    @PostLoad
    @PostPersist
    void marcarComoPersistida() {
        novo = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public OffsetDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public OffsetDateTime getExcluidoEm() {
        return excluidoEm;
    }

    public UUID getCriadoPor() {
        return criadoPor;
    }

    public UUID getAtualizadoPor() {
        return atualizadoPor;
    }
}