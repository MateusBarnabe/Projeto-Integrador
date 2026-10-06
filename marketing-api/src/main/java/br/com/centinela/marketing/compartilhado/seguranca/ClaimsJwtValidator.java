package br.com.centinela.marketing.compartilhado.seguranca;

import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Valida os claims necessários ao módulo antes de autorizar a requisição. */
final class ClaimsJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error CLAIM_INVALIDO = new OAuth2Error(
            "invalid_token", "O token não contém os claims de identidade válidos.", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        if (subject != null && subject.startsWith("svc:")) {
            return OAuth2TokenValidatorResult.success();
        }

        if (uuidValido(token.getClaimAsString("tenant_id")) && uuidValido(subject)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(CLAIM_INVALIDO);
    }

    private static boolean uuidValido(String valor) {
        if (valor == null) {
            return false;
        }
        try {
            UUID.fromString(valor);
            return true;
        } catch (IllegalArgumentException excecao) {
            return false;
        }
    }
}