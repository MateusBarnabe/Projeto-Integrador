package br.com.centinela.marketing.rastreamento;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
 * Um clique no anúncio, o "lead 0" (R1): alguém que chegou à landing page por um link de campanha e
 * ainda não deixou contato. Não é lead (norteador §2): não tem contato, não entra no funil nem no
 * rodízio. Guarda o contexto técnico do aparelho (do clique) e o comportamento na página (do script).
 *
 * <p>Se o envio do formulário trouxer o {@code mkt_visita} na página de entrada, a visita é ligada ao
 * lead e passa a {@code convertida}. Se passar de {@code expira_em} sem converter, o job 4.7 a descarta:
 * zera o contexto técnico e o comportamento e mantém só a linha, para as contagens.
 */
@Entity
@Table(name = "visitas_anuncio")
@SQLDelete(sql = "UPDATE marketing.visitas_anuncio SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class VisitaAnuncio extends EntidadeBase {

    @Column(name = "link_id", nullable = false, updatable = false)
    private UUID linkId;

    @Column(name = "situacao", nullable = false)
    @Convert(converter = SituacaoVisita.Conversor.class)
    private SituacaoVisita situacao = SituacaoVisita.ATIVA;

    @Column(name = "clicado_em", nullable = false, updatable = false)
    private OffsetDateTime clicadoEm;

    @Column(name = "ultimo_sinal_em")
    private OffsetDateTime ultimoSinalEm;

    @Column(name = "expira_em", nullable = false)
    private OffsetDateTime expiraEm;

    @Column(name = "utm_source", updatable = false)
    private String utmSource;

    @Column(name = "utm_medium", updatable = false)
    private String utmMedium;

    @Column(name = "utm_campaign", updatable = false)
    private String utmCampaign;

    @Column(name = "utm_term", updatable = false)
    private String utmTerm;

    @Column(name = "utm_content", updatable = false)
    private String utmContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contexto_tecnico")
    private Map<String, Object> contextoTecnico;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comportamento")
    private Map<String, Object> comportamento;

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "convertida_em")
    private OffsetDateTime convertidaEm;

    @Column(name = "descartada_em")
    private OffsetDateTime descartadaEm;

    protected VisitaAnuncio() {
    }

    public VisitaAnuncio(LinkCampanha link, Map<String, Object> contextoTecnico, OffsetDateTime clicadoEm,
            Duration prazo) {
        Objects.requireNonNull(link, "link");
        this.linkId = Objects.requireNonNull(link.getId(), "link sem id");
        this.clicadoEm = Objects.requireNonNull(clicadoEm, "clicadoEm");
        this.expiraEm = clicadoEm.plus(prazo);
        LinkCampanha.Utm utm = link.utm();
        this.utmSource = utm.source();
        this.utmMedium = utm.medium();
        this.utmCampaign = utm.campaign();
        this.utmTerm = utm.term();
        this.utmContent = utm.content();
        this.contextoTecnico = contextoTecnico == null ? new LinkedHashMap<>() : new LinkedHashMap<>(contextoTecnico);
        this.comportamento = new LinkedHashMap<>();
    }

    public boolean ativa() {
        return situacao == SituacaoVisita.ATIVA;
    }

    /**
     * Junta os sinais do script aos que a visita já tem e adia o descarte: o prazo conta do último
     * sinal. Visita que não está ativa ignora.
     *
     * @return {@code true} se a visita mudou
     */
    public boolean registrarSinais(SinaisDoVisitante recebidos, OffsetDateTime agora, Duration prazo) {
        if (!ativa() || recebidos == null) {
            return false;
        }
        SinaisDoVisitante sinais = recebidos.limpo();
        Map<String, Object> novo = new LinkedHashMap<>(comportamento == null ? Map.of() : comportamento);
        novo.put("tempoNaPaginaSegundos", maior(novo.get("tempoNaPaginaSegundos"), sinais.tempoNaPaginaSegundos()));
        novo.put("profundidadeRolagem", maior(novo.get("profundidadeRolagem"), sinais.profundidadeRolagem()));
        novo.put("elementosClicados", juntar(novo.get("elementosClicados"), sinais.elementosClicados()));
        novo.put("paginasVisitadas", juntar(novo.get("paginasVisitadas"), sinais.paginasVisitadas()));
        comportamento = novo;

        Map<String, Object> contexto = new LinkedHashMap<>(contextoTecnico == null ? Map.of() : contextoTecnico);
        contexto.putIfAbsent("idioma", sinais.idioma());
        contexto.putIfAbsent("fuso", sinais.fuso());
        contexto.putIfAbsent("tela", sinais.tela());
        contexto.values().removeIf(Objects::isNull);
        contextoTecnico = contexto;

        ultimoSinalEm = agora;
        OffsetDateTime novoPrazo = agora.plus(prazo);
        if (novoPrazo.isAfter(expiraEm)) {
            expiraEm = novoPrazo;
        }
        return true;
    }

    /** O envio do visitante virou o lead informado. */
    public void converter(UUID leadId, OffsetDateTime agora) {
        if (!ativa()) {
            throw new IllegalStateException("Só uma visita ativa pode ser convertida.");
        }
        this.leadId = Objects.requireNonNull(leadId, "leadId");
        this.situacao = SituacaoVisita.CONVERTIDA;
        this.convertidaEm = Objects.requireNonNull(agora, "agora");
    }

    private static Integer maior(Object atual, Integer novo) {
        Integer anterior = atual instanceof Number numero ? numero.intValue() : null;
        if (anterior == null) {
            return novo;
        }
        return novo == null ? anterior : Math.max(anterior, novo);
    }

    private static List<String> juntar(Object atual, List<String> novos) {
        Set<String> todos = new LinkedHashSet<>();
        if (atual instanceof List<?> lista) {
            lista.stream().filter(Objects::nonNull).map(Object::toString).forEach(todos::add);
        }
        for (String novo : novos) {
            if (todos.size() >= SinaisDoVisitante.ITENS_MAXIMOS) {
                break;
            }
            todos.add(novo);
        }
        return new ArrayList<>(todos);
    }

    public UUID getLinkId() {
        return linkId;
    }

    public SituacaoVisita getSituacao() {
        return situacao;
    }

    public OffsetDateTime getClicadoEm() {
        return clicadoEm;
    }

    public OffsetDateTime getUltimoSinalEm() {
        return ultimoSinalEm;
    }

    public OffsetDateTime getExpiraEm() {
        return expiraEm;
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

    public Map<String, Object> getContextoTecnico() {
        return contextoTecnico;
    }

    public Map<String, Object> getComportamento() {
        return comportamento;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public OffsetDateTime getConvertidaEm() {
        return convertidaEm;
    }

    public OffsetDateTime getDescartadaEm() {
        return descartadaEm;
    }
}
