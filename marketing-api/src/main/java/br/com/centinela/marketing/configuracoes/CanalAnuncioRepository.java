package br.com.centinela.marketing.configuracoes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CanalAnuncioRepository extends JpaRepository<CanalAnuncio, UUID> {

    Optional<CanalAnuncio> findByCanal(CanalAnuncioTipo canal);

    List<CanalAnuncio> findAllByOrderByCanalAsc();
}
