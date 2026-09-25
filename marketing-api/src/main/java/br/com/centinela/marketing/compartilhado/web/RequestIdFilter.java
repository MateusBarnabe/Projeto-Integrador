package br.com.centinela.marketing.compartilhado.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propaga o {@code X-Request-Id} para os logs e para a resposta (Contrato §3).
 * O mesmo valor vira o {@code correlacaoId} dos eventos publicados.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String CHAVE_MDC = "requestId";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(CABECALHO);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(CHAVE_MDC, requestId);
        response.setHeader(CABECALHO, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC);
        }
    }
}
