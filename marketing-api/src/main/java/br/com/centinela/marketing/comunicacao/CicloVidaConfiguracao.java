package br.com.centinela.marketing.comunicacao;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Configuração do ciclo de vida do aquecimento e retenção LGPD, uma linha por tenant (norteador §3,
 * §4.2, §4.3 e §9.4).
 */
@Entity
@Table(name = "ciclo_vida_configuracoes")
@SQLDelete(sql = "UPDATE marketing.ciclo_vida_configuracoes SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CicloVidaConfiguracao extends EntidadeBase {

    @Column(name = "max_tentativas_aquecimento", nullable = false)
    private int maxTentativasAquecimento;

    @Column(name = "meses_retencao_pos_esgotamento", nullable = false)
    private int mesesRetencaoPosEsgotamento;

    @Column(name = "meses_retencao_pos_optout", nullable = false)
    private int mesesRetencaoPosOptout;

    protected CicloVidaConfiguracao() {
    }

    public CicloVidaConfiguracao(int maxTentativasAquecimento, int mesesRetencaoPosEsgotamento,
            int mesesRetencaoPosOptout) {
        alterar(maxTentativasAquecimento, mesesRetencaoPosEsgotamento, mesesRetencaoPosOptout);
    }

    /** Valores iniciais por tenant (D7): até 3 tentativas de aquecimento, 6 meses pós-esgotamento e 12 meses pós-opt-out. */
    public static CicloVidaConfiguracao padrao() {
        return new CicloVidaConfiguracao(3, 6, 12);
    }

    public void alterar(int maxTentativasAquecimento, int mesesRetencaoPosEsgotamento, int mesesRetencaoPosOptout) {
        if (maxTentativasAquecimento <= 0) {
            throw new IllegalArgumentException("O máximo de tentativas de aquecimento deve ser maior que zero.");
        }
        if (mesesRetencaoPosEsgotamento <= 0) {
            throw new IllegalArgumentException("O prazo de retenção após esgotamento deve ser de pelo menos 1 mês.");
        }
        if (mesesRetencaoPosOptout <= 0) {
            throw new IllegalArgumentException("O prazo de retenção após opt-out deve ser de pelo menos 1 mês.");
        }
        this.maxTentativasAquecimento = maxTentativasAquecimento;
        this.mesesRetencaoPosEsgotamento = mesesRetencaoPosEsgotamento;
        this.mesesRetencaoPosOptout = mesesRetencaoPosOptout;
    }

    public int getMaxTentativasAquecimento() {
        return maxTentativasAquecimento;
    }

    public int getMesesRetencaoPosEsgotamento() {
        return mesesRetencaoPosEsgotamento;
    }

    public int getMesesRetencaoPosOptout() {
        return mesesRetencaoPosOptout;
    }
}
