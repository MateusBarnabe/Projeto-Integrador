package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
 *
 * <p>O formulário é do Landing: a regra de campo aponta para um campo de um formulário pelos ids do
 * resumo do formulário ({@code GET /api/landing/formularios/{id}/resumo}). Só o UUID, sem FK.
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

    @Column(name = "formulario_id")
    private UUID formularioId;

    @Column(name = "campo_id")
    private UUID campoId;

    @Column(name = "campo_meta")
    private String campoMeta;

    /** Métrica da interação nas regras de {@code comportamento} ({@link MetricaComportamento}). */
    @Column(name = "campo_comportamento")
    private String campoComportamento;

    /**
     * Em campos, as respostas (texto); em {@code utm}, objetos com source, medium etc.; em
     * {@code comportamento}, {@code {"minimo": n}} nas métricas numéricas e textos nas outras.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valores", nullable = false)
    private List<Object> valores;

    @Column(name = "pontos", nullable = false)
    private int pontos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected RegraPontuacao() {
    }

    private RegraPontuacao(String criterio, List<?> valores, int pontos) {
        alterar(criterio, valores, pontos);
    }

    /** Regra sobre a resposta de um campo de formulário do Landing. */
    public static RegraPontuacao porCampo(String criterio, UUID formularioId, UUID campoId,
            List<String> valores, int pontos) {
        RegraPontuacao regra = new RegraPontuacao(criterio, valores, pontos);
        regra.apontarParaCampo(formularioId, campoId);
        return regra;
    }

    /** Regra sobre a resposta de uma pergunta do formulário instantâneo do Meta, pelo nome da pergunta. */
    public static RegraPontuacao porCampoMeta(String criterio, String pergunta, List<String> valores, int pontos) {
        RegraPontuacao regra = new RegraPontuacao(criterio, valores, pontos);
        regra.apontarParaCampoMeta(pergunta);
        return regra;
    }

    /** Regra sobre a origem do tráfego, ex.: {@code [{"source": "google", "medium": "cpc"}, {"direto": true}]}. */
    public static RegraPontuacao porUtm(String criterio, List<?> valores, int pontos) {
        RegraPontuacao regra = new RegraPontuacao(criterio, valores, pontos);
        regra.apontarParaUtm();
        return regra;
    }

    /**
     * Regra sobre o comportamento do visitante na landing page (R1), ex.: tempo na página
     * {@code [{"minimo": 60}]} ou página visitada {@code ["precos"]}.
     */
    public static RegraPontuacao porComportamento(String criterio, MetricaComportamento metrica, List<?> valores,
            int pontos) {
        RegraPontuacao regra = new RegraPontuacao(criterio, valores, pontos);
        regra.apontarParaComportamento(metrica);
        return regra;
    }

    public void alterar(String criterio, List<?> valores, int pontos) {
        if (criterio == null || criterio.isBlank()) {
            throw new IllegalArgumentException("Regra de pontuação sem critério.");
        }
        if (valores == null || valores.isEmpty()) {
            throw new IllegalArgumentException("Regra de pontuação sem valores que a disparem.");
        }
        if (pontos < 0) {
            throw new IllegalArgumentException("Pontos não podem ser negativos.");
        }
        this.criterio = criterio.strip();
        this.valores = List.copyOf(valores);
        this.pontos = pontos;
    }

    public void apontarParaCampo(UUID formularioId, UUID campoId) {
        Objects.requireNonNull(formularioId, "Regra sobre campo do formulário precisa do formulário.");
        Objects.requireNonNull(campoId, "Regra sobre campo do formulário precisa do campo.");
        this.fonte = FonteRegra.CAMPO_FORMULARIO;
        this.formularioId = formularioId;
        this.campoId = campoId;
        this.campoMeta = null;
        this.campoComportamento = null;
    }

    public void apontarParaComportamento(MetricaComportamento metrica) {
        Objects.requireNonNull(metrica, "Regra de comportamento precisa da métrica.");
        this.fonte = FonteRegra.COMPORTAMENTO;
        this.formularioId = null;
        this.campoId = null;
        this.campoMeta = null;
        this.campoComportamento = metrica.valor();
    }

    public void apontarParaCampoMeta(String pergunta) {
        if (pergunta == null || pergunta.isBlank()) {
            throw new IllegalArgumentException("Regra sobre o Meta precisa do nome da pergunta.");
        }
        this.fonte = FonteRegra.CAMPO_META;
        this.formularioId = null;
        this.campoId = null;
        this.campoMeta = pergunta.strip();
        this.campoComportamento = null;
    }

    public void apontarParaUtm() {
        this.fonte = FonteRegra.UTM;
        this.formularioId = null;
        this.campoId = null;
        this.campoMeta = null;
        this.campoComportamento = null;
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

    public UUID getFormularioId() {
        return formularioId;
    }

    public UUID getCampoId() {
        return campoId;
    }

    public String getCampoMeta() {
        return campoMeta;
    }

    public String getCampoComportamento() {
        return campoComportamento;
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
