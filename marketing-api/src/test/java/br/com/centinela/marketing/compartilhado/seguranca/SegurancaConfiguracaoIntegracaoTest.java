package br.com.centinela.marketing.compartilhado.seguranca;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

@WebMvcTest(controllers = SegurancaConfiguracaoIntegracaoTest.TestController.class)
@Import({SegurancaConfig.class, SegurancaConfiguracaoIntegracaoTest.TestController.class})
class SegurancaConfiguracaoIntegracaoTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-00000000000a");
    private static final UUID USUARIO = UUID.fromString("10000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtDecoder decoder;

    @Test
    void permissaoDoTokenPassaPeloSecurityFilterChain() throws Exception {
        when(decoder.decode("token")).thenReturn(jwt(List.of("marketing.lead.ver")));

        mvc.perform(get("/api/marketing/f1-test")
                        .header("Authorization", "Bearer token")
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void ausenciaDePermissaoResponde403() throws Exception {
        when(decoder.decode("token")).thenReturn(jwt(List.of("marketing.lead.editar")));

        mvc.perform(get("/api/marketing/f1-test")
                        .header("Authorization", "Bearer token")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtroColocaTenantDoTokenNoContextoDaRequisicao() throws Exception {
        when(decoder.decode("token")).thenReturn(jwt(List.of("marketing.lead.ver")));

        mvc.perform(get("/api/marketing/f2-tenant")
                        .header("Authorization", "Bearer token")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(TENANT.toString()));
    }

    @RestController
    static class TestController {
        @GetMapping("/api/marketing/f1-test")
        @PreAuthorize("hasAuthority('marketing.lead.ver')")
        String testarPermissao() {
            return "ok";
        }

        @GetMapping("/api/marketing/f2-tenant")
        String tenantAtual() {
            return TenantContexto.exigir().toString();
        }
    }

    private static Jwt jwt(List<String> permissoes) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"),
                Map.of("sub", USUARIO.toString(), "tenant_id", TENANT.toString(), "perms", permissoes));
    }
}