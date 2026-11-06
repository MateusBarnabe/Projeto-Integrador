package br.com.centinela.marketing.compartilhado.http;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ponto de entrada das chamadas a outros módulos: um {@link ClienteDeModulo} por destino, todos com o
 * mesmo token de serviço em cache e o mesmo timeout (conexão e resposta).
 */
public class ClientesDeModulos implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClientesDeModulos.class);

    private final HttpClient http;
    private final Map<ModuloDestino, ClienteDeModulo> clientes = new EnumMap<>(ModuloDestino.class);

    public ClientesDeModulos(IntegracaoPropriedades propriedades, ObjectMapper json) {
        this(propriedades, json, Clock.systemUTC());
    }

    ClientesDeModulos(IntegracaoPropriedades propriedades, ObjectMapper json, Clock relogio) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(propriedades.timeout())
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(propriedades.timeout());

        ChamadaHttp identity = new ChamadaHttp(ModuloDestino.IDENTITY, propriedades.identityBaseUrl(), fabrica, json);
        TokenDeServico token = new TokenDeServico(identity, propriedades.servico(), relogio, json);
        for (ModuloDestino modulo : ModuloDestino.values()) {
            ChamadaHttp chamada = modulo == ModuloDestino.IDENTITY ? identity
                    : new ChamadaHttp(modulo, propriedades.baseUrl(modulo), fabrica, json);
            clientes.put(modulo, new ClienteDeModulo(chamada, token, json));
        }

        if (!propriedades.servico().configurado()) {
            log.warn("SVC_CLIENT_SECRET não definido: chamadas a outros módulos vão falhar até ele ser configurado.");
        }
    }

    public ClienteDeModulo para(ModuloDestino modulo) {
        return clientes.get(modulo);
    }

    public ClienteDeModulo identity() {
        return para(ModuloDestino.IDENTITY);
    }

    public ClienteDeModulo landing() {
        return para(ModuloDestino.LANDING);
    }

    public ClienteDeModulo crm() {
        return para(ModuloDestino.CRM);
    }

    @Override
    public void close() {
        http.close();
    }
}
