package br.com.centinela.marketing.leads;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Timeline do lead, sempre na ordem da {@code sequencia}. */
public interface LeadEventoRepository extends JpaRepository<LeadEvento, UUID> {

    List<LeadEvento> findByLeadIdOrderBySequenciaAsc(UUID leadId);
}
