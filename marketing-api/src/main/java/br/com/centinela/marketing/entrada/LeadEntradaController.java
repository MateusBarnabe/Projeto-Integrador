package br.com.centinela.marketing.entrada;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Paginacao;
import br.com.centinela.marketing.compartilhado.web.Resposta;

/** Visão autenticada para demonstrar e inspecionar as entradas capturadas pelo IOT. */
@RestController
@RequestMapping("/api/marketing/entradas")
public class LeadEntradaController {

    private static final Map<String, String> CAMPOS_ORDENAVEIS = Map.of(
            "criadoEm", "criadoEm", "nome", "nome", "campanha", "utmCampaign");

    private final LeadEntradaBufferRepository repository;

    public LeadEntradaController(LeadEntradaBufferRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    // @PreAuthorize("hasAuthority('marketing.lead.ver')")
    public Resposta<Pagina<LeadEntradaResumo>> listar(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho,
            @RequestParam(required = false) String ordenar) {
        var pedido = Paginacao.pedido(pagina, tamanho, ordenar, CAMPOS_ORDENAVEIS);
        Page<LeadEntradaBuffer> resultado = repository.findAll(pedido);
        return Resposta.ok(Pagina.de(resultado, LeadEntradaResumo::de));
    }

    public record LeadEntradaResumo(
            String nome,
            String email,
            String campanha,
            String origem,
            String tipo,
            boolean processado) {

        static LeadEntradaResumo de(LeadEntradaBuffer buffer) {
            return new LeadEntradaResumo(buffer.getNome(), buffer.getEmail(), buffer.getUtmCampaign(),
                    buffer.getUtmSource(), buffer.getTipo(), buffer.isProcessado());
        }
    }
}