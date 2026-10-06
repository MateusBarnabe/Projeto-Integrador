package br.com.centinela.marketing.compartilhado.seguranca;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Ponto único para obter a identidade autenticada usada pelo domínio. */
public final class ContextoSeguranca {

    private ContextoSeguranca() {
    }

    public static UsuarioAtual exigirUsuarioAtual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            throw new IllegalStateException("Não há usuário autenticado no contexto.");
        }

        return new UsuarioAtual(
                uuidDoClaim(token, "tenant_id"),
                uuidDoSubject(token));
    }

    private static UUID uuidDoClaim(JwtAuthenticationToken token, String nome) {
        String valor = token.getToken().getClaimAsString(nome);
        return uuid(valor, "O token não contém " + nome + " válido.");
    }

    private static UUID uuidDoSubject(JwtAuthenticationToken token) {
        return uuid(token.getToken().getSubject(), "O token não contém sub válido.");
    }

    private static UUID uuid(String valor, String mensagem) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException | NullPointerException excecao) {
            throw new IllegalStateException(mensagem, excecao);
        }
    }
}