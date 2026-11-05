package br.com.centinela.marketing.leads;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de {@link Lead}. O filtro por tenant e por soft delete é aplicado pelo Hibernate. */
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByVisitanteId(UUID visitanteId);

    Optional<Lead> findByMetaLeadId(String metaLeadId);
}
