package br.com.centinela.marketing.compartilhado.http;

import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo.Motivo;

/**
 * Cliente HTTP de um módulo da plataforma (Contrato §9.1, §9.2, §9.6). Chama direto pelo nome do
 * container (endereço de {@link IntegracaoPropriedades}), com {@code Authorization: Bearer} do token
 * de serviço, {@code X-Tenant-Id} e o {@code X-Request-Id} atual, e timeout de 3 s.
 *
 * <p>Devolve o {@code data} do envelope já convertido. Toda falha (timeout, módulo fora,
 * {@code 4xx}, {@code 5xx}, envelope inválido) vira {@link FalhaNaChamadaDeModulo}. Um {@code 401}
 * do destino descarta o token, pede outro e repete a chamada uma vez.
 *
 * <p>Os clientes de negócio (Landing, CRM, identity) usam esta classe em vez de montar HTTP; obtenha-a
 * em {@link ClientesDeModulos}. O {@code caminho} é um modelo com variáveis ({@code /api/landing/envios/{id}}),
 * para os valores não aparecerem no log.
 */
public final class ClienteDeModulo {

    public static final String CABECALHO_TENANT = "X-Tenant-Id";

    private final ChamadaHttp chamada;
    private final TokenDeServico token;
    private final ObjectMapper json;

    ClienteDeModulo(ChamadaHttp chamada, TokenDeServico token, ObjectMapper json) {
        this.chamada = chamada;
        this.token = token;
        this.json = json;
    }

    public ModuloDestino modulo() {
        return chamada.modulo();
    }

    /**
     * {@code GET} com o tenant informado. Em rotina, o tenant é o de {@code TenantContexto.exigir()}.
     * Tenant nulo não envia {@code X-Tenant-Id}: só para rotas sem tenant, como
     * {@code GET /api/identity/tenants/resolver}.
     */
    public <T> T get(UUID tenant, String caminho, Class<T> tipo, Object... variaveis) {
        return executar(HttpMethod.GET, tenant, caminho, null, tipo(tipo), variaveis);
    }

    public <T> T get(UUID tenant, String caminho, ParameterizedTypeReference<T> tipo, Object... variaveis) {
        return executar(HttpMethod.GET, tenant, caminho, null, tipo(tipo), variaveis);
    }

    /** {@code POST} com corpo em JSON. {@code tipo} nulo ou {@code Void.class} ignora o {@code data}. */
    public <T> T post(UUID tenant, String caminho, Object corpo, Class<T> tipo, Object... variaveis) {
        return executar(HttpMethod.POST, tenant, caminho, corpo, tipo(tipo), variaveis);
    }

    /** {@code PUT} com corpo em JSON. {@code tipo} nulo ou {@code Void.class} ignora o {@code data}. */
    public <T> T put(UUID tenant, String caminho, Object corpo, Class<T> tipo, Object... variaveis) {
        return executar(HttpMethod.PUT, tenant, caminho, corpo, tipo(tipo), variaveis);
    }

    /** Qualquer método, para o que os atalhos acima não cobrem. */
    public <T> T chamar(HttpMethod metodo, UUID tenant, String caminho, Object corpo,
            ParameterizedTypeReference<T> tipo, Object... variaveis) {
        return executar(metodo, tenant, caminho, corpo, tipo(tipo), variaveis);
    }

    private <T> T executar(HttpMethod metodo, UUID tenant, String caminho, Object corpo, JavaType tipo,
            Object[] variaveis) {
        String usado = token.obter();
        try {
            return chamada.executar(metodo, caminho, variaveis, corpo, tipo, cabecalhos(usado, tenant));
        } catch (FalhaNaChamadaDeModulo falha) {
            if (falha.motivo() != Motivo.NAO_AUTENTICADO || falha.modulo() != modulo()) {
                throw falha;
            }
            // Token revogado ou chave do identity trocada: pede outro e tenta uma única vez mais
            token.descartar(usado);
            String novo = token.obter();
            return chamada.executar(metodo, caminho, variaveis, corpo, tipo, cabecalhos(novo, tenant));
        }
    }

    private static Consumer<HttpHeaders> cabecalhos(String tokenDeServico, UUID tenant) {
        return h -> {
            h.setBearerAuth(tokenDeServico);
            if (tenant != null) {
                h.set(CABECALHO_TENANT, tenant.toString());
            }
        };
    }

    private JavaType tipo(Class<?> tipo) {
        return tipo == null || tipo == Void.class || tipo == void.class ? null : json.constructType(tipo);
    }

    private JavaType tipo(ParameterizedTypeReference<?> tipo) {
        return tipo == null ? null : json.constructType(tipo.getType());
    }
}
