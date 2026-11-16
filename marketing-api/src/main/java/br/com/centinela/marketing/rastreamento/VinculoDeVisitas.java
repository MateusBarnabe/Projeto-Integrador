package br.com.centinela.marketing.rastreamento;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadEvento;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import br.com.centinela.marketing.leads.TipoEventoLead;
import jakarta.persistence.EntityManager;

/**
 * Liga as visitas do anúncio ao lead que enviou o formulário (R1, norteador §5, passo 3). Chamado na
 * leitura do envio (E5 e job 4.6), antes da pontuação, com as páginas de entrada da primeira e da
 * última origem do envio: o Landing guarda a URL inteira, e nela está o {@code mkt_visita} que o
 * clique acrescentou.
 *
 * <p>Cada visita ativa encontrada vira {@code convertida} e registra {@code visita_vinculada} na
 * timeline. O lead fica com o contexto técnico e o comportamento da visita mais recente, que as regras
 * de {@code comportamento} pontuam em seguida. Visita já convertida, descartada ou de outro tenant é
 * ignorada. Roda na transação e no tenant de quem chama, com o lead já travado.
 */
@Service
public class VinculoDeVisitas {

    private static final Logger LOG = LoggerFactory.getLogger(VinculoDeVisitas.class);

    private final AcessoPublico acesso;
    private final VisitaAnuncioRepository visitas;
    private final LeadRepository leads;
    private final LeadEventoRepository eventos;
    private final EntityManager entityManager;

    VinculoDeVisitas(AcessoPublico acesso, VisitaAnuncioRepository visitas, LeadRepository leads,
            LeadEventoRepository eventos, EntityManager entityManager) {
        this.acesso = acesso;
        this.visitas = visitas;
        this.leads = leads;
        this.eventos = eventos;
        this.entityManager = entityManager;
    }

    /**
     * @param paginasDeEntrada URLs de entrada do envio (a última origem primeiro); nulas são ignoradas
     * @param fonte            o que disparou o vínculo, para a timeline ({@code landing.formulario.recebido})
     * @param eventoId         id do evento consumido; nulo numa rotina
     * @return as visitas ligadas agora
     */
    @Transactional
    public List<VisitaAnuncio> vincular(Lead lead, List<String> paginasDeEntrada, String fonte, UUID eventoId) {
        Set<UUID> ids = idsDeVisita(paginasDeEntrada);
        if (ids.isEmpty() || lead.getId() == null) {
            return List.of();
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<VisitaAnuncio> ligadas = new ArrayList<>();
        for (UUID id : ids) {
            if (!acesso.travarAtiva(id)) {
                continue;
            }
            VisitaAnuncio visita = visitas.findById(id).orElse(null);
            if (visita == null) {
                continue;
            }
            entityManager.refresh(visita);
            visita.converter(lead.getId(), agora);
            visitas.save(visita);
            eventos.save(new LeadEvento(lead.getId(), TipoEventoLead.VISITA_VINCULADA,
                    payload(visita, fonte, eventoId)));
            ligadas.add(visita);
        }
        ligadas.stream().max(Comparator.comparing(VisitaAnuncio::getClicadoEm)).ifPresent(maisRecente -> {
            lead.registrarInteracao(maisRecente.getContextoTecnico(), maisRecente.getComportamento());
            leads.save(lead);
            LOG.info("Lead {} ligado a {} visita(s) de anúncio", lead.getId(), ligadas.size());
        });
        return ligadas;
    }

    /** {@code mkt_visita} de cada URL, sem repetir, na ordem recebida. */
    static Set<UUID> idsDeVisita(List<String> urls) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (urls == null) {
            return ids;
        }
        for (String url : urls) {
            String consulta = consulta(url);
            if (consulta == null) {
                continue;
            }
            for (String par : consulta.split("&")) {
                String[] chaveValor = par.split("=", 2);
                if (chaveValor.length == 2 && LinkCampanha.PARAMETRO_VISITA.equals(chaveValor[0])) {
                    try {
                        ids.add(UUID.fromString(chaveValor[1].strip()));
                    } catch (IllegalArgumentException naoEhUuid) {
                        // valor adulterado: não há visita a ligar
                    }
                }
            }
        }
        return ids;
    }

    private static String consulta(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url.strip()).getRawQuery();
        } catch (IllegalArgumentException urlInvalida) {
            int inicio = url.indexOf('?');
            return inicio < 0 ? null : url.substring(inicio + 1).split("#", 2)[0];
        }
    }

    // Sem dado pessoal: ids, campanha e o resumo da interação
    private static Map<String, Object> payload(VisitaAnuncio visita, String fonte, UUID eventoId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fonte", fonte);
        if (eventoId != null) {
            payload.put("eventoId", eventoId.toString());
        }
        payload.put("visitaId", visita.getId().toString());
        payload.put("linkId", visita.getLinkId().toString());
        payload.put("utmCampaign", visita.getUtmCampaign());
        payload.put("clicadoEm", visita.getClicadoEm().toString());
        Map<String, Object> contexto = visita.getContextoTecnico();
        payload.put("tipoDispositivo", contexto == null ? null : contexto.get("tipoDispositivo"));
        Map<String, Object> comportamento = visita.getComportamento();
        payload.put("tempoNaPaginaSegundos",
                comportamento == null ? null : comportamento.get("tempoNaPaginaSegundos"));
        return payload;
    }
}
