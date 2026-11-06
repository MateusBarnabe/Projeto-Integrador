package br.com.centinela.marketing.entrada;

import java.util.UUID;

/**
 * Identificadores que um fato traz para achar o lead (norteador §5). {@link IdentificacaoDeLeads}
 * tenta cada um na ordem de {@link Chave}, do mais forte para o mais fraco; quem monta só preenche os
 * que o seu fluxo usa:
 *
 * <ul>
 * <li>Meta (E4): {@code metaLeadId}, e-mail, telefone;</li>
 * <li>chatbot (E1): {@code sessaoId}, e-mail, telefone;</li>
 * <li>envio (E5): {@code envioId}, {@code sessaoId} (quando o Landing mandar), {@code crmContatoId} e,
 * com o envio lido na chegada, o e-mail e o telefone dele; os outros leads de mesmo contato entram no
 * merge ({@link IdentificacaoDeLeads#mesmoContato});</li>
 * <li>oportunidade (E6): {@code envioId} ({@code origemModuloId}), {@code crmContatoId}.</li>
 * </ul>
 *
 * E-mail e telefone são normalizados aqui ({@link NormalizacaoDeContato}).
 */
public record ChavesDeIdentidade(
        String metaLeadId,
        UUID envioId,
        UUID sessaoId,
        UUID crmContatoId,
        String email,
        String telefone) {

    /** Ordem em que as chaves são tentadas. */
    public enum Chave {
        META_LEAD, ENVIO, SESSAO, CONTATO_CRM, EMAIL, TELEFONE
    }

    public static final ChavesDeIdentidade NENHUMA = new ChavesDeIdentidade(null, null, null, null, null, null);

    public ChavesDeIdentidade {
        metaLeadId = metaLeadId == null || metaLeadId.isBlank() ? null : metaLeadId.strip();
        email = NormalizacaoDeContato.email(email);
        telefone = NormalizacaoDeContato.telefone(telefone);
    }

    public static ChavesDeIdentidade daSessao(UUID sessaoId) {
        return NENHUMA.comSessao(sessaoId);
    }

    public static ChavesDeIdentidade doEnvio(UUID envioId) {
        return NENHUMA.comEnvio(envioId);
    }

    public static ChavesDeIdentidade doMeta(String metaLeadId) {
        return new ChavesDeIdentidade(metaLeadId, null, null, null, null, null);
    }

    public ChavesDeIdentidade comEnvio(UUID envio) {
        return new ChavesDeIdentidade(metaLeadId, envio, sessaoId, crmContatoId, email, telefone);
    }

    public ChavesDeIdentidade comSessao(UUID sessao) {
        return new ChavesDeIdentidade(metaLeadId, envioId, sessao, crmContatoId, email, telefone);
    }

    public ChavesDeIdentidade comContatoCrm(UUID contato) {
        return new ChavesDeIdentidade(metaLeadId, envioId, sessaoId, contato, email, telefone);
    }

    /** E-mail e telefone em qualquer formato; ficam normalizados. */
    public ChavesDeIdentidade comContato(String emailBruto, String telefoneBruto) {
        return new ChavesDeIdentidade(metaLeadId, envioId, sessaoId, crmContatoId, emailBruto, telefoneBruto);
    }

    /** Tem e-mail ou telefone utilizável: o mínimo para alguém ser lead fora do Meta (§2). */
    public boolean temContato() {
        return email != null || telefone != null;
    }

    @Override
    public String toString() {
        // Sem e-mail e telefone: o registro pode aparecer em log
        return "ChavesDeIdentidade[metaLeadId=" + metaLeadId + ", envioId=" + envioId + ", sessaoId=" + sessaoId
                + ", crmContatoId=" + crmContatoId + ", email=" + (email != null ? "***" : null)
                + ", telefone=" + (telefone != null ? "***" : null) + "]";
    }
}
