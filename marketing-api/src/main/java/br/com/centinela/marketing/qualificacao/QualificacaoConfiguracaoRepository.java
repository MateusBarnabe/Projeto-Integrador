package br.com.centinela.marketing.qualificacao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Configuração de qualificação do tenant atual; há no máximo uma por tenant. */
public interface QualificacaoConfiguracaoRepository extends JpaRepository<QualificacaoConfiguracao, UUID> {

    Optional<QualificacaoConfiguracao> findFirstBy();
}
