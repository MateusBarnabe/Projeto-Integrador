package br.com.centinela.marketing.formularios;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Versões publicadas dos formulários do tenant atual. */
public interface FormularioVersaoRepository extends JpaRepository<FormularioVersao, UUID> {

    Optional<FormularioVersao> findByFormularioIdAndVersao(UUID formularioId, int versao);

    List<FormularioVersao> findByFormularioIdOrderByVersaoAsc(UUID formularioId);
}
