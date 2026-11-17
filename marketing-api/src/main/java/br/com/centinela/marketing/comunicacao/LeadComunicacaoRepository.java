package br.com.centinela.marketing.comunicacao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadComunicacaoRepository extends JpaRepository<LeadComunicacao, UUID> {

    List<LeadComunicacao> findByLeadIdOrderByEnviadoEmDescCriadoEmDesc(UUID leadId);

    long countByLeadIdAndModeloIdAndStatus(UUID leadId, UUID modeloId, StatusComunicacao status);
}
