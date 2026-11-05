package br.com.centinela.marketing.leads;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Registro central do lead (norteador §3). Existe a partir do momento em que há um identificador
 * de contato; o visitante de etapa 0 sem contato fica só no buffer de entrada.
 */
@Entity
@Table(name = "leads")
@SQLDelete(sql = "UPDATE marketing.leads SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Lead extends EntidadeBase {

    public static final int ETAPA_MINIMA = 0;
    public static final int ETAPA_MAXIMA = 3;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Column(name = "etapa", nullable = false)
    private short etapa;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "temperatura", insertable = false, updatable = false)
    @Convert(converter = Temperatura.Conversor.class)
    private Temperatura temperatura;

    @Column(name = "origem", nullable = false, updatable = false)
    @Convert(converter = OrigemLead.Conversor.class)
    private OrigemLead origem;

    @Column(name = "nome")
    private String nome;

    @Column(name = "email")
    private String email;

    @Column(name = "telefone")
    private String telefone;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "utm_term")
    private String utmTerm;

    @Column(name = "utm_content")
    private String utmContent;

    @Column(name = "landing_page_url")
    private String landingPageUrl;

    @Column(name = "first_touch")
    private OffsetDateTime firstTouch;

    @Column(name = "last_touch")
    private OffsetDateTime lastTouch;

    @Column(name = "visitante_id")
    private UUID visitanteId;

    @Column(name = "envio_id")
    private UUID envioId;

    @Column(name = "formulario_id")
    private UUID formularioId;

    @Column(name = "formulario_versao")
    private Integer formularioVersao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_formulario")
    private Map<String, Object> dadosFormulario;

    @Column(name = "consentimento_marketing", nullable = false)
    private boolean consentimentoMarketing;

    @Column(name = "consentimento_em")
    private OffsetDateTime consentimentoEm;

    @Column(name = "boas_vindas_enviada_em")
    private OffsetDateTime boasVindasEnviadaEm;

    @Column(name = "meta_lead_id")
    private String metaLeadId;

    @Column(name = "crm_empresa_id")
    private UUID crmEmpresaId;

    @Column(name = "crm_contato_id")
    private UUID crmContatoId;

    @Column(name = "crm_oportunidade_id")
    private UUID crmOportunidadeId;

    @Column(name = "pontuacao")
    private Integer pontuacao;

    @Column(name = "faixa_qualificacao")
    @Convert(converter = FaixaQualificacao.Conversor.class)
    private FaixaQualificacao faixaQualificacao;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pontuacao_detalhe")
    private List<Map<String, Object>> pontuacaoDetalhe;

    @Column(name = "qualificado_em")
    private OffsetDateTime qualificadoEm;

    @Column(name = "vendedor_id")
    private UUID vendedorId;

    @Column(name = "vendedor_atribuido_em")
    private OffsetDateTime vendedorAtribuidoEm;

    @Column(name = "ultima_comunicacao_em")
    private OffsetDateTime ultimaComunicacaoEm;

    @Column(name = "tentativas_aquecimento", nullable = false)
    private int tentativasAquecimento;

    @Column(name = "status_aquecimento", nullable = false)
    @Convert(converter = StatusAquecimento.Conversor.class)
    private StatusAquecimento statusAquecimento = StatusAquecimento.ATIVO;

    @Column(name = "esgotado_em")
    private OffsetDateTime esgotadoEm;

    @Column(name = "opt_out_em")
    private OffsetDateTime optOutEm;

    @Column(name = "token_descadastro", nullable = false, updatable = false)
    private String tokenDescadastro;

    @Column(name = "anonimizado", nullable = false)
    private boolean anonimizado;

    protected Lead() {
    }

    public Lead(OrigemLead origem, int etapa) {
        this.origem = Objects.requireNonNull(origem, "origem");
        this.etapa = validarEtapa(etapa);
        this.tokenDescadastro = gerarTokenDescadastro();
    }

    /**
     * Leva o lead para a etapa informada. A etapa nunca é rebaixada (§2): uma etapa menor ou igual
     * à atual é ignorada.
     *
     * @return {@code true} se a etapa mudou
     */
    public boolean avancarEtapa(int novaEtapa) {
        short nova = validarEtapa(novaEtapa);
        if (nova <= etapa) {
            return false;
        }
        etapa = nova;
        return true;
    }

    /** Atualiza os dados de contato sem apagar o que já existe com um valor vazio. */
    public void atualizarContato(String nome, String email, String telefone, String whatsapp) {
        this.nome = preencher(this.nome, nome);
        this.email = preencher(this.email, email);
        this.telefone = preencher(this.telefone, telefone);
        this.whatsapp = preencher(this.whatsapp, whatsapp);
    }

    /** Liga o lead ao visitante do Landing, que é o mesmo da etapa 0 à 3. */
    public void vincularVisitante(UUID visitanteId) {
        this.visitanteId = preencher(this.visitanteId, visitanteId);
    }

    /** Guarda o {@code leadgen_id} do Meta, usado para descartar reentregas do webhook. */
    public void vincularMetaLead(String metaLeadId) {
        this.metaLeadId = preencher(this.metaLeadId, metaLeadId);
    }

    /**
     * Atualiza o estado atual do consentimento de marketing. A evidência de cada aceite e revogação
     * é gravada à parte, em {@link LeadConsentimento}.
     */
    public void registrarConsentimento(boolean aceito, OffsetDateTime registradoEm) {
        this.consentimentoMarketing = aceito;
        this.consentimentoEm = Objects.requireNonNull(registradoEm, "registradoEm");
    }

    /** Regra de promoção do buffer (§3): só vira lead quem tem Meta, e-mail ou telefone. */
    public boolean possuiIdentificadorDeContato() {
        return temTexto(metaLeadId) || temTexto(email) || temTexto(telefone);
    }

    private static short validarEtapa(int etapa) {
        if (etapa < ETAPA_MINIMA || etapa > ETAPA_MAXIMA) {
            throw new IllegalArgumentException("Etapa do funil deve estar entre 0 e 3: " + etapa);
        }
        return (short) etapa;
    }

    private static String preencher(String atual, String novo) {
        return temTexto(novo) ? novo.strip() : atual;
    }

    private static <T> T preencher(T atual, T novo) {
        return novo != null ? novo : atual;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String gerarTokenDescadastro() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public int getEtapa() {
        return etapa;
    }

    public Temperatura getTemperatura() {
        return temperatura;
    }

    public OrigemLead getOrigem() {
        return origem;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getWhatsapp() {
        return whatsapp;
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

    public String getUtmTerm() {
        return utmTerm;
    }

    public String getUtmContent() {
        return utmContent;
    }

    public String getLandingPageUrl() {
        return landingPageUrl;
    }

    public OffsetDateTime getFirstTouch() {
        return firstTouch;
    }

    public OffsetDateTime getLastTouch() {
        return lastTouch;
    }

    public UUID getVisitanteId() {
        return visitanteId;
    }

    public UUID getEnvioId() {
        return envioId;
    }

    public UUID getFormularioId() {
        return formularioId;
    }

    public Integer getFormularioVersao() {
        return formularioVersao;
    }

    public Map<String, Object> getDadosFormulario() {
        return dadosFormulario;
    }

    public boolean isConsentimentoMarketing() {
        return consentimentoMarketing;
    }

    public OffsetDateTime getConsentimentoEm() {
        return consentimentoEm;
    }

    public OffsetDateTime getBoasVindasEnviadaEm() {
        return boasVindasEnviadaEm;
    }

    public String getMetaLeadId() {
        return metaLeadId;
    }

    public UUID getCrmEmpresaId() {
        return crmEmpresaId;
    }

    public UUID getCrmContatoId() {
        return crmContatoId;
    }

    public UUID getCrmOportunidadeId() {
        return crmOportunidadeId;
    }

    public Integer getPontuacao() {
        return pontuacao;
    }

    public FaixaQualificacao getFaixaQualificacao() {
        return faixaQualificacao;
    }

    public List<Map<String, Object>> getPontuacaoDetalhe() {
        return pontuacaoDetalhe;
    }

    public OffsetDateTime getQualificadoEm() {
        return qualificadoEm;
    }

    public UUID getVendedorId() {
        return vendedorId;
    }

    public OffsetDateTime getVendedorAtribuidoEm() {
        return vendedorAtribuidoEm;
    }

    public OffsetDateTime getUltimaComunicacaoEm() {
        return ultimaComunicacaoEm;
    }

    public int getTentativasAquecimento() {
        return tentativasAquecimento;
    }

    public StatusAquecimento getStatusAquecimento() {
        return statusAquecimento;
    }

    public OffsetDateTime getEsgotadoEm() {
        return esgotadoEm;
    }

    public OffsetDateTime getOptOutEm() {
        return optOutEm;
    }

    public String getTokenDescadastro() {
        return tokenDescadastro;
    }

    public boolean isAnonimizado() {
        return anonimizado;
    }
}
