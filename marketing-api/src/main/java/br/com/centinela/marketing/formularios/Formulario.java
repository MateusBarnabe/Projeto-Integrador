package br.com.centinela.marketing.formularios;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Formulário da landing page (§9.5). Um tenant pode ter vários, por exemplo um por campanha. O
 * conteúdo fica nas versões ({@link FormularioVersao}); aqui só a versão que o Landing mostra.
 */
@Entity
@Table(name = "formularios")
@SQLDelete(sql = "UPDATE marketing.formularios SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Formulario extends EntidadeBase {

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "versao_ativa")
    private Integer versaoAtiva;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected Formulario() {
    }

    public Formulario(String nome) {
        renomear(nome);
    }

    public void renomear(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Formulário sem nome.");
        }
        this.nome = nome.strip();
    }

    public void ativar() {
        ativo = true;
    }

    public void desativar() {
        ativo = false;
    }

    /**
     * Cria a próxima versão com os campos informados e passa a mostrá-la no Landing. A versão
     * anterior continua gravada sem mudança. O formulário precisa já estar salvo, para ter id.
     */
    public FormularioVersao publicar(List<Campo> campos, OffsetDateTime publicadoEm) {
        if (getId() == null) {
            throw new IllegalStateException("Salve o formulário antes de publicar uma versão.");
        }
        int proxima = versaoAtiva == null ? 1 : versaoAtiva + 1;
        FormularioVersao versao = new FormularioVersao(getId(), proxima, campos,
                Objects.requireNonNull(publicadoEm, "publicadoEm"));
        versaoAtiva = proxima;
        return versao;
    }

    public boolean foiPublicado() {
        return versaoAtiva != null;
    }

    public String getNome() {
        return nome;
    }

    public Integer getVersaoAtiva() {
        return versaoAtiva;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
