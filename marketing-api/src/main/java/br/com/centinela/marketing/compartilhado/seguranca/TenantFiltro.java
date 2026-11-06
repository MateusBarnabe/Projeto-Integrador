package br.com.centinela.marketing.compartilhado.seguranca;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Coloca o tenant da requisição no contexto para o Hibernate e o domínio (Contrato §6).
 *
 * <p>Token de usuário: claim {@code tenant_id}. Token de serviço ({@code sub = svc:...}, sem
 * {@code tenant_id}): cabeçalho {@code X-Tenant-Id}, lido só nesse caso (§9.2). Um {@code tenantId}
 * no corpo ou na query nunca é usado, e o cabeçalho é ignorado em token de usuário.
 */
public class TenantFiltro extends OncePerRequestFilter {

    public static final String CABECALHO_TENANT = "X-Tenant-Id";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
            FilterChain cadeia) throws ServletException, IOException {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            cadeia.doFilter(requisicao, resposta);
            return;
        }

        UUID tenant;
        String subject = token.getToken().getSubject();
        if (subject != null && subject.startsWith("svc:")) {
            tenant = uuidOuNulo(requisicao.getHeader(CABECALHO_TENANT));
            if (tenant == null) {
                resposta.setStatus(HttpStatus.BAD_REQUEST.value());
                resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
                resposta.setCharacterEncoding("UTF-8");
                JSON.writeValue(resposta.getOutputStream(), Resposta.erro(
                        "Token de serviço exige o cabeçalho X-Tenant-Id com um UUID válido."));
                return;
            }
        } else {
            tenant = ContextoSeguranca.exigirUsuarioAtual().tenantId();
        }

        TenantContexto.definir(tenant);
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            TenantContexto.limpar();
        }
    }

    private static UUID uuidOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException excecao) {
            return null;
        }
    }
}
