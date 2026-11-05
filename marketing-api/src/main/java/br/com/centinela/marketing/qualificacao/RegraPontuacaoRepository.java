package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Regras de pontuação do tenant atual. */
public interface RegraPontuacaoRepository extends JpaRepository<RegraPontuacao, UUID> {

    List<RegraPontuacao> findByAtivoTrue();

    /** Regras ativas de uma fonte, ex.: as de UTM, que valem para todo lead. */
    List<RegraPontuacao> findByAtivoTrueAndFonte(FonteRegra fonte);

    /** Regras ativas dos campos de um formulário do Landing, para pontuar um envio dele. */
    List<RegraPontuacao> findByAtivoTrueAndFormularioId(UUID formularioId);
}
