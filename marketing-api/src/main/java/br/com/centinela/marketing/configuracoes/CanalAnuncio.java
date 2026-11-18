package br.com.centinela.marketing.configuracoes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 * Preferências por canal de anúncio e credenciais cifradas do Meta Ads (D6, norteador §3 e §9.4).
 * Os segredos ({@code meta_app_secret}, {@code meta_verify_token} e {@code meta_page_access_token})
 * só entram por escrita cifrada e nunca saem pela API.
 */
@Entity
@Table(name = "canais_anuncio")
@SQLDelete(sql = "UPDATE marketing.canais_anuncio SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CanalAnuncio extends EntidadeBase {

    @Column(name = "canal", nullable = false, updatable = false, length = 20)
    @Convert(converter = CanalAnuncioTipo.Conversor.class)
    private CanalAnuncioTipo canal;

    @Column(name = "orcamento_mensal", precision = 15, scale = 2)
    private BigDecimal orcamentoMensal;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuracao")
    private Map<String, Object> configuracao;

    @Column(name = "meta_app_secret")
    private String metaAppSecretCifrado;

    @Column(name = "meta_verify_token")
    private String metaVerifyTokenCifrado;

    @Column(name = "meta_page_id", length = 120)
    private String metaPageId;

    @Column(name = "meta_page_access_token")
    private String metaPageAccessTokenCifrado;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected CanalAnuncio() {
    }

    public CanalAnuncio(CanalAnuncioTipo canal) {
        this.canal = Objects.requireNonNull(canal, "canal");
        this.configuracao = new LinkedHashMap<>();
        this.ativo = false;
    }

    public void atualizarPreferencias(BigDecimal orcamentoMensal, String publicoAlvo, String objetivo, boolean ativo) {
        if (orcamentoMensal != null && orcamentoMensal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O orçamento mensal não pode ser negativo.");
        }
        this.orcamentoMensal = orcamentoMensal;
        Map<String, Object> novaConfig = new LinkedHashMap<>();
        if (publicoAlvo != null && !publicoAlvo.isBlank()) {
            novaConfig.put("publicoAlvo", publicoAlvo.strip());
        }
        if (objetivo != null && !objetivo.isBlank()) {
            novaConfig.put("objetivo", objetivo.strip());
        }
        this.configuracao = novaConfig;
        this.ativo = ativo;
    }

    /**
     * Atualiza as credenciais do Meta Ads (só válido para {@code meta_ads}). Segredo em branco ou
     * nulo mantém o que já estava gravado.
     */
    public void atualizarCredenciaisMeta(String metaPageId, String appSecretAberto, String verifyTokenAberto,
            String pageAccessTokenAberto, CifradorDeSegredo cifrador) {
        if (canal != CanalAnuncioTipo.META_ADS) {
            throw new IllegalStateException("Credenciais do Meta só existem no canal meta_ads.");
        }
        Objects.requireNonNull(cifrador, "cifrador");
        if (metaPageId != null) {
            this.metaPageId = metaPageId.isBlank() ? null : metaPageId.strip();
        }
        if (appSecretAberto != null && !appSecretAberto.isBlank()) {
            this.metaAppSecretCifrado = cifrador.cifrar(appSecretAberto);
        }
        if (verifyTokenAberto != null && !verifyTokenAberto.isBlank()) {
            this.metaVerifyTokenCifrado = cifrador.cifrar(verifyTokenAberto);
        }
        if (pageAccessTokenAberto != null && !pageAccessTokenAberto.isBlank()) {
            this.metaPageAccessTokenCifrado = cifrador.cifrar(pageAccessTokenAberto);
        }
    }

    public CanalAnuncioTipo getCanal() {
        return canal;
    }

    public BigDecimal getOrcamentoMensal() {
        return orcamentoMensal;
    }

    public Map<String, Object> getConfiguracao() {
        return configuracao;
    }

    public String getPublicoAlvo() {
        Object valor = configuracao == null ? null : configuracao.get("publicoAlvo");
        return valor instanceof String texto ? texto : null;
    }

    public String getObjetivo() {
        Object valor = configuracao == null ? null : configuracao.get("objetivo");
        return valor instanceof String texto ? texto : null;
    }

    public String getMetaPageId() {
        return metaPageId;
    }

    public boolean possuiMetaAppSecret() {
        return metaAppSecretCifrado != null && !metaAppSecretCifrado.isBlank();
    }

    public boolean possuiMetaVerifyToken() {
        return metaVerifyTokenCifrado != null && !metaVerifyTokenCifrado.isBlank();
    }

    public boolean possuiMetaPageAccessToken() {
        return metaPageAccessTokenCifrado != null && !metaPageAccessTokenCifrado.isBlank();
    }

    public String decifrarMetaAppSecret(CifradorDeSegredo cifrador) {
        return cifrador.decifrar(metaAppSecretCifrado);
    }

    public String decifrarMetaVerifyToken(CifradorDeSegredo cifrador) {
        return cifrador.decifrar(metaVerifyTokenCifrado);
    }

    public String decifrarMetaPageAccessToken(CifradorDeSegredo cifrador) {
        return cifrador.decifrar(metaPageAccessTokenCifrado);
    }

    public boolean isAtivo() {
        return ativo;
    }
}
