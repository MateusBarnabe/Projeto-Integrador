package br.com.centinela.marketing.leads.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Paginacao;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import io.swagger.v3.oas.annotations.Operation;

/**
 * API de leads (A2, norteador §6, §9.2 e §9.6): lista, detalhe, resumo para outros módulos e busca
 * global da casca. O tenant vem só do token (ou do {@code X-Tenant-Id} com token de serviço, §9.2);
 * {@code tenantId} na query é ignorado. Lead de outro tenant ou excluído responde {@code 404}.
 */
@RestController
public class LeadsController {

    static final int MAXIMO_IDS_RESUMO = 100;
    static final int MINIMO_BUSCA = 2;
    static final int MAXIMO_BUSCA = 100;

    private final LeadConsultas consultas;

    public LeadsController(LeadConsultas consultas) {
        this.consultas = consultas;
    }

    @GetMapping("/api/marketing/leads")
    @PreAuthorize("hasAuthority('marketing.lead.ver')")
    @Operation(summary = "Lista paginada de leads, com filtros",
            description = "Filtros: etapa, faixa, origem, canal (aceitam vários valores separados por vírgula), "
                    + "campanha (utm_campaign), vendedorId, periodo (7d, 30d, 90d ou AAAA-MM-DD,AAAA-MM-DD) e q "
                    + "(nome ou e-mail). Ordenar: criadoEm (padrão), atualizadoEm, nome, etapa, pontuacao, "
                    + "qualificadoEm, com ,asc ou ,desc.")
    public Resposta<Pagina<ItemListaLead>> listar(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho,
            @RequestParam(required = false) String ordenar,
            @RequestParam(required = false) List<String> etapa,
            @RequestParam(required = false) List<String> faixa,
            @RequestParam(required = false) List<String> origem,
            @RequestParam(required = false) List<String> canal,
            @RequestParam(required = false) String campanha,
            @RequestParam(required = false) String vendedorId,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String q) {
        FiltroLeads filtro = FiltroLeads.interpretar(etapa, faixa, origem, canal, campanha, vendedorId, periodo, q);
        PageRequest pedido = Paginacao.pedido(pagina, tamanho, ordenar, LeadConsultas.CAMPOS_ORDENAVEIS);
        return Resposta.ok(consultas.listar(filtro, pedido));
    }

    @GetMapping("/api/marketing/leads/{id}")
    @PreAuthorize("hasAuthority('marketing.lead.ver')")
    @Operation(summary = "Detalhe do lead: contato, origem, respostas, pontuação, consentimentos, timeline e CRM")
    public ResponseEntity<Resposta<DetalheLead>> detalhar(@PathVariable UUID id) {
        return consultas.detalhar(id)
                .map(detalhe -> ResponseEntity.ok(Resposta.ok(detalhe)))
                .orElseGet(LeadsController::naoEncontrado);
    }

    @GetMapping("/api/marketing/leads/{id}/resumo")
    @PreAuthorize("hasAuthority('marketing.lead.ver_resumo')")
    @Operation(summary = "Resumo de um lead para outros módulos, sem dado pessoal")
    public ResponseEntity<Resposta<ResumoLead>> resumir(@PathVariable UUID id) {
        return consultas.resumir(id)
                .map(resumo -> ResponseEntity.ok(Resposta.ok(resumo)))
                .orElseGet(LeadsController::naoEncontrado);
    }

    @GetMapping("/api/marketing/leads/resumo")
    @PreAuthorize("hasAuthority('marketing.lead.ver_resumo')")
    @Operation(summary = "Resumo de vários leads (até 100 ids), sem dado pessoal",
            description = "Ids que não existem no tenant ficam de fora da resposta.")
    public Resposta<List<ResumoLead>> resumir(@RequestParam List<String> ids) {
        return Resposta.ok(consultas.resumir(idsDoResumo(ids)));
    }

    @GetMapping("/api/marketing/busca")
    @PreAuthorize("hasAuthority('marketing.lead.ver')")
    @Operation(summary = "Busca global da casca: leads por nome ou e-mail, até 5")
    public Resposta<List<ResultadoBusca>> buscar(@RequestParam String q) {
        String texto = q.strip();
        if (texto.length() < MINIMO_BUSCA) {
            throw new ParametroInvalidoException("Informe pelo menos 2 caracteres para buscar.",
                    new ErroCampo("q", "CAMPO_INVALIDO", "A busca exige pelo menos 2 caracteres."));
        }
        if (texto.length() > MAXIMO_BUSCA) {
            throw new ParametroInvalidoException("A busca aceita até 100 caracteres.",
                    new ErroCampo("q", "CAMPO_INVALIDO", "A busca aceita até 100 caracteres."));
        }
        return Resposta.ok(consultas.buscar(texto));
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    ResponseEntity<Resposta<Void>> parametroInvalido(ParametroInvalidoException ex) {
        return ResponseEntity.badRequest().body(Resposta.erro(ex.getMessage(), ex.erros()));
    }

    /** Aceita {@code ids=a,b} e {@code ids=a&ids=b}; repetidos contam uma vez, na ordem do pedido. */
    private static Set<UUID> idsDoResumo(List<String> valores) {
        Set<UUID> ids = new LinkedHashSet<>();
        List<ErroCampo> erros = new ArrayList<>();
        valores.stream()
                .flatMap(valor -> List.of(valor.split(",")).stream())
                .map(String::strip)
                .filter(valor -> !valor.isEmpty())
                .forEach(valor -> {
                    try {
                        ids.add(UUID.fromString(valor));
                    } catch (IllegalArgumentException excecao) {
                        erros.add(new ErroCampo("ids", "CAMPO_INVALIDO", "Não é um UUID: " + valor + "."));
                    }
                });
        if (!erros.isEmpty()) {
            throw new ParametroInvalidoException("Dados inválidos.", erros);
        }
        if (ids.isEmpty()) {
            throw new ParametroInvalidoException("Dados inválidos.",
                    new ErroCampo("ids", "CAMPO_OBRIGATORIO", "Informe pelo menos um id."));
        }
        if (ids.size() > MAXIMO_IDS_RESUMO) {
            throw new ParametroInvalidoException("Dados inválidos.", new ErroCampo("ids", "CAMPO_INVALIDO",
                    "Máximo de " + MAXIMO_IDS_RESUMO + " ids por chamada."));
        }
        return ids;
    }

    private static <T> ResponseEntity<Resposta<T>> naoEncontrado() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro("Lead não encontrado."));
    }
}
