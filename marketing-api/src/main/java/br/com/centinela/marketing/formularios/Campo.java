package br.com.centinela.marketing.formularios;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Item de {@code formulario_versoes.campos}, no formato do schema {@code Campo} da §9.5.
 *
 * @param id          id do campo; é o {@code campoId} das respostas do Landing
 * @param chave       estável entre versões e formulários, usada nas regras de pontuação
 * @param fixo        {@code true} em nome, e-mail, telefone e consentimento, travados na tela
 * @param opcoes      obrigatório só quando o tipo é lista, radio ou checkbox
 */
public record Campo(
        UUID id,
        String chave,
        String label,
        TipoCampo tipo,
        boolean obrigatorio,
        boolean fixo,
        int ordem,
        List<String> opcoes) {

    public Campo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tipo, "tipo");
        if (chave == null || chave.isBlank()) {
            throw new IllegalArgumentException("Campo sem chave.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Campo " + chave + " sem rótulo.");
        }
        opcoes = opcoes == null ? List.of() : List.copyOf(opcoes);
        if (tipo.exigeOpcoes() && opcoes.isEmpty()) {
            throw new IllegalArgumentException("Campo " + chave + " do tipo " + tipo.valor() + " precisa de opções.");
        }
    }
}
