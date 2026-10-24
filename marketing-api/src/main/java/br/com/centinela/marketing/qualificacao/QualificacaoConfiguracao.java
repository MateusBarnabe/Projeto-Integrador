package br.com.centinela.marketing.qualificacao;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Faixas de qualificação e regras do rodízio, uma linha por tenant (§9.3). Os valores de
 * {@link #padrao()} são os iniciais das Regras de Negócio; cada tenant ajusta.
 */
@Entity
@Table(name = "qualificacao_configuracoes")
@SQLDelete(sql = "UPDATE marketing.qualificacao_configuracoes SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class QualificacaoConfiguracao extends EntidadeBase {

    @Column(name = "limite_mql_quente", nullable = false)
    private int limiteMqlQuente;

    @Column(name = "limite_mql_morno", nullable = false)
    private int limiteMqlMorno;

    @Column(name = "rodizio_ativo", nullable = false)
    private boolean rodizioAtivo;

    @Column(name = "horario_comercial_inicio", nullable = false)
    private LocalTime horarioComercialInicio;

    @Column(name = "horario_comercial_fim", nullable = false)
    private LocalTime horarioComercialFim;

    /** 1 (segunda) a 7 (domingo), a mesma numeração de {@link DayOfWeek#getValue()}. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "dias_atendimento", nullable = false)
    private Short[] diasAtendimento;

    @Column(name = "minutos_transbordo")
    private Integer minutosTransbordo;

    protected QualificacaoConfiguracao() {
    }

    /** MQL quente a partir de 70, morno de 40 a 69, rodízio ligado, de segunda a sexta das 8h às 18h. */
    public static QualificacaoConfiguracao padrao() {
        QualificacaoConfiguracao configuracao = new QualificacaoConfiguracao();
        configuracao.definirLimites(70, 40);
        configuracao.definirAtendimento(LocalTime.of(8, 0), LocalTime.of(18, 0),
                EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        configuracao.rodizioAtivo = true;
        configuracao.minutosTransbordo = 15;
        return configuracao;
    }

    public void definirLimites(int limiteMqlQuente, int limiteMqlMorno) {
        if (limiteMqlMorno < 0 || limiteMqlMorno >= limiteMqlQuente) {
            throw new IllegalArgumentException("O limite de MQL morno deve ficar entre 0 e o de MQL quente.");
        }
        this.limiteMqlQuente = limiteMqlQuente;
        this.limiteMqlMorno = limiteMqlMorno;
    }

    public void definirAtendimento(LocalTime inicio, LocalTime fim, Set<DayOfWeek> dias) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new IllegalArgumentException("O horário comercial precisa começar antes de terminar.");
        }
        if (dias == null || dias.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um dia de atendimento.");
        }
        this.horarioComercialInicio = inicio;
        this.horarioComercialFim = fim;
        this.diasAtendimento = dias.stream().sorted().map(dia -> (short) dia.getValue()).toArray(Short[]::new);
    }

    public void definirRodizio(boolean ativo, Integer minutosTransbordo) {
        if (minutosTransbordo != null && minutosTransbordo <= 0) {
            throw new IllegalArgumentException("O transbordo precisa de um tempo positivo.");
        }
        this.rodizioAtivo = ativo;
        this.minutosTransbordo = minutosTransbordo;
    }

    public int getLimiteMqlQuente() {
        return limiteMqlQuente;
    }

    public int getLimiteMqlMorno() {
        return limiteMqlMorno;
    }

    public boolean isRodizioAtivo() {
        return rodizioAtivo;
    }

    public LocalTime getHorarioComercialInicio() {
        return horarioComercialInicio;
    }

    public LocalTime getHorarioComercialFim() {
        return horarioComercialFim;
    }

    public Set<DayOfWeek> getDiasAtendimento() {
        EnumSet<DayOfWeek> dias = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(diasAtendimento).forEach(dia -> dias.add(DayOfWeek.of(dia)));
        return dias;
    }

    public Integer getMinutosTransbordo() {
        return minutosTransbordo;
    }
}
