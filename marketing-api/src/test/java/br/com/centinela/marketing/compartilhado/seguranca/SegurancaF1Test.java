package br.com.centinela.marketing.compartilhado.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class SegurancaF1Test {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-00000000000a");
    private static final UUID USUARIO = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
        void permissoesDoClaimViraramAuthoritiesSemPrefixo() {
        JwtAuthenticationToken autenticacao = new JwtAuthenticationToken(jwtComClaims(
                List.of("marketing.lead.ver", "marketing.lead.editar")));

        var convertido = SegurancaConfig.conversorDePermissoes().convert(autenticacao.getToken());

        assertThat(convertido.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("marketing.lead.ver", "marketing.lead.editar");
    }

    @Test
    void contextoLeTenantESubDoToken() {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwtComClaims(List.of("marketing.lead.ver"))));

        assertThat(ContextoSeguranca.exigirUsuarioAtual())
                .isEqualTo(new UsuarioAtual(TENANT, USUARIO));
    }

    @Test
    void contextoRejeitaTokenSemTenantValido() {
        Jwt jwt = jwtComClaims(Map.of(
                "sub", USUARIO.toString(),
                "perms", List.of("marketing.lead.ver")));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(ContextoSeguranca::exigirUsuarioAtual)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id");
    }

        private static Jwt jwtComClaims(List<String> permissoes) {
                return jwtComClaims(Map.of(
                                "sub", USUARIO.toString(),
                                "tenant_id", TENANT.toString(),
                                "perms", permissoes));
        }

        private static Jwt jwtComClaims(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                                Map.of("alg", "none"), claims);
    }
}