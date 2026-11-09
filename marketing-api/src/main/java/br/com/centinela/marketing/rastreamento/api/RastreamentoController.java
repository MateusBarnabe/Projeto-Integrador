package br.com.centinela.marketing.rastreamento.api;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Paginacao;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import br.com.centinela.marketing.rastreamento.LinkCampanha;
import br.com.centinela.marketing.rastreamento.LinkCampanhaRepository;
import br.com.centinela.marketing.rastreamento.RastreamentoConfiguracao;
import br.com.centinela.marketing.rastreamento.RastreamentoConfiguracaoRepository;
import br.com.centinela.marketing.rastreamento.SituacaoVisita;
import br.com.centinela.marketing.rastreamento.VisitaAnuncio;
import br.com.centinela.marketing.rastreamento.VisitaAnuncioRepository;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.AlteracaoConfiguracao;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.Configuracao;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.ItemLink;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.ItemVisita;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.PedidoLink;
import br.com.centinela.marketing.rastreamento.api.RastreamentoDtos.ResumoVisitas;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

/**
 * Rastreamento do anúncio na tela Configurações (R1, norteador §9.4): a configuração do tenant, os
 * links de campanha e as visitas ("lead 0") com o resumo das últimas 24 horas. Leitura com
 * {@code marketing.configuracao.ver}, edição com {@code marketing.configuracao.editar}. Tenant só do
 * token; registro de outro tenant responde {@code 404}.
 */
@RestController
public class RastreamentoController {

    private final RastreamentoConfiguracaoRepository configuracoes;
    private final LinkCampanhaRepository links;
    private final VisitaAnuncioRepository visitas;
    private final String urlPublica;

    public RastreamentoController(RastreamentoConfiguracaoRepository configuracoes, LinkCampanhaRepository links,
            VisitaAnuncioRepository visitas,
            @Value("${marketing.rastreamento.url-publica:http://localhost:8087}") String urlPublica) {
        this.configuracoes = configuracoes;
        this.links = links;
        this.visitas = visitas;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    // --- configuração ---------------------------------------------------------------------------

    @GetMapping("/api/marketing/rastreamento")
    @PreAuthorize("hasAuthority('marketing.configuracao.ver')")
    @Operation(summary = "Configuração do rastreamento do anúncio: ligado, prazo de conversão e URL do script")
    @Transactional(readOnly = true)
    public Resposta<Configuracao> configuracao() {
        return Resposta.ok(Configuracao.de(configuracoes.findFirstBy().orElseGet(RastreamentoConfiguracao::padrao),
                urlPublica));
    }

    @PutMapping("/api/marketing/rastreamento")
    @PreAuthorize("hasAuthority('marketing.configuracao.editar')")
    @Operation(summary = "Liga ou desliga o rastreamento e define o prazo para o visitante converter")
    @Transactional
    public Resposta<Configuracao> alterarConfiguracao(@Valid @RequestBody AlteracaoConfiguracao pedido) {
        RastreamentoConfiguracao configuracao = configuracoes.findFirstBy()
                .orElseGet(RastreamentoConfiguracao::padrao);
        configuracao.alterar(pedido.ativo(), pedido.prazoConversaoMinutos());
        return Resposta.ok(Configuracao.de(configuracoes.save(configuracao), urlPublica));
    }

    // --- links de campanha ----------------------------------------------------------------------

    @GetMapping("/api/marketing/links-campanha")
    @PreAuthorize("hasAuthority('marketing.configuracao.ver')")
    @Operation(summary = "Links de campanha, com a URL para o anúncio e quantos cliques cada um teve")
    @Transactional(readOnly = true)
    public Resposta<Pagina<ItemLink>> listarLinks(@RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho) {
        PageRequest pedido = Paginacao.pedido(pagina, tamanho, null, Map.of("criadoEm", "criadoEm"));
        return Resposta.ok(Pagina.de(links.findAll(pedido),
                link -> ItemLink.de(link, urlPublica, visitas.countByLinkId(link.getId()))));
    }

    @PostMapping("/api/marketing/links-campanha")
    @PreAuthorize("hasAuthority('marketing.configuracao.editar')")
    @Operation(summary = "Cria um link de campanha para usar como destino do anúncio")
    @Transactional
    public ResponseEntity<Resposta<ItemLink>> criarLink(@Valid @RequestBody PedidoLink pedido) {
        LinkCampanha link = new LinkCampanha(pedido.nome(), pedido.urlDestino(), pedido.utm());
        if (Boolean.FALSE.equals(pedido.ativo())) {
            link.alterar(pedido.nome(), pedido.urlDestino(), pedido.utm(), false);
        }
        LinkCampanha criado = links.save(link);
        return ResponseEntity.created(URI.create("/api/marketing/links-campanha/" + criado.getId()))
                .body(Resposta.ok(ItemLink.de(criado, urlPublica, 0)));
    }

    @PutMapping("/api/marketing/links-campanha/{id}")
    @PreAuthorize("hasAuthority('marketing.configuracao.editar')")
    @Operation(summary = "Altera um link de campanha; o código e a URL pública não mudam")
    @Transactional
    public ResponseEntity<Resposta<ItemLink>> alterarLink(@PathVariable UUID id, @Valid @RequestBody PedidoLink pedido) {
        return links.findById(id).map(link -> {
            link.alterar(pedido.nome(), pedido.urlDestino(), pedido.utm(), !Boolean.FALSE.equals(pedido.ativo()));
            LinkCampanha salvo = links.save(link);
            return ResponseEntity.ok(Resposta.ok(ItemLink.de(salvo, urlPublica, visitas.countByLinkId(id))));
        }).orElseGet(() -> naoEncontrado("Link não encontrado."));
    }

    @DeleteMapping("/api/marketing/links-campanha/{id}")
    @PreAuthorize("hasAuthority('marketing.configuracao.editar')")
    @Operation(summary = "Exclui um link de campanha (soft delete); o anúncio que apontar para ele recebe 404")
    @Transactional
    public ResponseEntity<Resposta<Void>> excluirLink(@PathVariable UUID id) {
        return links.findById(id).map(link -> {
            links.delete(link);
            return ResponseEntity.ok(Resposta.<Void>ok(null));
        }).orElseGet(() -> naoEncontrado("Link não encontrado."));
    }

    // --- visitas ---------------------------------------------------------------------------------

    @GetMapping("/api/marketing/visitas-anuncio")
    @PreAuthorize("hasAuthority('marketing.configuracao.ver')")
    @Operation(summary = "Visitas de anúncio (lead 0), das mais recentes, com o contexto técnico e o comportamento",
            description = "situacao: ativa, convertida, descartada (vários separados por vírgula; padrão: todas).")
    @Transactional(readOnly = true)
    public Resposta<Pagina<ItemVisita>> listarVisitas(@RequestParam(required = false) List<String> situacao,
            @RequestParam(required = false) Integer pagina, @RequestParam(required = false) Integer tamanho) {
        PageRequest pedido = Paginacao.pedido(pagina, tamanho, null, Map.of("clicadoEm", "clicadoEm"));
        Page<VisitaAnuncio> pagina0 = visitas.findBySituacaoIn(situacoes(situacao), pedido);
        Set<UUID> idsDosLinks = pagina0.getContent().stream().map(VisitaAnuncio::getLinkId).collect(Collectors.toSet());
        Map<UUID, String> nomes = links.findAllById(idsDosLinks).stream()
                .collect(Collectors.toMap(LinkCampanha::getId, LinkCampanha::getNome));
        return Resposta.ok(Pagina.de(pagina0, visita -> ItemVisita.de(visita, nomes.get(visita.getLinkId()))));
    }

    @GetMapping("/api/marketing/visitas-anuncio/resumo")
    @PreAuthorize("hasAuthority('marketing.configuracao.ver')")
    @Operation(summary = "Visitas de anúncio das últimas 24 horas por situação e a taxa de conversão")
    @Transactional(readOnly = true)
    public Resposta<ResumoVisitas> resumirVisitas() {
        Map<SituacaoVisita, Long> porSituacao = visitas
                .contarPorSituacao(OffsetDateTime.now(ZoneOffset.UTC).minusHours(24)).stream()
                .collect(Collectors.toMap(linha -> (SituacaoVisita) linha[0], linha -> (Long) linha[1]));
        long ativas = porSituacao.getOrDefault(SituacaoVisita.ATIVA, 0L);
        long convertidas = porSituacao.getOrDefault(SituacaoVisita.CONVERTIDA, 0L);
        long descartadas = porSituacao.getOrDefault(SituacaoVisita.DESCARTADA, 0L);
        long total = ativas + convertidas + descartadas;
        double taxa = total == 0 ? 0 : Math.round(convertidas * 1_000_000.0 / total) / 10_000.0;
        return Resposta.ok(new ResumoVisitas(ativas, convertidas, descartadas, total, taxa));
    }

    // --- erros -----------------------------------------------------------------------------------

    /** Regra do domínio (URL fora de http/https, prazo fora do intervalo): {@code 400} com a mensagem. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Resposta<Void>> invalido(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Resposta.erro("Dados inválidos.",
                List.of(new ErroCampo(null, "CAMPO_INVALIDO", ex.getMessage()))));
    }

    private static Set<SituacaoVisita> situacoes(List<String> valores) {
        if (valores == null || valores.isEmpty()) {
            return EnumSet.allOf(SituacaoVisita.class);
        }
        Map<String, SituacaoVisita> porValor = EnumSet.allOf(SituacaoVisita.class).stream()
                .collect(Collectors.toMap(SituacaoVisita::valor, Function.identity()));
        Set<SituacaoVisita> escolhidas = EnumSet.noneOf(SituacaoVisita.class);
        valores.stream().flatMap(valor -> List.of(valor.split(",")).stream()).map(String::strip)
                .filter(valor -> !valor.isEmpty()).forEach(valor -> {
                    SituacaoVisita situacao = porValor.get(valor);
                    if (situacao == null) {
                        throw new IllegalArgumentException("Situação desconhecida: " + valor
                                + ". Use ativa, convertida ou descartada.");
                    }
                    escolhidas.add(situacao);
                });
        return escolhidas.isEmpty() ? EnumSet.allOf(SituacaoVisita.class) : escolhidas;
    }

    private static <T> ResponseEntity<Resposta<T>> naoEncontrado(String mensagem) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro(mensagem));
    }
}
