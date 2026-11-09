package br.com.centinela.marketing.rastreamento;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Visitas de anúncio do tenant atual. */
public interface VisitaAnuncioRepository extends JpaRepository<VisitaAnuncio, UUID> {

    Page<VisitaAnuncio> findBySituacaoIn(Collection<SituacaoVisita> situacoes, Pageable pagina);

    List<VisitaAnuncio> findByLeadIdOrderByClicadoEmDesc(UUID leadId);

    long countByLinkId(UUID linkId);

    /** Visitas desde a data, por situação: {@code [situacao, total]}. */
    @Query("SELECT v.situacao, count(v) FROM VisitaAnuncio v WHERE v.clicadoEm >= :desde GROUP BY v.situacao")
    List<Object[]> contarPorSituacao(OffsetDateTime desde);
}
