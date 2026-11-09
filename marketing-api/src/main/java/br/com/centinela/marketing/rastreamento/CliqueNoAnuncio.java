package br.com.centinela.marketing.rastreamento;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * Clique no anúncio, {@code GET /public/marketing/c/{codigo}} (R1, norteador §5, passo 0). Acha o
 * tenant pelo código do link (nunca por parâmetro), registra a visita com o contexto técnico do
 * aparelho e devolve a URL da landing page com as UTM do link e o {@code mkt_visita}.
 *
 * <p>Com o rastreamento desligado no tenant, só redireciona, sem visita. O IP e o User-Agent não são
 * guardados nem vão para log: só o que sai deles (tipo de dispositivo, sistema, navegador e
 * localização aproximada).
 */
@Service
public class CliqueNoAnuncio {

    private static final Logger LOG = LoggerFactory.getLogger(CliqueNoAnuncio.class);

    private final AcessoPublico acesso;
    private final LinkCampanhaRepository links;
    private final VisitaAnuncioRepository visitas;
    private final RastreamentoConfiguracaoRepository configuracoes;
    private final LocalizacaoPorIp localizacao;
    private final TransactionTemplate transacao;

    CliqueNoAnuncio(AcessoPublico acesso, LinkCampanhaRepository links, VisitaAnuncioRepository visitas,
            RastreamentoConfiguracaoRepository configuracoes, LocalizacaoPorIp localizacao,
            TransactionTemplate transacao) {
        this.acesso = acesso;
        this.links = links;
        this.visitas = visitas;
        this.configuracoes = configuracoes;
        this.localizacao = localizacao;
        this.transacao = transacao;
    }

    /**
     * @return a URL da landing page; vazio se o link não existe ou está desligado
     */
    public Optional<URI> registrar(String codigo, String userAgent, String ip) {
        Optional<UUID> tenant = acesso.tenantDoLink(codigo);
        if (tenant.isEmpty()) {
            return Optional.empty();
        }
        return TenantContexto.calcular(tenant.get(), () -> transacao.execute(status -> {
            LinkCampanha link = acesso.linkPeloCodigo(codigo).flatMap(links::findById).orElse(null);
            if (link == null || !link.isAtivo()) {
                return Optional.<URI>empty();
            }
            RastreamentoConfiguracao configuracao = configuracoes.findFirstBy()
                    .orElseGet(RastreamentoConfiguracao::padrao);
            if (!configuracao.isAtivo()) {
                return Optional.of(link.destinoPara(null));
            }
            VisitaAnuncio visita = visitas.save(new VisitaAnuncio(link, contextoTecnico(userAgent, ip),
                    OffsetDateTime.now(ZoneOffset.UTC), configuracao.prazo()));
            LOG.info("Clique no link {} registrou a visita {}", link.getId(), visita.getId());
            return Optional.of(link.destinoPara(visita.getId()));
        }));
    }

    private Map<String, Object> contextoTecnico(String userAgent, String ip) {
        LeitorDeUserAgent.Aparelho aparelho = LeitorDeUserAgent.ler(userAgent);
        Map<String, Object> contexto = new LinkedHashMap<>();
        contexto.put("tipoDispositivo", aparelho.tipoDispositivo());
        contexto.put("sistema", aparelho.sistema());
        contexto.put("navegador", aparelho.navegador());
        localizacao.localizar(ip).ifPresent(local -> {
            Map<String, Object> onde = new LinkedHashMap<>();
            onde.put("cidade", local.cidade());
            onde.put("uf", local.uf());
            onde.put("pais", local.pais());
            onde.put("simulada", local.simulada());
            contexto.put("localizacao", onde);
        });
        return contexto;
    }
}
