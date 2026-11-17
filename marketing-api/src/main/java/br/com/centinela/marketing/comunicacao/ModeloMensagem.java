package br.com.centinela.marketing.comunicacao;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Modelo de e-mail para confirmação de boas-vindas (4.5) ou cadência de aquecimento das etapas
 * frias (4.2), norteador §3 e §9.4.
 */
@Entity
@Table(name = "modelos_mensagem")
@SQLDelete(sql = "UPDATE marketing.modelos_mensagem SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ModeloMensagem extends EntidadeBase {

    @Column(name = "tipo", nullable = false, updatable = false)
    @Convert(converter = TipoModeloMensagem.Conversor.class)
    private TipoModeloMensagem tipo;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "canal", nullable = false, length = 20)
    @Convert(converter = CanalComunicacao.Conversor.class)
    private CanalComunicacao canal = CanalComunicacao.EMAIL;

    /** Só em {@code aquecimento}: 0 ou 1; nulo vale para qualquer etapa fria. */
    @Column(name = "etapa_alvo")
    private Short etapaAlvo;

    /** Só em {@code aquecimento}: posição na cadência (1º toque, 2º toque...). */
    @Column(name = "ordem_sequencia")
    private Integer ordemSequencia;

    /** Só em {@code aquecimento}: dias de espera desde o toque anterior. */
    @Column(name = "dias_apos_anterior")
    private Integer diasAposAnterior;

    @Column(name = "assunto", nullable = false, length = 255)
    private String assunto;

    @Column(name = "corpo", nullable = false)
    private String corpo;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected ModeloMensagem() {
    }

    /** Cria um modelo de boas-vindas (sempre ativo e sem campos de cadência). */
    public static ModeloMensagem deBoasVindas(String nome, String assunto, String corpo) {
        ModeloMensagem modelo = new ModeloMensagem();
        modelo.tipo = TipoModeloMensagem.BOAS_VINDAS;
        modelo.canal = CanalComunicacao.EMAIL;
        modelo.alterarBoasVindas(nome, assunto, corpo);
        return modelo;
    }

    /** Modelo inicial de boas-vindas criado na primeira vez que o tenant usa o módulo (D7, §3). */
    public static ModeloMensagem boasVindasPadrao() {
        return deBoasVindas(
                "Confirmação de boas-vindas",
                "Recebemos o seu contato, {{nome}}!",
                """
                Olá, {{nome}}!

                Confirmamos o recebimento da sua solicitação enviada por {{origem}}. \
                Nossa equipe comercial já recebeu os seus dados e entrará em contato em breve.

                Atenciosamente,
                Equipe de Atendimento""");
    }

    /** Cria um modelo de aquecimento na cadência das etapas frias (0 ou 1). */
    public static ModeloMensagem deAquecimento(String nome, Integer etapaAlvo, int ordemSequencia,
            int diasAposAnterior, String assunto, String corpo, boolean ativo) {
        ModeloMensagem modelo = new ModeloMensagem();
        modelo.tipo = TipoModeloMensagem.AQUECIMENTO;
        modelo.canal = CanalComunicacao.EMAIL;
        modelo.alterarAquecimento(nome, etapaAlvo, ordemSequencia, diasAposAnterior, assunto, corpo, ativo);
        return modelo;
    }

    public void alterarBoasVindas(String nome, String assunto, String corpo) {
        if (tipo != TipoModeloMensagem.BOAS_VINDAS) {
            throw new IllegalStateException("Modelo não é de boas-vindas.");
        }
        validarTexto(nome, "O nome do modelo é obrigatório.", 120);
        validarTexto(assunto, "O assunto do e-mail é obrigatório.", 255);
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("O corpo do e-mail é obrigatório.");
        }
        this.nome = nome.strip();
        this.assunto = assunto.strip();
        this.corpo = corpo.strip();
        this.etapaAlvo = null;
        this.ordemSequencia = null;
        this.diasAposAnterior = null;
        this.ativo = true;
    }

    public void alterarAquecimento(String nome, Integer etapaAlvo, int ordemSequencia, int diasAposAnterior,
            String assunto, String corpo, boolean ativo) {
        if (tipo != TipoModeloMensagem.AQUECIMENTO) {
            throw new IllegalStateException("Modelo não é de aquecimento.");
        }
        validarTexto(nome, "O nome do modelo é obrigatório.", 120);
        validarTexto(assunto, "O assunto do e-mail é obrigatório.", 255);
        if (corpo == null || corpo.isBlank()) {
            throw new IllegalArgumentException("O corpo do e-mail é obrigatório.");
        }
        if (etapaAlvo != null && etapaAlvo != 0 && etapaAlvo != 1) {
            throw new IllegalArgumentException("A etapa alvo do aquecimento deve ser 0, 1 ou vazia (qualquer etapa fria).");
        }
        if (ordemSequencia <= 0) {
            throw new IllegalArgumentException("A posição na sequência deve ser maior que zero.");
        }
        if (diasAposAnterior < 0) {
            throw new IllegalArgumentException("Os dias após o toque anterior não podem ser negativos.");
        }
        this.nome = nome.strip();
        this.etapaAlvo = etapaAlvo == null ? null : etapaAlvo.shortValue();
        this.ordemSequencia = ordemSequencia;
        this.diasAposAnterior = diasAposAnterior;
        this.assunto = assunto.strip();
        this.corpo = corpo.strip();
        this.ativo = ativo;
    }

    private static void validarTexto(String valor, String mensagem, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        if (valor.strip().length() > tamanhoMaximo) {
            throw new IllegalArgumentException("Texto excede o limite de " + tamanhoMaximo + " caracteres.");
        }
    }

    public TipoModeloMensagem getTipo() {
        return tipo;
    }

    public String getNome() {
        return nome;
    }

    public CanalComunicacao getCanal() {
        return canal;
    }

    public Integer getEtapaAlvo() {
        return etapaAlvo == null ? null : etapaAlvo.intValue();
    }

    public Integer getOrdemSequencia() {
        return ordemSequencia;
    }

    public Integer getDiasAposAnterior() {
        return diasAposAnterior;
    }

    public String getAssunto() {
        return assunto;
    }

    public String getCorpo() {
        return corpo;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
