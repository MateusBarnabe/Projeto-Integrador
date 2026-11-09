package br.com.centinela.marketing.rastreamento.api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import br.com.centinela.marketing.rastreamento.LinkCampanha;
import br.com.centinela.marketing.rastreamento.RastreamentoConfiguracao;
import br.com.centinela.marketing.rastreamento.VisitaAnuncio;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Formatos da API do rastreamento (R1). Nada aqui tem dado pessoal: a visita não tem contato. */
public final class RastreamentoDtos {

    private RastreamentoDtos() {
    }

    /**
     * {@code GET /api/marketing/rastreamento}.
     *
     * @param urlScript tag a pedir para o Landing pôr na landing page
     */
    public record Configuracao(boolean ativo, int prazoConversaoMinutos, String urlScript) {

        static Configuracao de(RastreamentoConfiguracao configuracao, String urlPublica) {
            return new Configuracao(configuracao.isAtivo(), configuracao.getPrazoConversaoMinutos(),
                    urlPublica + "/public/marketing/rastreamento.js");
        }
    }

    /** {@code PUT /api/marketing/rastreamento}. */
    public record AlteracaoConfiguracao(
            @NotNull(message = "Informe se o rastreamento fica ligado.") Boolean ativo,
            @NotNull(message = "Informe o prazo.")
            @Min(value = RastreamentoConfiguracao.PRAZO_MINIMO_MINUTOS, message = "O prazo mínimo é 1 minuto.")
            @Max(value = RastreamentoConfiguracao.PRAZO_MAXIMO_MINUTOS, message = "O prazo máximo é 7 dias.")
            Integer prazoConversaoMinutos) {
    }

    /** {@code POST} e {@code PUT /api/marketing/links-campanha}. */
    public record PedidoLink(
            @NotBlank(message = "Informe o nome do link.") @Size(max = 120, message = "Até 120 caracteres.")
            String nome,
            @NotBlank(message = "Informe a URL da landing page.") @Size(max = 2048, message = "Até 2048 caracteres.")
            String urlDestino,
            @Size(max = 255) String utmSource,
            @Size(max = 255) String utmMedium,
            @Size(max = 255) String utmCampaign,
            @Size(max = 255) String utmTerm,
            @Size(max = 255) String utmContent,
            Boolean ativo) {

        LinkCampanha.Utm utm() {
            return new LinkCampanha.Utm(utmSource, utmMedium, utmCampaign, utmTerm, utmContent);
        }
    }

    /**
     * Link de campanha.
     *
     * @param urlPublica o que vai no anúncio
     * @param visitas    cliques registrados
     */
    public record ItemLink(UUID id, String codigo, String nome, String urlDestino, String utmSource, String utmMedium,
            String utmCampaign, String utmTerm, String utmContent, boolean ativo, String urlPublica, long visitas,
            OffsetDateTime criadoEm) {

        static ItemLink de(LinkCampanha link, String urlPublica, long visitas) {
            LinkCampanha.Utm utm = link.utm();
            return new ItemLink(link.getId(), link.getCodigo(), link.getNome(), link.getUrlDestino(), utm.source(),
                    utm.medium(), utm.campaign(), utm.term(), utm.content(), link.isAtivo(),
                    urlPublica + "/public/marketing/c/" + link.getCodigo(), visitas, link.getCriadoEm());
        }
    }

    /** Visita de anúncio: o "lead 0" e o que aconteceu com ele. */
    public record ItemVisita(UUID id, UUID linkId, String linkNome, String situacao, OffsetDateTime clicadoEm,
            OffsetDateTime ultimoSinalEm, OffsetDateTime expiraEm, String utmSource, String utmMedium,
            String utmCampaign, Map<String, Object> contextoTecnico, Map<String, Object> comportamento, UUID leadId,
            OffsetDateTime convertidaEm, OffsetDateTime descartadaEm) {

        static ItemVisita de(VisitaAnuncio visita, String linkNome) {
            return new ItemVisita(visita.getId(), visita.getLinkId(), linkNome, visita.getSituacao().valor(),
                    visita.getClicadoEm(), visita.getUltimoSinalEm(), visita.getExpiraEm(), visita.getUtmSource(),
                    visita.getUtmMedium(), visita.getUtmCampaign(), visita.getContextoTecnico(),
                    visita.getComportamento(), visita.getLeadId(), visita.getConvertidaEm(),
                    visita.getDescartadaEm());
        }
    }

    /**
     * Visitas das últimas 24 horas, por situação.
     *
     * @param taxaConversao convertidas sobre o total, em pontos percentuais ({@code 12.5} = 12,5%)
     */
    public record ResumoVisitas(long ativas, long convertidas, long descartadas, long total, double taxaConversao) {
    }
}
