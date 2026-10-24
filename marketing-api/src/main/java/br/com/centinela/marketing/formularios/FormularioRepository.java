package br.com.centinela.marketing.formularios;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Formulários do tenant atual. O filtro por tenant e por soft delete é aplicado pelo Hibernate. */
public interface FormularioRepository extends JpaRepository<Formulario, UUID> {
}
