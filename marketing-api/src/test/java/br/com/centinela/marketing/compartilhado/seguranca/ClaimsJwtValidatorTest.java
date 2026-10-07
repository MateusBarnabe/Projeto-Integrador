package br.com.centinela.marketing.compartilhado.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class ClaimsJwtValidatorTest {

    private final ClaimsJwtValidator validator = new ClaimsJwtValidator();

    @Test
    void aceitaTokenDeUsuarioComTenantESubUuid() {
        OAuth2TokenValidatorResult resultado = validator.validate(jwt(Map.of(
                "sub", "10000000-0000-4000-8000-000000000001",
                "tenant_id", "a0000000-0000-4000-8000-00000000000a")));

        assertThat(resultado.hasErrors()).isFalse();
    }

    @Test
    void rejeitaTokenDeUsuarioSemTenantOuComSubInvalido() {
        OAuth2TokenValidatorResult semTenant = validator.validate(jwt(Map.of(
                "sub", "10000000-0000-4000-8000-000000000001")));
        OAuth2TokenValidatorResult subInvalido = validator.validate(jwt(Map.of(
                "sub", "marketing",
                "tenant_id", "a0000000-0000-4000-8000-00000000000a")));

        assertThat(semTenant.hasErrors()).isTrue();
        assertThat(subInvalido.hasErrors()).isTrue();
    }

    @Test
    void aceitaTokenDeServicoParaValidacaoPosteriorDoTenant() {
        OAuth2TokenValidatorResult resultado = validator.validate(jwt(Map.of("sub", "svc:landing")));

        assertThat(resultado.hasErrors()).isFalse();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
