package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Escala do rodízio do tenant atual. */
public interface VendedorRodizioRepository extends JpaRepository<VendedorRodizio, UUID> {

    Optional<VendedorRodizio> findByVendedorId(UUID vendedorId);

    List<VendedorRodizio> findByAtivoTrue();
}
