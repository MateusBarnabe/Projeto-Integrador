package br.com.centinela.marketing.rastreamento;

import java.time.Duration;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Rastreamento do clique no anúncio, uma linha por tenant (R1). Sem linha, valem os valores de
 * {@link #padrao()}: ligado, com 30 minutos para o visitante converter.
 */
@Entity
@Table(name = "rastreamento_configuracoes")
@SQLDelete(sql = "UPDATE marketing.rastreamento_configuracoes SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class RastreamentoConfiguracao extends EntidadeBase {

    public static final int PRAZO_PADRAO_MINUTOS = 30;
    public static final int PRAZO_MINIMO_MINUTOS = 1;
    /** Sete dias. */
    public static final int PRAZO_MAXIMO_MINUTOS = 10_080;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "prazo_conversao_minutos", nullable = false)
    private int prazoConversaoMinutos = PRAZO_PADRAO_MINUTOS;

    protected RastreamentoConfiguracao() {
    }

    public static RastreamentoConfiguracao padrao() {
        return new RastreamentoConfiguracao();
    }

    public void alterar(boolean ativo, int prazoConversaoMinutos) {
        if (prazoConversaoMinutos < PRAZO_MINIMO_MINUTOS || prazoConversaoMinutos > PRAZO_MAXIMO_MINUTOS) {
            throw new IllegalArgumentException("Prazo de conversão deve ficar entre 1 minuto e 7 dias.");
        }
        this.ativo = ativo;
        this.prazoConversaoMinutos = prazoConversaoMinutos;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public int getPrazoConversaoMinutos() {
        return prazoConversaoMinutos;
    }

    public Duration prazo() {
        return Duration.ofMinutes(prazoConversaoMinutos);
    }
}
