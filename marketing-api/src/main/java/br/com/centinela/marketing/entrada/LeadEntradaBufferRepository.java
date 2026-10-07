package br.com.centinela.marketing.entrada;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadEntradaBufferRepository extends JpaRepository<LeadEntradaBuffer, UUID> {

    boolean existsByEventoId(UUID eventoId);

    Page<LeadEntradaBuffer> findAllByOrderByCriadoEmDesc(Pageable pageable);
}