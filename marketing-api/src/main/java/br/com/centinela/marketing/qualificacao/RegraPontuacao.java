package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Critério da pontuação do lead (§9.3). Cada critério conta uma vez por lead: se duas regras do
 * mesmo critério baterem, vale a de mais pontos (isso é do motor de pontuação, Q1).
 */
@Entity
@Table(name = "regras_pontuacao")
@SQLDelete(sql = "UPDATE marketing.regras_pontuacao SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RegraPontuacao extends EntidadeBase {

    @Column(name = "criterio", nullable = false)
    private String criterio;

    @Column(name = "fonte", nullable = false)
    @Convert(converter = FonteRegra.Conversor.class)
    private FonteRegra fonte;

    @Column(name = "campo_chave")
    private String campoChave;

    /** Em {@code campo_formulario}, as respostas (texto); em {@code utm}, objetos com source, medium etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores", nullable = false)
    private List<Object> valores;

    @Column(name = "pontos", nullable = false)
    private int pontos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected RegraPontuacao() {
    }

    private RegraPontuacao(String criterio, FonteRegra fonte, String campoChave, List<?> valores, int pontos) {
        alterar(criterio, fonte, campoChave, valores, pontos);
    }

    /** Regra sobre uma resposta do formulário, pela {@code chave} do campo. */
    public static RegraPontuacao porCampo(String criterio, String campoChave, List<String> valores, int pontos) {
        return new RegraPontuacao(criterio, FonteRegra.CAMPO_FORMULARIO, campoChave, valores, pontos);
    }

    /** Regra sobre a origem do tráfego, ex.: {@code [{"source": "google", "medium": "cpc"}, {"direto": true}]}. */
    public static RegraPontuacao porUtm(String criterio, List<?> valores, int pontos) {
        return new RegraPontuacao(criterio, FonteRegra.UTM, null, valores, pontos);
    }

    public void alterar(String criterio, FonteRegra fonte, String campoChave, List<?> valores, int pontos) {
        if (criterio == null || criterio.isBlank()) {
            throw new IllegalArgumentException("Regra de pontuação sem critério.");
        }
        Objects.requireNonNull(fonte, "fonte");
        if (fonte == FonteRegra.CAMPO_FORMULARIO && (campoChave == null || campoChave.isBlank())) {
            throw new IllegalArgumentException("Regra sobre campo do formulário precisa da chave do campo.");
        }
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Regra de pontuação sem valores que a disparem.");
        }
        if (pontos < 0) {
            throw new IllegalArgumentException("Pontos não podem ser negativos.");
        }
        this.criterio = criterio.strip();
        this.fonte = fonte;
        this.campoChave = fonte == FonteRegra.UTM ? null : campoChave.strip();
        this.valores = List.copyOf(valores);
        this.pontos = pontos;
    }

    public void ativar() {
        ativo = true;
    }

    public void desativar() {
        ativo = false;
    }

    public String getCriterio() {
        return criterio;
    }

    public FonteRegra getFonte() {
        return fonte;
    }

    public String getCampoChave() {
        return campoChave;
    }

    public List<Object> getValores() {
        return valores;
    }

    public int getPontos() {
        return pontos;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
