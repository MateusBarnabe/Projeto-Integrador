package br.com.centinela.marketing.compartilhado.dados;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

class EntidadeBaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @AfterEach
    void limpar() {
        TenantContexto.limpar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void prePersistPreencheSeteColunasEUsuarioDoToken() {
        TenantContexto.definir(TENANT);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwtDoUsuario()));
        Registro registro = new Registro();

        registro.prepararCriacao();

        assertThat(registro.getId()).isNotNull();
        assertThat(registro.getTenantId()).isEqualTo(TENANT);
        assertThat(registro.getCriadoEm()).isNotNull();
        assertThat(registro.getAtualizadoEm()).isNotNull();
        assertThat(registro.getExcluidoEm()).isNull();
        assertThat(registro.getCriadoPor()).isEqualTo(USUARIO);
        assertThat(registro.getAtualizadoPor()).isEqualTo(USUARIO);
        assertThat(registro.isNew()).isTrue();
    }

    @Test
    void marcarExcluidaPreencheDeletedAtSemApagarRegistro() {
        TenantContexto.definir(TENANT);
        Registro registro = new Registro();
        registro.prepararCriacao();

        registro.marcarExcluida();

        assertThat(registro.getId()).isNotNull();
        assertThat(registro.getExcluidoEm()).isNotNull();
    }

    private static Jwt jwtDoUsuario() {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", USUARIO.toString(), "tenant_id", TENANT.toString()));
    }

    private static final class Registro extends EntidadeBase {
    }
}