package br.com.centinela.marketing.configuracoes;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import br.com.centinela.marketing.comunicacao.CicloVidaConfiguracao;
import br.com.centinela.marketing.comunicacao.ModeloMensagem;
import br.com.centinela.marketing.qualificacao.QualificacaoConfiguracao;
import br.com.centinela.marketing.qualificacao.RegraPontuacao;
import br.com.centinela.marketing.qualificacao.VendedorRodizio;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** DTOs das APIs de configuração do módulo (A3, norteador §6 e §9.4). */
public final class ConfiguracoesDtos {

    private ConfiguracoesDtos() {
    }

    // --- Canais de anúncio -----------------------------------------------------------------------

    /**
     * Canal de anúncio devolvido pela API: os segredos do Meta nunca saem, só os indicadores de que
     * já foram configurados e a URL pública do webhook.
     */
    public record ItemCanalAnuncio(
            UUID id,
            String canal,
            BigDecimal orcamentoMensal,
            String publicoAlvo,
            String objetivo,
            boolean ativo,
            String metaPageId,
            boolean metaAppSecretConfigurado,
            boolean metaVerifyTokenConfigurado,
            boolean metaPageAccessTokenConfigurado,
            String webhookUrl,
            OffsetDateTime atualizadoEm) {

        public static ItemCanalAnuncio de(CanalAnuncio entidade, String urlPublica) {
            boolean isMeta = entidade.getCanal() == CanalAnuncioTipo.META_ADS;
            String webhook = isMeta ? urlPublica + "/public/marketing/{subdominio}/meta/leads" : null;
            return new ItemCanalAnuncio(
                    entidade.getId(),
                    entidade.getCanal().valor(),
                    entidade.getOrcamentoMensal(),
                    entidade.getPublicoAlvo(),
                    entidade.getObjetivo(),
                    entidade.isAtivo(),
                    isMeta ? entidade.getMetaPageId() : null,
                    isMeta && entidade.possuiMetaAppSecret(),
                    isMeta && entidade.possuiMetaVerifyToken(),
                    isMeta && entidade.possuiMetaPageAccessToken(),
                    webhook,
                    entidade.getAtualizadoEm());
        }
    }

    public record PedidoCanalAnuncio(
            @NotBlank(message = "Informe o canal (google_ads ou meta_ads).") String canal,
            BigDecimal orcamentoMensal,
            String publicoAlvo,
            String objetivo,
            Boolean ativo,
            String metaPageId,
            String metaAppSecret,
            String metaVerifyToken,
            String metaPageAccessToken) {
    }

    // --- Modelos de mensagem ---------------------------------------------------------------------

    public record ItemModeloMensagem(
            UUID id,
            String tipo,
            String nome,
            String canal,
            Integer etapaAlvo,
            Integer ordemSequencia,
            Integer diasAposAnterior,
            String assunto,
            String corpo,
            boolean ativo,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {

        public static ItemModeloMensagem de(ModeloMensagem modelo) {
            return new ItemModeloMensagem(
                    modelo.getId(),
                    modelo.getTipo().valor(),
                    modelo.getNome(),
                    modelo.getCanal().valor(),
                    modelo.getEtapaAlvo(),
                    modelo.getOrdemSequencia(),
                    modelo.getDiasAposAnterior(),
                    modelo.getAssunto(),
                    modelo.getCorpo(),
                    modelo.isAtivo(),
                    modelo.getCriadoEm(),
                    modelo.getAtualizadoEm());
        }
    }

    public record PedidoModeloMensagem(
            String tipo,
            @NotBlank(message = "Informe o nome do modelo.") String nome,
            Integer etapaAlvo,
            Integer ordemSequencia,
            Integer diasAposAnterior,
            @NotBlank(message = "Informe o assunto do e-mail.") String assunto,
            @NotBlank(message = "Informe o corpo do e-mail.") String corpo,
            Boolean ativo) {
    }

    // --- Ciclo de vida ---------------------------------------------------------------------------

    public record ConfiguracaoCicloVida(
            int maxTentativasAquecimento,
            int mesesRetencaoPosEsgotamento,
            int mesesRetencaoPosOptout,
            OffsetDateTime atualizadoEm) {

        public static ConfiguracaoCicloVida de(CicloVidaConfiguracao entidade) {
            return new ConfiguracaoCicloVida(
                    entidade.getMaxTentativasAquecimento(),
                    entidade.getMesesRetencaoPosEsgotamento(),
                    entidade.getMesesRetencaoPosOptout(),
                    entidade.getAtualizadoEm());
        }
    }

    public record PedidoCicloVida(
            @NotNull(message = "Informe o máximo de tentativas de aquecimento.") Integer maxTentativasAquecimento,
            @NotNull(message = "Informe os meses de retenção após esgotamento.") Integer mesesRetencaoPosEsgotamento,
            @NotNull(message = "Informe os meses de retenção após opt-out.") Integer mesesRetencaoPosOptout) {
    }

    // --- Qualificação e distribuição -------------------------------------------------------------

    public record PreviaFaixas(long mqlQuente, long mqlMorno, long nutricao, long semPontuacao) {
    }

    public record ConfiguracaoQualificacao(
            int limiteMqlQuente,
            int limiteMqlMorno,
            boolean rodizioAtivo,
            String horarioComercialInicio,
            String horarioComercialFim,
            List<Integer> diasAtendimento,
            Integer minutosTransbordo,
            PreviaFaixas previaFaixas) {

        public static ConfiguracaoQualificacao de(QualificacaoConfiguracao entidade, PreviaFaixas previa) {
            List<Integer> dias = entidade.getDiasAtendimento().stream()
                    .map(DayOfWeek::getValue)
                    .sorted()
                    .toList();
            return new ConfiguracaoQualificacao(
                    entidade.getLimiteMqlQuente(),
                    entidade.getLimiteMqlMorno(),
                    entidade.isRodizioAtivo(),
                    formatarHora(entidade.getHorarioComercialInicio()),
                    formatarHora(entidade.getHorarioComercialFim()),
                    dias,
                    entidade.getMinutosTransbordo(),
                    previa);
        }

        private static String formatarHora(LocalTime hora) {
            return String.format("%02d:%02d", hora.getHour(), hora.getMinute());
        }
    }

    public record PedidoQualificacao(
            @NotNull(message = "Informe o limite de MQL quente.") Integer limiteMqlQuente,
            @NotNull(message = "Informe o limite de MQL morno.") Integer limiteMqlMorno,
            @NotNull(message = "Informe se o rodízio está ativo.") Boolean rodizioAtivo,
            @NotBlank(message = "Informe o início do horário comercial (HH:mm).") String horarioComercialInicio,
            @NotBlank(message = "Informe o fim do horário comercial (HH:mm).") String horarioComercialFim,
            @NotEmpty(message = "Informe ao menos um dia de atendimento.") List<Integer> diasAtendimento,
            Integer minutosTransbordo) {
    }

    public record CampoFormularioResumo(
            UUID id,
            String rotulo,
            String tipo,
            boolean obrigatorio,
            List<String> opcoes) {
    }

    public record FormularioQualificacao(
            UUID id,
            String nome,
            String status,
            boolean disponivelNoLanding,
            List<CampoFormularioResumo> campos) {
    }

    public record ItemRegraPontuacao(
            UUID id,
            String criterio,
            String fonte,
            UUID formularioId,
            UUID campoId,
            String campoMeta,
            String campoComportamento,
            List<Object> valores,
            int pontos,
            boolean ativo,
            OffsetDateTime criadoEm,
            OffsetDateTime atualizadoEm) {

        public static ItemRegraPontuacao de(RegraPontuacao regra) {
            return new ItemRegraPontuacao(
                    regra.getId(),
                    regra.getCriterio(),
                    regra.getFonte().valor(),
                    regra.getFormularioId(),
                    regra.getCampoId(),
                    regra.getCampoMeta(),
                    regra.getCampoComportamento(),
                    regra.getValores(),
                    regra.getPontos(),
                    regra.isAtivo(),
                    regra.getCriadoEm(),
                    regra.getAtualizadoEm());
        }
    }

    public record PedidoRegraPontuacao(
            @NotBlank(message = "Informe o critério da regra.") String criterio,
            @NotBlank(message = "Informe a fonte da regra.") String fonte,
            UUID formularioId,
            UUID campoId,
            String campoMeta,
            String campoComportamento,
            @NotEmpty(message = "Informe os valores que disparam a regra.") List<Object> valores,
            @NotNull(message = "Informe os pontos da regra.") Integer pontos,
            Boolean ativo) {
    }

    public record ItemVendedorRodizio(
            UUID id,
            UUID vendedorId,
            boolean ativo,
            OffsetDateTime ultimaAtribuicaoEm) {

        public static ItemVendedorRodizio de(VendedorRodizio vendedor) {
            return new ItemVendedorRodizio(
                    vendedor.getId(),
                    vendedor.getVendedorId(),
                    vendedor.isAtivo(),
                    vendedor.getUltimaAtribuicaoEm());
        }
    }

    public record EntradaVendedorRodizio(
            @NotNull(message = "Informe o UUID do vendedor.") UUID vendedorId,
            Boolean ativo) {
    }

    public record PedidoEscalaVendedores(
            @NotNull(message = "Informe a lista de vendedores.") @Valid List<EntradaVendedorRodizio> vendedores) {
    }
}
