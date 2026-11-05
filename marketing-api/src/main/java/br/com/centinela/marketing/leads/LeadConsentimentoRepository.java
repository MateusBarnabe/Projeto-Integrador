package br.com.centinela.marketing.leads;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Histórico de consentimentos do lead, do mais recente para o mais antigo. */
public interface LeadConsentimentoRepository extends JpaRepository<LeadConsentimento, UUID> {

    List<LeadConsentimento> findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(UUID leadId);

    Optional<LeadConsentimento> findFirstByLeadIdOrderByRegistradoEmDescCriadoEmDesc(UUID leadId);
}
