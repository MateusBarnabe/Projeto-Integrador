package br.com.centinela.marketing.comunicacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloMensagemRepository extends JpaRepository<ModeloMensagem, UUID> {

    Optional<ModeloMensagem> findFirstByTipoAndAtivoTrue(TipoModeloMensagem tipo);

    Page<ModeloMensagem> findByTipo(TipoModeloMensagem tipo, Pageable pageable);

    List<ModeloMensagem> findByTipoAndAtivoTrueOrderByOrdemSequenciaAscCriadoEmAsc(TipoModeloMensagem tipo);
}
