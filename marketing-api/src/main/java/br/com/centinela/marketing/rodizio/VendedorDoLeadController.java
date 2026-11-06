package br.com.centinela.marketing.rodizio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.centinela.marketing.compartilhado.web.ErroCampo;
import br.com.centinela.marketing.compartilhado.web.Resposta;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.rodizio.RodizioDeVendedores.LeadNaoEncontrado;
import br.com.centinela.marketing.rodizio.RodizioDeVendedores.VendedorForaDaEscala;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Reatribuição manual do vendedor de um lead (norteador §6 e §9.3), feita pela pessoa gestora no
 * detalhe do lead. Lead de outro tenant responde {@code 404}, nunca {@code 403}.
 */
@RestController
@RequestMapping("/api/marketing/leads")
class VendedorDoLeadController {

    private final RodizioDeVendedores rodizio;

    VendedorDoLeadController(RodizioDeVendedores rodizio) {
        this.rodizio = rodizio;
    }

    @PutMapping("/{id}/vendedor")
    @PreAuthorize("hasAuthority('marketing.qualificacao.editar')")
    public ResponseEntity<Resposta<VendedorDoLead>> reatribuir(@PathVariable UUID id,
            @Valid @RequestBody Reatribuicao corpo) {
        Lead lead = rodizio.reatribuir(id, corpo.vendedorId());
        return ResponseEntity.ok(Resposta.ok(new VendedorDoLead(lead.getId(), lead.getVendedorId(),
                lead.getVendedorAtribuidoEm())));
    }

    @ExceptionHandler(LeadNaoEncontrado.class)
    ResponseEntity<Resposta<Void>> leadNaoEncontrado(LeadNaoEncontrado ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro(ex.getMessage()));
    }

    @ExceptionHandler(VendedorForaDaEscala.class)
    ResponseEntity<Resposta<Void>> vendedorForaDaEscala(VendedorForaDaEscala ex) {
        return ResponseEntity.unprocessableEntity().body(Resposta.erro(ex.getMessage(),
                List.of(new ErroCampo("vendedorId", "VENDEDOR_FORA_DA_ESCALA", ex.getMessage()))));
    }

    /** @param vendedorId usuário do identity cadastrado em {@code vendedores_rodizio} */
    record Reatribuicao(@NotNull(message = "Informe o vendedor.") UUID vendedorId) {
    }

    record VendedorDoLead(UUID leadId, UUID vendedorId, OffsetDateTime vendedorAtribuidoEm) {
    }
}
