package br.com.centinela.marketing.leads;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Generated;
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
 * Item da timeline e da auditoria do lead (norteador §3). A {@code sequencia} é gerada pelo banco
 * e dá a ordem global usada como id das mensagens SSE do painel.
 */
@Entity
@Table(name = "lead_eventos")
@SQLDelete(sql = "UPDATE marketing.lead_eventos SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeadEvento extends EntidadeBase {

    @Generated
    @Column(name = "sequencia", insertable = false, updatable = false)
    private Long sequencia;

    @Column(name = "lead_id", nullable = false, updatable = false)
    private UUID leadId;

    @Column(name = "tipo_evento", nullable = false, updatable = false)
    @Convert(converter = TipoEventoLead.Conversor.class)
    private TipoEventoLead tipoEvento;

    @Column(name = "etapa_anterior", updatable = false)
    private Short etapaAnterior;

    @Column(name = "etapa_nova", updatable = false)
    private Short etapaNova;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", updatable = false)
    private Map<String, Object> payload;

    protected LeadEvento() {
    }

    public LeadEvento(UUID leadId, TipoEventoLead tipoEvento, Map<String, Object> payload) {
        this.leadId = Objects.requireNonNull(leadId, "leadId");
        this.tipoEvento = Objects.requireNonNull(tipoEvento, "tipoEvento");
        this.payload = payload;
    }

    /** Registro de mudança de etapa do funil, com a etapa de antes e a de depois. */
    public static LeadEvento etapaAlterada(UUID leadId, int etapaAnterior, int etapaNova,
            Map<String, Object> payload) {
        LeadEvento evento = new LeadEvento(leadId, TipoEventoLead.ETAPA_ALTERADA, payload);
        evento.etapaAnterior = (short) etapaAnterior;
        evento.etapaNova = (short) etapaNova;
        return evento;
    }

    public Long getSequencia() {
        return sequencia;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public TipoEventoLead getTipoEvento() {
        return tipoEvento;
    }

    public Integer getEtapaAnterior() {
        return etapaAnterior == null ? null : etapaAnterior.intValue();
    }

    public Integer getEtapaNova() {
        return etapaNova == null ? null : etapaNova.intValue();
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
