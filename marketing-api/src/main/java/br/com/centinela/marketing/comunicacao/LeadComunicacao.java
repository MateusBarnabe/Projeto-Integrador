package br.com.centinela.marketing.comunicacao;

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
 * Histórico de uma comunicação enviada ao lead (boas-vindas 4.5 ou aquecimento 4.2), norteador §3.
 */
@Entity
@Table(name = "lead_comunicacoes")
@SQLDelete(sql = "UPDATE marketing.lead_comunicacoes SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeadComunicacao extends EntidadeBase {

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "canal", nullable = false, updatable = false, length = 20)
    @Convert(converter = CanalComunicacao.Conversor.class)
    private CanalComunicacao canal;

    @Column(name = "modelo_id", nullable = false, updatable = false)
    private UUID modeloId;

    @Column(name = "status", nullable = false, length = 20)
    @Convert(converter = StatusComunicacao.Conversor.class)
    private StatusComunicacao status;

    @Column(name = "enviado_em", nullable = false, updatable = false)
    private OffsetDateTime enviadoEm;

    protected LeadComunicacao() {
    }

    public LeadComunicacao(UUID leadId, UUID modeloId, StatusComunicacao status, OffsetDateTime enviadoEm) {
        this.leadId = Objects.requireNonNull(leadId, "leadId");
        this.modeloId = Objects.requireNonNull(modeloId, "modeloId");
        this.status = Objects.requireNonNull(status, "status");
        this.enviadoEm = Objects.requireNonNull(enviadoEm, "enviadoEm");
        this.canal = CanalComunicacao.EMAIL;
    }

    public static LeadComunicacao enviada(UUID leadId, UUID modeloId, OffsetDateTime enviadoEm) {
        return new LeadComunicacao(leadId, modeloId, StatusComunicacao.ENVIADO, enviadoEm);
    }

    public static LeadComunicacao falhou(UUID leadId, UUID modeloId, OffsetDateTime tentadoEm) {
        return new LeadComunicacao(leadId, modeloId, StatusComunicacao.FALHOU, tentadoEm);
    }

    public UUID getLeadId() {
        return leadId;
    }

    public CanalComunicacao getCanal() {
        return canal;
    }

    public UUID getModeloId() {
        return modeloId;
    }

    public StatusComunicacao getStatus() {
        return status;
    }

    public OffsetDateTime getEnviadoEm() {
        return enviadoEm;
    }
}
