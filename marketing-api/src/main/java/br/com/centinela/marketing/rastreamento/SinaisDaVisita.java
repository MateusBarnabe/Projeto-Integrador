package br.com.centinela.marketing.rastreamento;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import jakarta.persistence.EntityManager;

/**
 * Sinais do script de rastreamento, {@code POST /public/marketing/visitas/{id}/sinais} (R1). Acha o
 * tenant pela visita (o id é o {@code mkt_visita}, gerado pelo servidor no clique), trava a visita e
 * junta os sinais. Cada sinal adia o descarte: o prazo conta do último.
 */
@Service
public class SinaisDaVisita {

    /** O que aconteceu com o sinal. */
    public enum Resultado {
        /** Juntado à visita. */
        REGISTRADO,
        /** A visita já foi convertida ou descartada: o sinal não muda nada. */
        IGNORADO,
        /** Não há visita com esse id. */
        VISITA_DESCONHECIDA
    }

    private final AcessoPublico acesso;
    private final VisitaAnuncioRepository visitas;
    private final RastreamentoConfiguracaoRepository configuracoes;
    private final TransactionTemplate transacao;
    private final EntityManager entityManager;

    SinaisDaVisita(AcessoPublico acesso, VisitaAnuncioRepository visitas,
            RastreamentoConfiguracaoRepository configuracoes, TransactionTemplate transacao,
            EntityManager entityManager) {
        this.acesso = acesso;
        this.visitas = visitas;
        this.configuracoes = configuracoes;
        this.transacao = transacao;
        this.entityManager = entityManager;
    }

    public Resultado registrar(UUID visitaId, SinaisDoVisitante sinais) {
        Optional<UUID> tenant = acesso.tenantDaVisita(visitaId);
        if (tenant.isEmpty()) {
            return Resultado.VISITA_DESCONHECIDA;
        }
        return TenantContexto.calcular(tenant.get(), () -> transacao.execute(status -> {
            if (!acesso.travarAtiva(visitaId)) {
                return Resultado.IGNORADO;
            }
            VisitaAnuncio visita = visitas.findById(visitaId).orElse(null);
            if (visita == null) {
                return Resultado.VISITA_DESCONHECIDA;
            }
            entityManager.refresh(visita);
            RastreamentoConfiguracao configuracao = configuracoes.findFirstBy()
                    .orElseGet(RastreamentoConfiguracao::padrao);
            boolean mudou = visita.registrarSinais(sinais, OffsetDateTime.now(ZoneOffset.UTC), configuracao.prazo());
            if (mudou) {
                visitas.save(visita);
            }
            return mudou ? Resultado.REGISTRADO : Resultado.IGNORADO;
        }));
    }
}
