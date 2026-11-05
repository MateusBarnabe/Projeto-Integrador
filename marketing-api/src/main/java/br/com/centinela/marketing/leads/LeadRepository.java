package br.com.centinela.marketing.leads;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Consultas de {@link Lead}. O filtro por tenant e por soft delete é aplicado pelo Hibernate.
 * As buscas por identificador seguem a ordem de resolução de identidade do norteador §5.
 */
public interface LeadRepository extends JpaRepository<Lead, UUID> {

    Optional<Lead> findByMetaLeadId(String metaLeadId);

    /** Captura do chatbot: a mesma conversa traz o mesmo {@code sessaoId}. */
    Optional<Lead> findBySessaoId(UUID sessaoId);

    /** Envio do Landing; é também o {@code origemModuloId} esperado em {@code crm.oportunidade.criada}. */
    Optional<Lead> findByEnvioId(UUID envioId);

    /** Contato do CRM, que o Landing já deduplica; vale o lead mais antigo quando houver mais de um. */
    Optional<Lead> findFirstByCrmContatoIdOrderByCriadoEmAsc(UUID crmContatoId);
}
