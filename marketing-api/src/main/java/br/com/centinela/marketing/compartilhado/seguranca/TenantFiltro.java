package br.com.centinela.marketing.compartilhado.seguranca;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Coloca o tenant do token no contexto da requisição para o Hibernate e o domínio. */
public class TenantFiltro extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
            FilterChain cadeia) throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)
                || token.getToken().getSubject().startsWith("svc:")) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }

        TenantContexto.definir(ContextoSeguranca.exigirUsuarioAtual().tenantId());
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            TenantContexto.limpar();
        }
    }
}