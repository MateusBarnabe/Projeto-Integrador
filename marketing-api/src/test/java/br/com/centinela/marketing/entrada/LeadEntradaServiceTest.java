package br.com.centinela.marketing.entrada;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.mocks.AutomacaoEmailMockService;
import br.com.centinela.marketing.mocks.CrmLeadMockService;
import br.com.centinela.marketing.mocks.GeoLocationMockService;

class LeadEntradaServiceTest {

    private static final UUID EVENTO = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private final LeadEntradaBufferRepository repository = Mockito.mock(LeadEntradaBufferRepository.class);
    private final GeoLocationMockService geoLocation = Mockito.mock(GeoLocationMockService.class);
    private final CrmLeadMockService crm = Mockito.mock(CrmLeadMockService.class);
    private final AutomacaoEmailMockService email = Mockito.mock(AutomacaoEmailMockService.class);
    private final LeadEntradaService service = new LeadEntradaService(repository, new ObjectMapper(), geoLocation, crm, email);

    @AfterEach
    void limparTenant() {
        TenantContexto.limpar();
    }

    @Test
    void normalizaEmailPersisteUmaEntradaEChamaOsMocks() {
        LeadEnriquecido dados = lead("  Ana Souza ", " ANA@EXAMPLE.COM ");
        when(repository.existsByEventoId(EVENTO)).thenReturn(false);
        when(geoLocation.resolver(null)).thenReturn("Localização mock (desenvolvimento)");

        service.processar(new LeadEnriquecidoEvento(EVENTO, "landing.lead.enriquecido", TENANT, dados));

        ArgumentCaptor<LeadEntradaBuffer> captor = ArgumentCaptor.forClass(LeadEntradaBuffer.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Ana Souza");
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(captor.getValue().getDadosComportamentais()).contains("dwell_time_seconds");
        verify(crm).registrarRecebimento(" ANA@EXAMPLE.COM ");
        verify(email).preparar(" ANA@EXAMPLE.COM ", "campanha-teste");
        assertThat(TenantContexto.atual()).isEmpty();
    }

    @Test
    void eventoDuplicadoNaoPersisteNovamente() {
        when(repository.existsByEventoId(EVENTO)).thenReturn(true);

        service.processar(new LeadEnriquecidoEvento(EVENTO, "landing.lead.enriquecido", TENANT,
                lead("Ana", "ana@example.com")));

        verify(repository, never()).save(any());
        verify(crm, never()).registrarRecebimento(any());
    }

    @Test
    void rejeitaLeadSemEmailValido() {
        assertThatThrownBy(() -> service.processar(new LeadEnriquecidoEvento(EVENTO,
                "landing.lead.enriquecido", TENANT, lead("Ana", "sem-email"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("e-mail");
    }

    private static LeadEnriquecido lead(String nome, String email) {
        return new LeadEnriquecido(null, nome, email, null, null, "google", "cpc", "campanha-teste",
                null, null, "desktop", "Windows", "Edge", null, "America/Sao_Paulo", "pt-BR", 40, 50,
                List.of("cta"), List.of("/"));
    }
}