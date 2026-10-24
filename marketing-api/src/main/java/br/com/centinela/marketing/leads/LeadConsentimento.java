package br.com.centinela.marketing.leads;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Evidência de um aceite ou revogação de consentimento (LGPD 8.1, RC03). É só de inclusão: cada
 * linha é um fato e não muda depois de gravada. O estado atual fica em {@link Lead}.
 */
@Entity
@Table(name = "lead_consentimentos")
@SQLDelete(sql = "UPDATE marketing.lead_consentimentos SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeadConsentimento extends EntidadeBase {

    public static final String FINALIDADE_MARKETING = "marketing";

    static final int TAMANHO_MAXIMO_USER_AGENT = 500;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "finalidade", nullable = false, updatable = false)
    private String finalidade;

    @Column(name = "aceito", nullable = false, updatable = false)
    private boolean aceito;

    @Column(name = "registrado_em", nullable = false, updatable = false)
    private OffsetDateTime registradoEm;

    // ip e user_agent só mudam na anonimização (4.3)
    @Column(name = "ip")
    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "versao_termo", updatable = false)
    private String versaoTermo;

    @Column(name = "origem", nullable = false, updatable = false)
    @Convert(converter = OrigemConsentimento.Conversor.class)
    private OrigemConsentimento origem;

    @Column(name = "formulario_id", updatable = false)
    private UUID formularioId;

    @Column(name = "formulario_versao", updatable = false)
    private Integer formularioVersao;

    @Column(name = "envio_id", updatable = false)
    private UUID envioId;

    @Column(name = "evento_id", updatable = false)
    private UUID eventoId;

    protected LeadConsentimento() {
    }

    private LeadConsentimento(UUID leadId, OrigemConsentimento origem, boolean aceito,
            OffsetDateTime registradoEm, String ip, String userAgent) {
        this.leadId = Objects.requireNonNull(leadId, "leadId");
        this.origem = Objects.requireNonNull(origem, "origem");
        this.registradoEm = Objects.requireNonNull(registradoEm, "registradoEm");
        this.finalidade = FINALIDADE_MARKETING;
        this.aceito = aceito;
        this.ip = ip;
        this.userAgent = limitarUserAgent(userAgent);
    }

    /** Consentimento que chegou num evento do Landing (etapas 1, 2 e 3). */
    public static LeadConsentimento doLanding(UUID leadId, OrigemConsentimento origem, boolean aceito,
            OffsetDateTime registradoEm, String ip, String userAgent, String versaoTermo,
            UUID formularioId, Integer formularioVersao, UUID envioId, UUID eventoId) {
        if (origem == OrigemConsentimento.DESCADASTRO) {
            throw new IllegalArgumentException("Use revogacaoPorDescadastro para o descadastro.");
        }
        LeadConsentimento consentimento = new LeadConsentimento(leadId, origem, aceito, registradoEm, ip, userAgent);
        consentimento.versaoTermo = versaoTermo;
        consentimento.formularioId = formularioId;
        consentimento.formularioVersao = formularioVersao;
        consentimento.envioId = envioId;
        consentimento.eventoId = eventoId;
        return consentimento;
    }

    /** Revogação feita pelo link de descadastro, com o IP e o User-Agent da requisição. */
    public static LeadConsentimento revogacaoPorDescadastro(UUID leadId, OffsetDateTime registradoEm,
            String ip, String userAgent) {
        return new LeadConsentimento(leadId, OrigemConsentimento.DESCADASTRO, false, registradoEm, ip, userAgent);
    }

    /**
     * Diz se este registro repete o anterior. Os eventos da etapa 2 chegam várias vezes; só grava
     * uma linha nova quando o aceite, o momento ou a versão do termo mudam (norteador §3).
     */
    public boolean repete(LeadConsentimento anterior) {
        return anterior != null
                && anterior.aceito == aceito
                && anterior.registradoEm.isEqual(registradoEm)
                && Objects.equals(anterior.versaoTermo, versaoTermo);
    }

    private static String limitarUserAgent(String userAgent) {
        if (userAgent == null || userAgent.length() <= TAMANHO_MAXIMO_USER_AGENT) {
            return userAgent;
        }
        return userAgent.substring(0, TAMANHO_MAXIMO_USER_AGENT);
    }

    public UUID getLeadId() {
        return leadId;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public boolean isAceito() {
        return aceito;
    }

    public OffsetDateTime getRegistradoEm() {
        return registradoEm;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getVersaoTermo() {
        return versaoTermo;
    }

    public OrigemConsentimento getOrigem() {
        return origem;
    }

    public UUID getFormularioId() {
        return formularioId;
    }

    public Integer getFormularioVersao() {
        return formularioVersao;
    }

    public UUID getEnvioId() {
        return envioId;
    }

    public UUID getEventoId() {
        return eventoId;
    }
}
