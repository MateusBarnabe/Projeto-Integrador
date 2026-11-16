package br.com.centinela.marketing.rastreamento;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import br.com.centinela.marketing.compartilhado.dados.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Destino de um anúncio (R1). A pessoa gestora cola no gerenciador de anúncios a URL pública do link
 * ({@code /public/marketing/c/{codigo}}); o clique registra a visita e segue para a landing page com as
 * UTM do link e o {@code mkt_visita}, que o Landing guarda na página de entrada do envio.
 *
 * <p>O código é aleatório e público; é por ele que a rota pública acha o tenant (norteador §6).
 */
@Entity
@Table(name = "links_campanha")
@SQLDelete(sql = "UPDATE marketing.links_campanha SET deleted_at = now(), updated_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LinkCampanha extends EntidadeBase {

    /** Parâmetro da URL da landing page com o id da visita. */
    public static final String PARAMETRO_VISITA = "mkt_visita";

    static final int TAMANHO_CODIGO = 10;
    private static final String ALFABETO = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @Column(name = "codigo", nullable = false, updatable = false)
    private String codigo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "url_destino", nullable = false)
    private String urlDestino;

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

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    protected LinkCampanha() {
    }

    public LinkCampanha(String nome, String urlDestino, Utm utm) {
        this.codigo = gerarCodigo();
        alterar(nome, urlDestino, utm, true);
    }

    /** UTM que o link acrescenta à URL da landing page (as que já estão na URL de destino ficam). */
    public record Utm(String source, String medium, String campaign, String term, String content) {
    }

    public void alterar(String nome, String urlDestino, Utm utm, boolean ativo) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do link.");
        }
        this.nome = nome.strip();
        this.urlDestino = validarDestino(urlDestino);
        Utm valores = utm == null ? new Utm(null, null, null, null, null) : utm;
        this.utmSource = vazioParaNulo(valores.source());
        this.utmMedium = vazioParaNulo(valores.medium());
        this.utmCampaign = vazioParaNulo(valores.campaign());
        this.utmTerm = vazioParaNulo(valores.term());
        this.utmContent = vazioParaNulo(valores.content());
        this.ativo = ativo;
    }

    /**
     * URL da landing page para um clique: a de destino com as UTM do link (sem repetir as que ela já
     * tem) e, se houver visita, o {@code mkt_visita}.
     */
    public URI destinoPara(UUID visitaId) {
        URI destino = URI.create(urlDestino);
        String consulta = destino.getRawQuery();
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("utm_source", utmSource);
        extras.put("utm_medium", utmMedium);
        extras.put("utm_campaign", utmCampaign);
        extras.put("utm_term", utmTerm);
        extras.put("utm_content", utmContent);
        extras.put(PARAMETRO_VISITA, visitaId == null ? null : visitaId.toString());

        StringBuilder novaConsulta = new StringBuilder(consulta == null ? "" : consulta);
        extras.forEach((parametro, valor) -> {
            if (valor == null || temParametro(consulta, parametro)) {
                return;
            }
            if (!novaConsulta.isEmpty()) {
                novaConsulta.append('&');
            }
            novaConsulta.append(parametro).append('=').append(URLEncoder.encode(valor, StandardCharsets.UTF_8));
        });

        String base = urlDestino.split("[?#]", 2)[0];
        String fragmento = destino.getRawFragment();
        return URI.create(base + (novaConsulta.isEmpty() ? "" : "?" + novaConsulta)
                + (fragmento == null ? "" : "#" + fragmento));
    }

    public Utm utm() {
        return new Utm(utmSource, utmMedium, utmCampaign, utmTerm, utmContent);
    }

    private static boolean temParametro(String consulta, String parametro) {
        if (consulta == null) {
            return false;
        }
        for (String par : consulta.split("&")) {
            if (par.equals(parametro) || par.startsWith(parametro + "=")) {
                return true;
            }
        }
        return false;
    }

    static String validarDestino(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Informe a URL da landing page.");
        }
        String limpa = url.strip();
        URI uri;
        try {
            uri = URI.create(limpa);
        } catch (IllegalArgumentException invalida) {
            throw new IllegalArgumentException("URL da landing page inválida.");
        }
        String esquema = uri.getScheme();
        if (esquema == null || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("A URL da landing page precisa começar com http:// ou https://.");
        }
        if (limpa.length() > 2048) {
            throw new IllegalArgumentException("A URL da landing page aceita até 2048 caracteres.");
        }
        return limpa;
    }

    private static String vazioParaNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.strip();
        return limpo.length() > 255 ? limpo.substring(0, 255) : limpo;
    }

    static String gerarCodigo() {
        StringBuilder codigo = new StringBuilder(TAMANHO_CODIGO);
        for (int i = 0; i < TAMANHO_CODIGO; i++) {
            codigo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return codigo.toString();
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getUrlDestino() {
        return urlDestino;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
