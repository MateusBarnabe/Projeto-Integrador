package br.com.centinela.marketing.entrada;

import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.domain.Persistable;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Registro bruto normalizado antes da drenagem para a entidade definitiva de lead. */
@Entity
@Table(name = "leads_entrada_buffer")
@SQLDelete(sql = "UPDATE marketing.leads_entrada_buffer SET deleted_at = now() WHERE id = ?")
public class LeadEntradaBuffer extends EntidadeBase implements Persistable<UUID> {

    @Column(name = "evento_id", nullable = false, unique = true, updatable = false)
    private UUID eventoId;

    @Column(nullable = false, length = 120)
    private String tipo;

    @Column(name = "visitante_id")
    private UUID visitanteId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 30)
    private String whatsapp;

    @Column(length = 30)
    private String telefone;

    @Column(name = "utm_source", length = 120)
    private String utmSource;

    @Column(name = "utm_medium", length = 120)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 200)
    private String utmCampaign;

    @Column(name = "utm_content", length = 200)
    private String utmContent;

    @Column(columnDefinition = "text")
    private String referrer;

    @Column(name = "dados_tecnicos", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dadosTecnicos;

    @Column(name = "dados_comportamentais", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String dadosComportamentais;

    @Column(name = "processado", nullable = false)
    private boolean processado;

    protected LeadEntradaBuffer() {
    }

    static LeadEntradaBuffer de(LeadEnriquecidoEvento evento, String dadosTecnicos,
            String dadosComportamentais) {
        LeadEnriquecido dados = evento.dados();
        LeadEntradaBuffer buffer = new LeadEntradaBuffer();
        buffer.eventoId = evento.id();
        buffer.tipo = evento.tipo();
        buffer.visitanteId = dados.visitanteId();
        buffer.nome = dados.nome().trim();
        buffer.email = dados.email().trim().toLowerCase();
        buffer.whatsapp = dados.whatsapp();
        buffer.telefone = dados.telefone();
        buffer.utmSource = dados.utmSource();
        buffer.utmMedium = dados.utmMedium();
        buffer.utmCampaign = dados.utmCampaign();
        buffer.utmContent = dados.utmContent();
        buffer.referrer = dados.referrer();
        buffer.dadosTecnicos = dadosTecnicos;
        buffer.dadosComportamentais = dadosComportamentais;
        buffer.processado = false;
        return buffer;
    }

    public UUID getEventoId() {
        return eventoId;
    }

    public String getTipo() {
        return tipo;
    }

    public UUID getVisitanteId() {
        return visitanteId;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    public String getUtmContent() {
        return utmContent;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getDadosTecnicos() {
        return dadosTecnicos;
    }

    public String getDadosComportamentais() {
        return dadosComportamentais;
    }

    public boolean isProcessado() {
        return processado;
    }
}