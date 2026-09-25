package br.com.centinela.marketing.compartilhado.seguranca;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.web.Resposta;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rotas públicas sem token (§12.6) e o resto exigindo o JWT do identity, validado localmente
 * pelo JWKS (§4.2). Permissões são conferidas nos controllers com {@code @PreAuthorize} (§5.2).
 */
@Configuration
@EnableMethodSecurity
public class SegurancaConfig {

    private final ObjectMapper objectMapper;

    public SegurancaConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/marketing/health", "/public/marketing/**", "/error").permitAll()
                        // Documentação (desligada com SWAGGER_HABILITADO=false; fora do roteamento do gateway)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> { })
                        .authenticationEntryPoint((req, res, ex) ->
                                escrever(res, HttpStatus.UNAUTHORIZED, "Token ausente, inválido ou expirado.")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                escrever(res, HttpStatus.UNAUTHORIZED, "Token ausente, inválido ou expirado."))
                        .accessDeniedHandler((req, res, ex) ->
                                escrever(res, HttpStatus.FORBIDDEN, "Sem permissão para esta ação.")))
                .build();
    }

    private void escrever(HttpServletResponse res, HttpStatus status, String mensagem) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), Resposta.erro(mensagem));
    }
}
