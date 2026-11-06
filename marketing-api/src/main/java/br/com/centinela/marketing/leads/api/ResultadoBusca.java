package br.com.centinela.marketing.leads.api;

import java.util.UUID;

import br.com.centinela.marketing.leads.Lead;

/**
 * Item da busca global da casca, no formato único do Contrato §8.6 ({@code ResultadoBusca} do
 * {@code contratos/marketing.yaml}). {@code rota} é relativa ao {@code urlFrontend} do módulo.
 */
public record ResultadoBusca(UUID id, String titulo, String subtitulo, String rota) {

    static ResultadoBusca de(Lead lead) {
        String titulo = lead.getNome() != null && !lead.getNome().isBlank() ? lead.getNome() : lead.getEmail();
        String subtitulo = "Etapa " + lead.getEtapa() + " · " + Valores.de(lead.getOrigem());
        return new ResultadoBusca(lead.getId(), titulo == null ? "" : titulo, subtitulo, "/leads/" + lead.getId());
    }
}
