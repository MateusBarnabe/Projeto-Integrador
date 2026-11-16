package br.com.centinela.marketing.rastreamento;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Links de campanha do tenant atual. */
public interface LinkCampanhaRepository extends JpaRepository<LinkCampanha, UUID> {
}
