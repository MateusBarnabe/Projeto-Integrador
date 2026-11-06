package br.com.centinela.marketing.qualificacao;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.OrigemLead;

/**
 * O que o motor de pontuação lê de um lead (§9.3). O motor não busca nada fora: as respostas do
 * envio já foram lidas do Landing (E5, job 4.6) e as do formulário instantâneo vêm da Graph API (E4).
 *
 * @param tenantId            tenant do lead; regra de outro tenant nunca vale
 * @param formularioId        formulário do Landing do envio; só as regras de campo dele valem
 * @param respostasFormulario valor de cada resposta do envio, por {@code campoId}
 * @param leadDoMeta          lead que entrou pelo Meta; só ele usa as regras de {@code campo_meta}
 * @param respostasMeta       valor de cada pergunta do formulário instantâneo, pelo nome da pergunta
 * @param utm                 origem do tráfego do lead
 */
public record EntradaPontuacao(
        UUID tenantId,
        UUID formularioId,
        Map<UUID, Object> respostasFormulario,
        boolean leadDoMeta,
        Map<String, Object> respostasMeta,
        Utm utm) {

    public EntradaPontuacao {
        Objects.requireNonNull(tenantId, "tenantId");
        respostasFormulario = imutavel(respostasFormulario);
        respostasMeta = imutavel(respostasMeta);
        utm = utm == null ? Utm.NENHUMA : utm;
    }

    /**
     * Monta a entrada com o que o lead já tem: o formulário e as respostas copiadas do envio
     * ({@code dados_formulario}), a origem e a UTM.
     *
     * @param respostasMeta respostas do formulário instantâneo do Meta; vazio fora do job 4.1
     */
    public static EntradaPontuacao doLead(Lead lead, Map<String, ?> respostasMeta) {
        Objects.requireNonNull(lead, "lead");
        return new EntradaPontuacao(
                lead.getTenantId(),
                lead.getFormularioId(),
                respostasPorCampo(lead.getDadosFormulario()),
                lead.getOrigem() == OrigemLead.META_ADS,
                respostasMeta == null ? Map.of() : new HashMap<>(respostasMeta),
                new Utm(lead.getUtmSource(), lead.getUtmMedium(), lead.getUtmCampaign(), lead.getUtmTerm(),
                        lead.getUtmContent()));
    }

    /** Respostas do envio no formato do Landing ({@code campoId}, {@code rotulo}, {@code tipo}, {@code valor}). */
    private static Map<UUID, Object> respostasPorCampo(List<Map<String, Object>> respostas) {
        Map<UUID, Object> porCampo = new HashMap<>();
        if (respostas == null) {
            return porCampo;
        }
        for (Map<String, Object> resposta : respostas) {
            Object campoId = resposta == null ? null : resposta.get("campoId");
            if (campoId == null) {
                continue;
            }
            try {
                porCampo.put(UUID.fromString(campoId.toString()), resposta.get("valor"));
            } catch (IllegalArgumentException campoIdInvalido) {
                // Resposta sem campo identificável não tem regra que a alcance
            }
        }
        return porCampo;
    }

    // Map.copyOf recusa valor nulo, e uma resposta em branco ou anonimizada chega nula
    private static <K, V> Map<K, V> imutavel(Map<K, ? extends V> mapa) {
        return mapa == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(mapa));
    }

    /** UTM do lead (a da última origem, §3). */
    public record Utm(String source, String medium, String campaign, String term, String content) {

        public static final Utm NENHUMA = new Utm(null, null, null, null, null);

        /** Sem nenhuma UTM: tráfego direto, para a regra {@code {"direto": true}}. */
        public boolean vazia() {
            return vazio(source) && vazio(medium) && vazio(campaign) && vazio(term) && vazio(content);
        }

        /** Valor de uma chave da regra de UTM ({@code source}, {@code medium}...); nulo se a chave não existe. */
        String valor(String chave) {
            return switch (chave) {
                case "source" -> source;
                case "medium" -> medium;
                case "campaign" -> campaign;
                case "term" -> term;
                case "content" -> content;
                default -> null;
            };
        }

        static boolean chaveConhecida(String chave) {
            return List.of("source", "medium", "campaign", "term", "content").contains(chave);
        }

        private static boolean vazio(String valor) {
            return valor == null || valor.isBlank();
        }
    }
}
