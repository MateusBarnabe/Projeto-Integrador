package br.com.centinela.marketing.meta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Buffer de leads do Meta do tenant atual. */
public interface LeadEntradaBufferRepository extends JpaRepository<LeadEntradaBuffer, UUID> {

    Optional<LeadEntradaBuffer> findByMetaLeadId(String metaLeadId);

    List<LeadEntradaBuffer> findByProcessadoFalseAndFalhaPermanenteFalseOrderByCriadoEmAsc();
}
