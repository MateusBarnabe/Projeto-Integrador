package br.com.centinela.marketing.entrada;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.compartilhado.web.Paginacao;
import br.com.centinela.marketing.compartilhado.web.Resposta;

/** Visão para demonstrar e inspecionar as entradas capturadas pelo IOT. */
@RestController
@RequestMapping("/api/marketing/entradas")
public class LeadEntradaController {

    private static final Map<String, String> CAMPOS_ORDENAVEIS = Map.of(
            "criadoEm", "criadoEm", "nome", "nome", "campanha", "utmCampaign");

    private final LeadEntradaBufferRepository repository;
    private final ObjectMapper objectMapper;

    public LeadEntradaController(LeadEntradaBufferRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    // @PreAuthorize("hasAuthority('marketing.lead.ver')")
    public Resposta<Pagina<LeadEntradaResumo>> listar(
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho,
            @RequestParam(required = false) String ordenar) {
        var pedido = Paginacao.pedido(pagina, tamanho, ordenar, CAMPOS_ORDENAVEIS);
        Page<LeadEntradaBuffer> resultado = repository.findAll(pedido);
        return Resposta.ok(Pagina.de(resultado, buffer -> LeadEntradaResumo.de(buffer, objectMapper)));
    }

    public record LeadEntradaResumo(
            UUID id,
            String nome,
            String email,
            String whatsapp,
            String telefone,
            String campanha,
            String origem,
            String tipo,
            String utmMedium,
            String utmContent,
            String referrer,
            Object dadosTecnicos,
            Object dadosComportamentais,
            boolean processado) {

        static LeadEntradaResumo de(LeadEntradaBuffer buffer, ObjectMapper mapper) {
            return new LeadEntradaResumo(
                    buffer.getId(),
                    buffer.getNome(),
                    buffer.getEmail(),
                    buffer.getWhatsapp(),
                    buffer.getTelefone(),
                    buffer.getUtmCampaign(),
                    buffer.getUtmSource(),
                    buffer.getTipo(),
                    buffer.getUtmMedium(),
                    buffer.getUtmContent(),
                    buffer.getReferrer(),
                    parseJson(buffer.getDadosTecnicos(), mapper),
                    parseJson(buffer.getDadosComportamentais(), mapper),
                    buffer.isProcessado());
        }

        private static Object parseJson(String json, ObjectMapper mapper) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return mapper.readValue(json, Object.class);
            } catch (Exception e) {
                return json;
            }
        }
    }
}