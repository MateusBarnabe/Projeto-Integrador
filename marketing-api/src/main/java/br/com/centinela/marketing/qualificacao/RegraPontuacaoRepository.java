package br.com.centinela.marketing.qualificacao;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Regras de pontuação do tenant atual. */
public interface RegraPontuacaoRepository extends JpaRepository<RegraPontuacao, UUID> {

    List<RegraPontuacao> findByAtivoTrue();

    /** Usado para avisar antes de remover do formulário um campo que pontua (§9.5). */
    boolean existsByCampoChaveAndAtivoTrue(String campoChave);
}
