package br.com.centinela.marketing.meta;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da integração com o Meta Ads ({@code marketing.meta} no {@code application.yml}).
 *
 * @param graphVersao versão da Graph API, com ou sem o {@code v} ({@code v23.0})
 * @param webhook     webhook público do Meta (E3)
 * @param leads       job 4.1, leads do Meta (E4)
 */
@ConfigurationProperties("marketing.meta")
public record MetaPropriedades(String graphVersao, Webhook webhook, Leads leads) {

    public static final String GRAPH_VERSAO_PADRAO = "v23.0";

    public MetaPropriedades {
        graphVersao = graphVersao == null || graphVersao.isBlank() ? GRAPH_VERSAO_PADRAO : graphVersao.strip();
        webhook = webhook == null ? new Webhook(null, null) : webhook;
        leads = leads == null ? new Leads(null, null, null) : leads;
    }

    /** A versão no formato do caminho da Graph API: sempre com o {@code v} na frente. */
    public String graphVersaoNoCaminho() {
        return graphVersao.startsWith("v") || graphVersao.startsWith("V") ? "v" + graphVersao.substring(1)
                : "v" + graphVersao;
    }

    /**
     * @param cacheTenant      quanto tempo o subdomínio resolvido no identity fica em cache; zero desliga
     * @param tamanhoMaximoCorpo teto do corpo do webhook, em bytes, lido antes de conferir a assinatura
     */
    public record Webhook(Duration cacheTenant, Integer tamanhoMaximoCorpo) {

        public Webhook {
            cacheTenant = cacheTenant == null || cacheTenant.isNegative() ? Duration.ofSeconds(60) : cacheTenant;
            tamanhoMaximoCorpo = tamanhoMaximoCorpo == null || tamanhoMaximoCorpo <= 0 ? 256 * 1024
                    : tamanhoMaximoCorpo;
        }
    }

    /**
     * @param lote           linhas do buffer por tenant em cada execução (norteador §4.1: 500)
     * @param maximoDeTentativas falhas seguidas até a linha virar {@code falha_permanente}
     * @param intervalo      de quanto em quanto tempo o job roda (norteador §4.1: 5 minutos)
     */
    public record Leads(Integer lote, Integer maximoDeTentativas, Duration intervalo) {

        public Leads {
            lote = lote == null || lote <= 0 ? 500 : lote;
            maximoDeTentativas = maximoDeTentativas == null || maximoDeTentativas <= 0 ? 5 : maximoDeTentativas;
            intervalo = intervalo == null || intervalo.isZero() || intervalo.isNegative() ? Duration.ofMinutes(5)
                    : intervalo;
        }
    }
}
