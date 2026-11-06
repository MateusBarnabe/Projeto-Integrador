package br.com.centinela.marketing.notificacoes;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.leads.Lead;

/**
 * Quem recebe a notificação {@code NOVO_LEAD} quando o lead não tem vendedor (rodízio desligado,
 * fora do horário ou sem vendedor ativo). É a decisão em aberto X11 (norteador §11): por enquanto
 * ninguém é notificado e o log registra só o id do lead, sem dado pessoal. Quando o Grupo 4 decidir
 * (por exemplo, a pessoa gestora do tenant), a troca é só aqui.
 */
@Component
public class DestinatarioSemVendedor {

    private static final Logger LOG = LoggerFactory.getLogger(DestinatarioSemVendedor.class);

    /** Destinatário da notificação de um lead sem vendedor; vazio quando ninguém deve ser notificado. */
    public Optional<UUID> escolher(Lead lead) {
        LOG.info("Lead {} sem vendedor: notificação NOVO_LEAD não enviada (destinatário em aberto, X11)",
                lead.getId());
        return Optional.empty();
    }
}
