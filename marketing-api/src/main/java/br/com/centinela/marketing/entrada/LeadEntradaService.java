package br.com.centinela.marketing.entrada;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.mocks.AutomacaoEmailMockService;
import br.com.centinela.marketing.mocks.CrmLeadMockService;
import br.com.centinela.marketing.mocks.GeoLocationMockService;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/** Valida, normaliza e grava a entrada sem acoplar o core às integrações externas. */
@Service
public class LeadEntradaService {

    private final LeadEntradaBufferRepository repository;
    private final ObjectMapper objectMapper;
    private final GeoLocationMockService geoLocation;
    private final CrmLeadMockService crm;
    private final AutomacaoEmailMockService automacaoEmail;

    public LeadEntradaService(LeadEntradaBufferRepository repository, ObjectMapper objectMapper,
            GeoLocationMockService geoLocation, CrmLeadMockService crm,
            AutomacaoEmailMockService automacaoEmail) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.geoLocation = geoLocation;
        this.crm = crm;
        this.automacaoEmail = automacaoEmail;
    }

    public void processar(LeadEnriquecidoEvento evento) {
        validar(evento);
        if (repository.existsByEventoId(evento.id())) {
            return;
        }

        Map<String, Object> tecnicosMapa = new LinkedHashMap<>();
        tecnicosMapa.put("device_type", evento.dados().deviceType());
        tecnicosMapa.put("os", evento.dados().os());
        tecnicosMapa.put("browser", evento.dados().browser());
        tecnicosMapa.put("location", geoLocation.resolver(evento.dados().location()));
        tecnicosMapa.put("timezone", evento.dados().timezone());
        tecnicosMapa.put("language", evento.dados().language());
        String tecnicos = json(tecnicosMapa);
        String comportamentais = json(Map.of(
                "dwell_time_seconds", evento.dados().dwellTimeSeconds(),
                "scroll_depth_percent", evento.dados().scrollDepthPercent(),
                "clicked_elements", evento.dados().clickedElements() == null ? List.of() : evento.dados().clickedElements(),
                "pages_visited", evento.dados().pagesVisited() == null ? List.of() : evento.dados().pagesVisited()));

        TenantContexto.calcular(evento.tenantId(), () -> repository.save(
                LeadEntradaBuffer.de(evento, tecnicos, comportamentais)));
        crm.registrarRecebimento(evento.dados().email());
        automacaoEmail.preparar(evento.dados().email(), evento.dados().utmCampaign());
    }

    private void validar(LeadEnriquecidoEvento evento) {
        if (evento == null || evento.id() == null || evento.tipo() == null
                || evento.tenantId() == null || evento.dados() == null) {
            throw new IllegalArgumentException("Evento de lead incompleto.");
        }
        if (evento.dados().nome() == null || evento.dados().nome().isBlank()
                || evento.dados().email() == null || evento.dados().email().isBlank()
                || !evento.dados().email().contains("@")) {
            throw new IllegalArgumentException("Lead sem nome ou e-mail válido.");
        }
    }

    private String json(Object valor) {
        try {
            return objectMapper.writeValueAsString(valor);
        } catch (JsonProcessingException excecao) {
            throw new IllegalStateException("Não foi possível normalizar os metadados do lead.", excecao);
        }
    }
}