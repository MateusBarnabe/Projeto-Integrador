package br.com.centinela.marketing.leads;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Leads com o e-mail já normalizado (minúsculas, sem espaços), do mais antigo para o mais novo.
     * O {@code lower} usa o índice {@code ix_leads_tenant_email} e alcança e-mail gravado sem normalizar.
     */
    @Query("select l from Lead l where lower(l.email) = :email order by l.criadoEm asc, l.id asc")
    List<Lead> buscarPorEmail(@Param("email") String emailNormalizado);

    /** Leads com o telefone já normalizado (só dígitos, E.164 sem {@code +}), do mais antigo para o mais novo. */
    @Query("select l from Lead l where l.telefone = :telefone order by l.criadoEm asc, l.id asc")
    List<Lead> buscarPorTelefone(@Param("telefone") String telefoneNormalizado);
}
