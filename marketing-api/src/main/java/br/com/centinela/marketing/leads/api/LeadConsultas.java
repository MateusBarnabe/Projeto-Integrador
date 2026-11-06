package br.com.centinela.marketing.leads.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Session;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.centinela.marketing.compartilhado.web.Pagina;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadConsentimentoRepository;
import br.com.centinela.marketing.leads.LeadEventoRepository;
import br.com.centinela.marketing.leads.LeadRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Leituras de lead para a API (A2). Roda no tenant do contexto: o Hibernate filtra pelo
 * {@code @TenantId} e esconde o soft delete pelo {@code @SQLRestriction} de {@link Lead}, então lead de
 * outro tenant ou excluído simplesmente não existe aqui (vira {@code 404} no controller).
 */
@Service
@Transactional(readOnly = true)
public class LeadConsultas {

    /** Campos de {@code ordenar}, nome público → atributo de {@link Lead}; o primeiro é o padrão. */
    static final Map<String, String> CAMPOS_ORDENAVEIS;

    static {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("criadoEm", "criadoEm");
        campos.put("atualizadoEm", "atualizadoEm");
        campos.put("nome", "nome");
        campos.put("etapa", "etapa");
        campos.put("pontuacao", "pontuacao");
        campos.put("qualificadoEm", "qualificadoEm");
        CAMPOS_ORDENAVEIS = Collections.unmodifiableMap(campos);
    }

    static final int MAXIMO_BUSCA = 5;

    private final EntityManager em;
    private final LeadRepository leads;
    private final LeadConsentimentoRepository consentimentos;
    private final LeadEventoRepository eventos;

    public LeadConsultas(EntityManager em, LeadRepository leads, LeadConsentimentoRepository consentimentos,
            LeadEventoRepository eventos) {
        this.em = em;
        this.leads = leads;
        this.consentimentos = consentimentos;
        this.eventos = eventos;
    }

    public Pagina<ItemListaLead> listar(FiltroLeads filtro, PageRequest pedido) {
        HibernateCriteriaBuilder cb = criteria();

        CriteriaQuery<Long> contagem = cb.createQuery(Long.class);
        Root<Lead> raizContagem = contagem.from(Lead.class);
        contagem.select(cb.count(raizContagem)).where(predicados(cb, raizContagem, filtro));
        long total = em.createQuery(contagem).getSingleResult();

        List<ItemListaLead> itens = List.of();
        if (total > pedido.getOffset()) {
            CriteriaQuery<Lead> consulta = cb.createQuery(Lead.class);
            Root<Lead> lead = consulta.from(Lead.class);
            consulta.select(lead).where(predicados(cb, lead, filtro)).orderBy(ordem(cb, lead, pedido.getSort()));
            itens = em.createQuery(consulta)
                    .setFirstResult((int) pedido.getOffset())
                    .setMaxResults(pedido.getPageSize())
                    .getResultList().stream()
                    .map(ItemListaLead::de)
                    .toList();
        }
        return new Pagina<>(itens, pedido.getPageNumber(), pedido.getPageSize(), total);
    }

    public Optional<DetalheLead> detalhar(UUID id) {
        return leads.findById(id).map(lead -> DetalheLead.de(lead,
                consentimentos.findByLeadIdOrderByRegistradoEmDescCriadoEmDesc(id),
                eventos.findByLeadIdOrderBySequenciaAsc(id)));
    }

    public Optional<ResumoLead> resumir(UUID id) {
        return leads.findById(id).map(ResumoLead::de);
    }

    /** Resumos na ordem dos ids pedidos; id inexistente, excluído ou de outro tenant fica de fora. */
    public List<ResumoLead> resumir(Collection<UUID> ids) {
        Map<UUID, Lead> encontrados = leads.findAllById(ids).stream()
                .collect(Collectors.toMap(Lead::getId, Function.identity()));
        return ids.stream().map(encontrados::get).filter(Objects::nonNull).map(ResumoLead::de).toList();
    }

    /** Busca global (§8.6): nome ou e-mail, sem anonimizados, os 5 mais recentes. */
    public List<ResultadoBusca> buscar(String texto) {
        HibernateCriteriaBuilder cb = criteria();
        CriteriaQuery<Lead> consulta = cb.createQuery(Lead.class);
        Root<Lead> lead = consulta.from(Lead.class);
        consulta.select(lead)
                .where(cb.isFalse(lead.<Boolean>get("anonimizado")), contem(cb, lead, texto))
                .orderBy(cb.desc(lead.get("criadoEm")), cb.asc(lead.get("id")));
        return em.createQuery(consulta).setMaxResults(MAXIMO_BUSCA).getResultList().stream()
                .map(ResultadoBusca::de)
                .toList();
    }

    private Predicate[] predicados(HibernateCriteriaBuilder cb, Root<Lead> lead, FiltroLeads filtro) {
        List<Predicate> predicados = new ArrayList<>();
        if (!filtro.etapas().isEmpty()) {
            predicados.add(lead.get("etapa").in(filtro.etapas()));
        }
        if (!filtro.faixas().isEmpty()) {
            predicados.add(lead.get("faixaQualificacao").in(filtro.faixas()));
        }
        if (!filtro.origens().isEmpty()) {
            predicados.add(lead.get("origem").in(filtro.origens()));
        }
        if (!filtro.canais().isEmpty()) {
            predicados.add(lead.get("canal").in(filtro.canais()));
        }
        if (filtro.campanha() != null) {
            predicados.add(cb.equal(cb.lower(lead.<String>get("utmCampaign")), filtro.campanha().toLowerCase()));
        }
        if (filtro.vendedorId() != null) {
            predicados.add(cb.equal(lead.get("vendedorId"), filtro.vendedorId()));
        }
        if (filtro.periodo() != null) {
            predicados.add(cb.greaterThanOrEqualTo(lead.<OffsetDateTime>get("criadoEm"), filtro.periodo().de()));
            if (filtro.periodo().ate() != null) {
                predicados.add(cb.lessThan(lead.<OffsetDateTime>get("criadoEm"), filtro.periodo().ate()));
            }
        }
        if (filtro.texto() != null) {
            // Busca por dado pessoal nunca encontra lead anonimizado
            predicados.add(cb.isFalse(lead.<Boolean>get("anonimizado")));
            predicados.add(contem(cb, lead, filtro.texto()));
        }
        return predicados.toArray(Predicate[]::new);
    }

    /** Trecho do nome ou do e-mail, sem diferenciar maiúsculas; {@code %} e {@code _} valem como texto. */
    private static Predicate contem(HibernateCriteriaBuilder cb, Root<Lead> lead, String texto) {
        String padrao = "%" + texto.toLowerCase()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Expression<String> nome = cb.lower(lead.<String>get("nome"));
        Expression<String> email = cb.lower(lead.<String>get("email"));
        return cb.or(cb.like(nome, padrao, '\\'), cb.like(email, padrao, '\\'));
    }

    /** Ordem pedida com nulos por último (pontuação ainda não calculada) e o id para desempatar. */
    private static List<Order> ordem(HibernateCriteriaBuilder cb, Root<Lead> lead, Sort sort) {
        List<Order> ordem = new ArrayList<>();
        for (Sort.Order pedido : sort) {
            Expression<?> campo = lead.get(pedido.getProperty());
            ordem.add(pedido.isAscending() ? cb.asc(campo, false) : cb.desc(campo, false));
        }
        ordem.add(cb.asc(lead.get("id")));
        return ordem;
    }

    private HibernateCriteriaBuilder criteria() {
        return em.unwrap(Session.class).getCriteriaBuilder();
    }
}
