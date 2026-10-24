package br.com.centinela.marketing.formularios;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Versão publicada de um formulário. Nunca muda depois de gravada: cada lead guarda a versão que
 * respondeu. O banco também barra alterações (trigger da V5). Criada por {@link Formulario#publicar}.
 */
@Entity
@Immutable
@Table(name = "formulario_versoes")
@SQLDelete(sql = "UPDATE marketing.formulario_versoes SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class FormularioVersao extends EntidadeBase {

    @Column(name = "formulario_id", nullable = false, updatable = false)
    private UUID formularioId;

    @Column(name = "versao", nullable = false, updatable = false)
    private int versao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "campos", nullable = false, updatable = false)
    private List<Campo> campos;

    @Column(name = "publicado_em", nullable = false, updatable = false)
    private OffsetDateTime publicadoEm;

    protected FormularioVersao() {
    }

    FormularioVersao(UUID formularioId, int versao, List<Campo> campos, OffsetDateTime publicadoEm) {
        this.formularioId = formularioId;
        this.versao = versao;
        this.campos = validarCampos(campos);
        this.publicadoEm = publicadoEm;
    }

    private static List<Campo> validarCampos(List<Campo> campos) {
        if (campos == null || campos.isEmpty()) {
            throw new IllegalArgumentException("Uma versão do formulário precisa de ao menos um campo.");
        }
        Set<UUID> ids = new HashSet<>();
        Set<String> chaves = new HashSet<>();
        for (Campo campo : campos) {
            if (!ids.add(campo.id())) {
                throw new IllegalArgumentException("Campo repetido: " + campo.id());
            }
            if (!chaves.add(campo.chave())) {
                throw new IllegalArgumentException("Chave de campo repetida: " + campo.chave());
            }
        }
        return campos.stream().sorted(Comparator.comparingInt(Campo::ordem)).toList();
    }

    public UUID getFormularioId() {
        return formularioId;
    }

    public int getVersao() {
        return versao;
    }

    /** Campos na ordem de exibição. */
    public List<Campo> getCampos() {
        return campos;
    }

    public OffsetDateTime getPublicadoEm() {
        return publicadoEm;
    }
}
