package br.com.centinela.marketing.rastreamento;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Configuração do rastreamento do tenant atual; há no máximo uma por tenant. */
public interface RastreamentoConfiguracaoRepository extends JpaRepository<RastreamentoConfiguracao, UUID> {

    Optional<RastreamentoConfiguracao> findFirstBy();
}
