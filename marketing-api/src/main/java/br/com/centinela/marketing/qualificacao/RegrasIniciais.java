package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Regras de pontuação iniciais do tenant (D7 e R5, norteador §9.3): as de {@code utm} da matriz e as
 * de {@code comportamento} do lead 0. Entram na primeira vez que o tenant usa as regras, seja na
 * primeira pontuação de um lead ({@link PontuacaoDeLeads}), seja na tela de qualificação. Quem já
 * teve alguma regra, mesmo excluída, não recebe de novo.
 */
@Component
public class RegrasIniciais {

    private final JdbcTemplate jdbc;
    private final RegraPontuacaoRepository regras;

    RegrasIniciais(JdbcTemplate jdbc, RegraPontuacaoRepository regras) {
        this.jdbc = jdbc;
        this.regras = regras;
    }

    @Transactional
    public void garantir() {
        UUID tenant = TenantContexto.exigir();
        if (jaTeveRegras(tenant)) {
            return;
        }
        // Dois leads do mesmo tenant novo pontuados ao mesmo tempo não podem criar as regras duas vezes
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", resultado -> null,
                "marketing.regras_iniciais:" + tenant);
        if (jaTeveRegras(tenant)) {
            return;
        }
        // Matriz 9.3: Origem do tráfego (utm)
        regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("source", "google", "medium", "cpc"), Map.of("direto", true)), 15));
        regras.save(RegraPontuacao.porUtm("Origem do tráfego",
                List.of(Map.of("medium", List.of("display", "social"))), 5));

        // R5: Comportamento da visita de anúncio (lead 0)
        regras.save(RegraPontuacao.porComportamento("Engajamento na página",
                MetricaComportamento.TEMPO_NA_PAGINA, List.of(Map.of("minimo", 30)), 20));
        regras.save(RegraPontuacao.porComportamento("Interesse em preço",
                MetricaComportamento.PAGINA_VISITADA, List.of("precos"), 15));
        regras.save(RegraPontuacao.porComportamento("Simulou o investimento",
                MetricaComportamento.ELEMENTO_CLICADO, List.of("simulador-preco"), 10));
        regras.save(RegraPontuacao.porComportamento("Leu a página inteira",
                MetricaComportamento.PROFUNDIDADE_ROLAGEM, List.of(Map.of("minimo", 90)), 5));
    }

    /** Inclui as excluídas ({@code deleted_at}), fora do filtro do Hibernate. */
    private boolean jaTeveRegras(UUID tenant) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM marketing.regras_pontuacao WHERE tenant_id = ?)", Boolean.class, tenant));
    }
}
