package br.com.centinela.marketing.compartilhado.http;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereços e credencial das chamadas a outros módulos ({@code marketing.integracao} no
 * {@code application.yml}). Os valores vêm das variáveis de ambiente da plataforma:
 * {@code IDENTITY_BASE_URL}, {@code LANDING_BASE_URL}, {@code CRM_BASE_URL},
 * {@code SVC_CLIENT_ID} e {@code SVC_CLIENT_SECRET} (Contrato §9.1, §9.2).
 *
 * @param timeout         limite de cada chamada, conexão e resposta (Contrato §9.6: 3 s)
 * @param identityBaseUrl endereço do identity, que emite o token de serviço
 * @param landingBaseUrl  endereço do Landing, pelo nome do container
 * @param crmBaseUrl      endereço do CRM, pelo nome do container
 * @param servico         credencial do token de serviço
 */
@ConfigurationProperties("marketing.integracao")
public record IntegracaoPropriedades(
        Duration timeout,
        String identityBaseUrl,
        String landingBaseUrl,
        String crmBaseUrl,
        Servico servico) {

    public static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(3);

    public IntegracaoPropriedades {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? TIMEOUT_PADRAO : timeout;
        servico = servico == null ? new Servico(null, null) : servico;
    }

    public String baseUrl(ModuloDestino modulo) {
        return switch (modulo) {
            case IDENTITY -> identityBaseUrl;
            case LANDING -> landingBaseUrl;
            case CRM -> crmBaseUrl;
        };
    }

    /**
     * Credencial do token de serviço: {@code clientId = marketing} e o segredo
     * {@code SVC_MARKETING_SEGREDO}, que o compose da plataforma entrega em {@code SVC_CLIENT_SECRET}.
     */
    public record Servico(String clientId, String clientSecret) {

        public Servico {
            clientId = clientId == null || clientId.isBlank() ? "marketing" : clientId;
        }

        boolean configurado() {
            return clientSecret != null && !clientSecret.isBlank();
        }

        /** Nunca expõe o segredo. */
        @Override
        public String toString() {
            return "Servico[clientId=" + clientId + ", clientSecret=" + (configurado() ? "***" : "(vazio)") + "]";
        }
    }
}
