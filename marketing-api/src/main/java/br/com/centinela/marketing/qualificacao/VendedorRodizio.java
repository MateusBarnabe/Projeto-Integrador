package br.com.centinela.marketing.qualificacao;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Vendedor que participa da escala do rodízio (§9.3). Guarda só o UUID do usuário do identity. */
@Entity
@Table(name = "vendedores_rodizio")
@SQLDelete(sql = "UPDATE marketing.vendedores_rodizio SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class VendedorRodizio extends EntidadeBase {

    @Column(name = "vendedor_id", nullable = false, updatable = false)
    private UUID vendedorId;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "ultima_atribuicao_em")
    private OffsetDateTime ultimaAtribuicaoEm;

    protected VendedorRodizio() {
    }

    public VendedorRodizio(UUID vendedorId) {
        this.vendedorId = Objects.requireNonNull(vendedorId, "vendedorId");
    }

    /** Marca que o vendedor acabou de receber um lead; ele vai para o fim da fila. */
    public void registrarAtribuicao(OffsetDateTime em) {
        this.ultimaAtribuicaoEm = Objects.requireNonNull(em, "em");
    }

    public void ativar() {
        ativo = true;
    }

    public void desativar() {
        ativo = false;
    }

    public UUID getVendedorId() {
        return vendedorId;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public OffsetDateTime getUltimaAtribuicaoEm() {
        return ultimaAtribuicaoEm;
    }
}
