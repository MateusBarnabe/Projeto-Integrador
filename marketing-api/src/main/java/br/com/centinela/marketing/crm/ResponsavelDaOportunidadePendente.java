package br.com.centinela.marketing.crm;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação provisória de {@link ResponsavelDaOportunidade}: o CRM ainda não oferece a ação para
 * gravar só o responsável (X2, Grupo 6). Não chama nada; registra no log que o vendedor ficou sem ir
 * ao CRM e devolve {@link Situacao#PENDENTE}. A tarefa C1 substitui esta classe.
 */
@Component
public class ResponsavelDaOportunidadePendente implements ResponsavelDaOportunidade {

    private static final Logger LOG = LoggerFactory.getLogger(ResponsavelDaOportunidadePendente.class);

    @Override
    public Situacao levarVendedor(UUID oportunidadeId, UUID vendedorId) {
        LOG.info("Vendedor {} não levado como responsável da oportunidade {}: o CRM ainda não tem a ação (C1, X2)",
                vendedorId, oportunidadeId);
        return Situacao.PENDENTE;
    }
}
