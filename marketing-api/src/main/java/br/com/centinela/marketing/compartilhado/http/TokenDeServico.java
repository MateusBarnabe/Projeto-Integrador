package br.com.centinela.marketing.compartilhado.http;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo.Motivo;

/**
 * Token de serviço do Marketing (Contrato §9.2): pedido ao identity em
 * {@code POST /api/identity/auth/token-servico} com {@code {clientId, clientSecret}}, guardado em
 * memória e renovado pouco antes de vencer ({@code expiresIn}, hoje 900 s). O token tem
 * {@code sub = svc:marketing} e não carrega tenant; o tenant vai em {@code X-Tenant-Id} em cada chamada.
 *
 * <p>Token e segredo nunca vão para log nem para mensagem de exceção.
 */
final class TokenDeServico {

    static final String CAMINHO = "/api/identity/auth/token-servico";

    /** Renova até 60 s antes de vencer (ou na metade da validade, se ela for curta). */
    private static final long MARGEM_MAXIMA_SEGUNDOS = 60;

    private static final Logger log = LoggerFactory.getLogger(TokenDeServico.class);

    private final ChamadaHttp identity;
    private final IntegracaoPropriedades.Servico credencial;
    private final Clock relogio;
    private final ObjectMapper json;

    private String token;
    private Instant renovarEm;

    TokenDeServico(ChamadaHttp identity, IntegracaoPropriedades.Servico credencial, Clock relogio, ObjectMapper json) {
        this.identity = identity;
        this.credencial = credencial;
        this.relogio = relogio;
        this.json = json;
    }

    /** Token válido, do cache ou recém-emitido. */
    synchronized String obter() {
        Instant agora = relogio.instant();
        if (token != null && agora.isBefore(renovarEm)) {
            return token;
        }
        if (!credencial.configurado()) {
            throw new FalhaNaChamadaDeModulo(ModuloDestino.IDENTITY, Motivo.NAO_CONFIGURADO, null,
                    "SVC_CLIENT_SECRET não definido.", null, null);
        }

        Emitido emitido = identity.executar(HttpMethod.POST, CAMINHO, null,
                Map.of("clientId", credencial.clientId(), "clientSecret", credencial.clientSecret()),
                json.constructType(Emitido.class), cabecalhos -> { });
        if (emitido == null || emitido.accessToken() == null || emitido.accessToken().isBlank()
                || emitido.expiresIn() <= 0) {
            throw new FalhaNaChamadaDeModulo(ModuloDestino.IDENTITY, Motivo.RESPOSTA_INVALIDA, 200, null, null, null);
        }

        long margem = Math.min(MARGEM_MAXIMA_SEGUNDOS, emitido.expiresIn() / 2);
        token = emitido.accessToken();
        renovarEm = agora.plusSeconds(emitido.expiresIn() - margem);
        log.info("Token de serviço emitido para o clientId {} (vale {} s)", credencial.clientId(), emitido.expiresIn());
        return token;
    }

    /**
     * Descarta o token recusado pelo destino ({@code 401}), para a próxima chamada pedir outro. Só
     * descarta se ainda for o mesmo: outra thread pode já ter renovado.
     */
    synchronized void descartar(String recusado) {
        if (Objects.equals(token, recusado)) {
            token = null;
            renovarEm = null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Emitido(String accessToken, long expiresIn) {

        @Override
        public String toString() {
            return "Emitido[accessToken=***, expiresIn=" + expiresIn + "]";
        }
    }
}
