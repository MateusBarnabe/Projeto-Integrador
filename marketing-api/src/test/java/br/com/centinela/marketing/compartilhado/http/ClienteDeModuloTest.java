package br.com.centinela.marketing.compartilhado.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo.Motivo;
import br.com.centinela.marketing.compartilhado.http.ServidorDeTeste.Resposta;
import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.RequestIdFilter;

@ExtendWith(OutputCaptureExtension.class)
class ClienteDeModuloTest {

    private static final String SEGREDO = "segredo-super-secreto-do-marketing";
    private static final String TOKEN_SERVICO = "POST " + TokenDeServico.CAMINHO;
    private static final UUID TENANT = UUID.fromString("3a7e91b0-0000-4000-8000-000000000001");
    private static final String ENVIO = "GET /api/landing/envios/8d1c0f8e-0000-4000-8000-000000000002";

    record Envio(UUID id, String canal, List<String> respostas) {
    }

    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final AtomicInteger emitidos = new AtomicInteger();

    private ServidorDeTeste servidor;
    private ClientesDeModulos clientes;

    @BeforeEach
    void subir() throws IOException {
        servidor = new ServidorDeTeste();
        servidor.responder(TOKEN_SERVICO, p -> Resposta.json(200, envelope(
                "{\"accessToken\":\"token-servico-" + emitidos.incrementAndGet() + "\",\"expiresIn\":900}")));
        clientes = novosClientes(SEGREDO, Duration.ofSeconds(3), servidor.url());
    }

    @AfterEach
    void derrubar() {
        clientes.close();
        servidor.close();
        MDC.clear();
    }

    @Test
    void enviaTokenDeServicoTenantERequestIdEDesembrulhaOEnvelope() {
        servidor.responder(ENVIO, 200, envelope(
                "{\"id\":\"8d1c0f8e-0000-4000-8000-000000000002\",\"canal\":\"FORMULARIO\",\"respostas\":[\"a\"],\"extra\":1}"));
        MDC.put(RequestIdFilter.CHAVE_MDC, "req-123");

        Envio envio = clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class,
                "8d1c0f8e-0000-4000-8000-000000000002");

        assertThat(envio.canal()).isEqualTo("FORMULARIO");
        assertThat(envio.respostas()).containsExactly("a");

        ServidorDeTeste.Pedido pedido = servidor.pedidos(ENVIO).get(0);
        assertThat(pedido.cabecalho("Authorization")).isEqualTo("Bearer token-servico-1");
        assertThat(pedido.cabecalho("X-Tenant-Id")).isEqualTo(TENANT.toString());
        assertThat(pedido.cabecalho("X-Request-Id")).isEqualTo("req-123");
        assertThat(pedido.cabecalho("Accept")).contains("application/json");

        ServidorDeTeste.Pedido credencial = servidor.pedidos(TOKEN_SERVICO).get(0);
        assertThat(credencial.cabecalho("Authorization")).isNull();
        assertThat(credencial.corpo()).contains("\"clientId\":\"marketing\"").contains(SEGREDO);
    }

    @Test
    void aceitaTipoGenerico() {
        servidor.responder("GET /api/crm/empresas/resumo?ids=a,b", 200, envelope("[{\"id\":\"a\"},{\"id\":\"b\"}]"));

        List<Map<String, String>> itens = clientes.crm().get(TENANT, "/api/crm/empresas/resumo?ids={ids}",
                new ParameterizedTypeReference<>() { }, "a,b");

        assertThat(itens).extracting(i -> i.get("id")).containsExactly("a", "b");
    }

    @Test
    void reaproveitaOTokenEmCacheERenovaPertoDeVencer() {
        servidor.responder(ENVIO, 200, envelope("{}"));

        clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "8d1c0f8e-0000-4000-8000-000000000002");
        relogio.avancar(Duration.ofSeconds(839));
        clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "8d1c0f8e-0000-4000-8000-000000000002");
        assertThat(emitidos).hasValue(1);

        // 900 s de validade, renovado 60 s antes
        relogio.avancar(Duration.ofSeconds(2));
        clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "8d1c0f8e-0000-4000-8000-000000000002");
        assertThat(emitidos).hasValue(2);
        assertThat(servidor.pedidos(ENVIO).get(2).cabecalho("Authorization")).isEqualTo("Bearer token-servico-2");
    }

    @Test
    void com401DoDestinoRenovaOTokenERepeteUmaVez() {
        servidor.responder(ENVIO, p -> "Bearer token-servico-1".equals(p.cabecalho("Authorization"))
                ? Resposta.json(401, "{\"success\":false,\"data\":null,\"message\":\"Token inválido.\",\"errors\":[]}")
                : Resposta.json(200, envelope("{\"canal\":\"CHATBOT\"}")));

        Envio envio = clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class,
                "8d1c0f8e-0000-4000-8000-000000000002");

        assertThat(envio.canal()).isEqualTo("CHATBOT");
        assertThat(emitidos).hasValue(2);
        assertThat(servidor.pedidos(ENVIO)).hasSize(2);
    }

    @Test
    void com401RepetidoDesisteComNaoAutenticado() {
        servidor.responder(ENVIO, 401, "{\"success\":false,\"data\":null,\"message\":\"Token inválido.\",\"errors\":[]}");

        FalhaNaChamadaDeModulo falha = falhaAoLerEnvio();

        assertThat(falha.motivo()).isEqualTo(Motivo.NAO_AUTENTICADO);
        assertThat(falha.modulo()).isEqualTo(ModuloDestino.LANDING);
        assertThat(servidor.pedidos(ENVIO)).hasSize(2);
    }

    @Test
    void classificaAsRespostasDeErroComOEnvelopeDoOutroModulo() {
        servidor.responder(ENVIO, 404, "{\"success\":false,\"data\":null,\"message\":\"Envio não encontrado.\",\"errors\":[]}");
        FalhaNaChamadaDeModulo naoEncontrado = falhaAoLerEnvio();
        assertThat(naoEncontrado.motivo()).isEqualTo(Motivo.NAO_ENCONTRADO);
        assertThat(naoEncontrado.naoEncontrado()).isTrue();
        assertThat(naoEncontrado.status()).isEqualTo(404);
        assertThat(naoEncontrado.mensagemDoModulo()).isEqualTo("Envio não encontrado.");

        servidor.responder(ENVIO, 403, "{\"success\":false,\"data\":null,\"message\":\"Sem permissão.\",\"errors\":[]}");
        assertThat(falhaAoLerEnvio().motivo()).isEqualTo(Motivo.SEM_PERMISSAO);

        servidor.responder(ENVIO, 422, "{\"success\":false,\"data\":null,\"message\":\"Regra.\",\"errors\":"
                + "[{\"campo\":\"email\",\"codigo\":\"EMAIL_INVALIDO\",\"detalhe\":\"Formato inválido.\"}]}");
        FalhaNaChamadaDeModulo rejeitada = falhaAoLerEnvio();
        assertThat(rejeitada.motivo()).isEqualTo(Motivo.REJEITADA);
        assertThat(rejeitada.erros()).containsExactly(new ErroCampo("email", "EMAIL_INVALIDO", "Formato inválido."));

        servidor.responder(ENVIO, 503, "<html>Bad gateway</html>");
        FalhaNaChamadaDeModulo foraDoAr = falhaAoLerEnvio();
        assertThat(foraDoAr.motivo()).isEqualTo(Motivo.INDISPONIVEL);
        assertThat(foraDoAr.indisponivel()).isTrue();
        assertThat(foraDoAr.mensagemDoModulo()).isNull();
    }

    @Test
    void respostaForaDoEnvelopeEhInvalida() {
        servidor.responder(ENVIO, 200, "{\"canal\":\"FORMULARIO\"}");
        assertThat(falhaAoLerEnvio().motivo()).isEqualTo(Motivo.RESPOSTA_INVALIDA);

        servidor.responder(ENVIO, 200, "não é json");
        assertThat(falhaAoLerEnvio().motivo()).isEqualTo(Motivo.RESPOSTA_INVALIDA);

        servidor.responder(ENVIO, 200, "{\"success\":false,\"data\":null,\"message\":\"Falhou.\",\"errors\":[]}");
        FalhaNaChamadaDeModulo semSucesso = falhaAoLerEnvio();
        assertThat(semSucesso.motivo()).isEqualTo(Motivo.RESPOSTA_INVALIDA);
        assertThat(semSucesso.mensagemDoModulo()).isEqualTo("Falhou.");

        servidor.responder(ENVIO, 200, envelope("{\"respostas\":\"não é lista\"}"));
        assertThat(falhaAoLerEnvio().motivo()).isEqualTo(Motivo.RESPOSTA_INVALIDA);
    }

    @Test
    void timeoutViraIndisponivel() {
        clientes.close();
        clientes = novosClientes(SEGREDO, Duration.ofMillis(300), servidor.url());
        servidor.responder(ENVIO, p -> new Resposta(200, envelope("{}"), 2_000));

        long inicio = System.nanoTime();
        FalhaNaChamadaDeModulo falha = falhaAoLerEnvio();

        assertThat(falha.motivo()).isEqualTo(Motivo.INDISPONIVEL);
        assertThat(falha.status()).isNull();
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(1_500));
    }

    @Test
    void moduloForaDoArViraIndisponivel() throws IOException {
        int portaFechada;
        try (ServerSocket socket = new ServerSocket(0)) {
            portaFechada = socket.getLocalPort();
        }
        IntegracaoPropriedades propriedades = new IntegracaoPropriedades(Duration.ofSeconds(1), servidor.url(),
                "http://127.0.0.1:" + portaFechada, servidor.url(), new IntegracaoPropriedades.Servico(null, SEGREDO));
        try (ClientesDeModulos outros = new ClientesDeModulos(propriedades, json, relogio)) {
            FalhaNaChamadaDeModulo falha = catchThrowableOfType(FalhaNaChamadaDeModulo.class,
                    () -> outros.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "x"));
            assertThat(falha.motivo()).isEqualTo(Motivo.INDISPONIVEL);
            assertThat(falha.modulo()).isEqualTo(ModuloDestino.LANDING);
        }
    }

    @Test
    void identityForaDoArFalhaComoIdentityIndisponivel() throws IOException {
        int portaFechada;
        try (ServerSocket socket = new ServerSocket(0)) {
            portaFechada = socket.getLocalPort();
        }
        IntegracaoPropriedades propriedades = new IntegracaoPropriedades(Duration.ofSeconds(1),
                "http://127.0.0.1:" + portaFechada, servidor.url(), servidor.url(),
                new IntegracaoPropriedades.Servico(null, SEGREDO));
        try (ClientesDeModulos outros = new ClientesDeModulos(propriedades, json, relogio)) {
            FalhaNaChamadaDeModulo falha = catchThrowableOfType(FalhaNaChamadaDeModulo.class,
                    () -> outros.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "x"));
            assertThat(falha.motivo()).isEqualTo(Motivo.INDISPONIVEL);
            assertThat(falha.modulo()).isEqualTo(ModuloDestino.IDENTITY);
        }
    }

    @Test
    void semTenantNaoEnviaXTenantId() {
        servidor.responder("GET /api/identity/tenants/resolver?subdominio=empresa-a", 200,
                envelope("{\"id\":\"3a7e91b0-0000-4000-8000-000000000001\"}"));

        Map<?, ?> tenant = clientes.identity().get(null, "/api/identity/tenants/resolver?subdominio={s}", Map.class,
                "empresa-a");

        assertThat(tenant.get("id")).isEqualTo(TENANT.toString());
        ServidorDeTeste.Pedido pedido = servidor.pedidos("GET /api/identity/tenants/resolver?subdominio=empresa-a").get(0);
        assertThat(pedido.cabecalho("X-Tenant-Id")).isNull();
        assertThat(pedido.cabecalho("Authorization")).isEqualTo("Bearer token-servico-1");
    }

    @Test
    void postEnviaCorpoEmJsonEAceitaRespostaSemData() {
        servidor.responder("PUT /api/crm/oportunidades/o-1/responsavel", 200, envelope("null"));
        servidor.responder("POST /api/crm/coisas", 204, null);

        Object semData = clientes.crm().put(TENANT, "/api/crm/oportunidades/{id}/responsavel",
                Map.of("responsavelId", "u-1"), Void.class, "o-1");
        Object semCorpo = clientes.crm().post(TENANT, "/api/crm/coisas", Map.of("a", 1), Map.class);

        assertThat(semData).isNull();
        assertThat(semCorpo).isNull();
        ServidorDeTeste.Pedido pedido = servidor.pedidos("PUT /api/crm/oportunidades/o-1/responsavel").get(0);
        assertThat(pedido.cabecalho("Content-Type")).startsWith("application/json");
        assertThat(pedido.corpo()).isEqualTo("{\"responsavelId\":\"u-1\"}");
    }

    @Test
    void semSegredoFalhaSemChamarNinguem() {
        clientes.close();
        clientes = novosClientes("", Duration.ofSeconds(3), servidor.url());

        FalhaNaChamadaDeModulo falha = falhaAoLerEnvio();

        assertThat(falha.motivo()).isEqualTo(Motivo.NAO_CONFIGURADO);
        assertThat(falha.modulo()).isEqualTo(ModuloDestino.IDENTITY);
        assertThat(servidor.pedidos(TOKEN_SERVICO)).isEmpty();
        assertThat(servidor.pedidos(ENVIO)).isEmpty();
    }

    @Test
    void credencialRecusadaPeloIdentityFalhaComoNaoAutenticado() {
        servidor.responder(TOKEN_SERVICO, 401,
                "{\"success\":false,\"data\":null,\"message\":\"Credencial inválida.\",\"errors\":[]}");

        FalhaNaChamadaDeModulo falha = falhaAoLerEnvio();

        assertThat(falha.motivo()).isEqualTo(Motivo.NAO_AUTENTICADO);
        assertThat(falha.modulo()).isEqualTo(ModuloDestino.IDENTITY);
        assertThat(servidor.pedidos(ENVIO)).isEmpty();
    }

    @Test
    void tokenESegredoNuncaAparecemEmLogNemNaMensagem(CapturedOutput saida) {
        servidor.responder(ENVIO, 401, "{\"success\":false,\"data\":null,\"message\":\"Token inválido.\",\"errors\":[]}");

        FalhaNaChamadaDeModulo falha = falhaAoLerEnvio();

        assertThat(saida.getAll()).contains("landing").doesNotContain(SEGREDO).doesNotContain("token-servico-");
        assertThat(falha.getMessage()).doesNotContain(SEGREDO).doesNotContain("token-servico-")
                .doesNotContain("Token inválido.");
        assertThat(new IntegracaoPropriedades.Servico(null, SEGREDO).toString()).doesNotContain(SEGREDO);
    }

    @Test
    void enderecoNaoConfiguradoFalhaComoNaoConfigurado() {
        IntegracaoPropriedades propriedades = new IntegracaoPropriedades(null, servidor.url(), " ", servidor.url(),
                new IntegracaoPropriedades.Servico(null, SEGREDO));
        try (ClientesDeModulos outros = new ClientesDeModulos(propriedades, json, relogio)) {
            assertThatThrownBy(() -> outros.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class, "x"))
                    .isInstanceOfSatisfying(FalhaNaChamadaDeModulo.class, f -> {
                        assertThat(f.motivo()).isEqualTo(Motivo.NAO_CONFIGURADO);
                        assertThat(f.modulo()).isEqualTo(ModuloDestino.LANDING);
                    });
        }
        assertThat(propriedades.timeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(propriedades.servico().clientId()).isEqualTo("marketing");
    }

    private FalhaNaChamadaDeModulo falhaAoLerEnvio() {
        return catchThrowableOfType(FalhaNaChamadaDeModulo.class,
                () -> clientes.landing().get(TENANT, "/api/landing/envios/{id}", Envio.class,
                        "8d1c0f8e-0000-4000-8000-000000000002"));
    }

    private ClientesDeModulos novosClientes(String segredo, Duration timeout, String url) {
        IntegracaoPropriedades propriedades = new IntegracaoPropriedades(timeout, url, url, url,
                new IntegracaoPropriedades.Servico("marketing", segredo));
        return new ClientesDeModulos(propriedades, json, relogio);
    }

    private static String envelope(String data) {
        return "{\"success\":true,\"data\":" + data + ",\"message\":null,\"errors\":[]}";
    }

    private static final class RelogioAjustavel extends Clock {

        private Instant agora = Instant.parse("2026-11-05T12:00:00Z");

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
