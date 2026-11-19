package br.com.centinela.marketing.meta;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Lead do Meta Ads que o webhook recebeu e o job 4.1 ainda vai buscar na Graph API (D2, norteador §3).
 * Guarda só os identificadores do webhook: nome, e-mail e respostas vão direto para {@code leads}.
 * O momento do recebimento é o {@code created_at}.
 *
 * <p>Quem grava o webhook é o {@link BufferDoMeta} (SQL com {@code ON CONFLICT}, para a reentrega
 * concorrente não duplicar nem falhar); a entidade serve ao job e aos testes.
 */
@Entity
@Table(name = "leads_entrada_buffer")
@SQLDelete(sql = "UPDATE marketing.leads_entrada_buffer SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LeadEntradaBuffer extends EntidadeBase {

    /** Ids do corpo do webhook que ficam em {@code payload_bruto}, e nada além deles. */
    public static final String CHAVE_LEADGEN_ID = "leadgen_id";
    public static final String CHAVE_PAGE_ID = "page_id";
    public static final String CHAVE_FORM_ID = "form_id";
    public static final String CHAVE_AD_ID = "ad_id";

    @Column(name = "meta_lead_id", nullable = false, updatable = false, length = 100)
    private String metaLeadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_bruto", nullable = false, updatable = false)
    private Map<String, Object> payloadBruto;

    @Column(name = "processado", nullable = false)
    private boolean processado;

    @Column(name = "processado_em")
    private OffsetDateTime processadoEm;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "falha_permanente", nullable = false)
    private boolean falhaPermanente;

    protected LeadEntradaBuffer() {
    }

    public LeadEntradaBuffer(String metaLeadId, Map<String, Object> payloadBruto) {
        this.metaLeadId = Objects.requireNonNull(metaLeadId, "metaLeadId");
        this.payloadBruto = payloadBruto == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payloadBruto);
    }

    /** O lead virou {@code leads}: a linha sai da fila. */
    public void marcarProcessado(OffsetDateTime em) {
        this.processado = true;
        this.processadoEm = Objects.requireNonNull(em, "em");
    }

    /**
     * Conta uma falha do job. Na {@code maximoDeTentativas}-ésima, a linha vira {@code falha_permanente}
     * e sai da fila, para investigação manual.
     *
     * @return {@code true} se a falha virou permanente agora
     */
    public boolean registrarFalha(int maximoDeTentativas) {
        tentativas++;
        if (!falhaPermanente && tentativas >= maximoDeTentativas) {
            falhaPermanente = true;
            return true;
        }
        return false;
    }

    public String getMetaLeadId() {
        return metaLeadId;
    }

    public Map<String, Object> getPayloadBruto() {
        return payloadBruto;
    }

    public String getPageId() {
        return texto(CHAVE_PAGE_ID);
    }

    public String getFormId() {
        return texto(CHAVE_FORM_ID);
    }

    public String getAdId() {
        return texto(CHAVE_AD_ID);
    }

    public boolean isProcessado() {
        return processado;
    }

    public OffsetDateTime getProcessadoEm() {
        return processadoEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public boolean isFalhaPermanente() {
        return falhaPermanente;
    }

    private String texto(String chave) {
        Object valor = payloadBruto == null ? null : payloadBruto.get(chave);
        return valor == null ? null : valor.toString();
    }
}
