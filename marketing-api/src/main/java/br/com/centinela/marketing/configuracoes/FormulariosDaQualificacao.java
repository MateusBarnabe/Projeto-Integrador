package br.com.centinela.marketing.configuracoes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.centinela.marketing.compartilhado.http.ClientesDeModulos;
import br.com.centinela.marketing.compartilhado.http.FalhaNaChamadaDeModulo;
import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.CampoFormularioResumo;
import br.com.centinela.marketing.configuracoes.ConfiguracoesDtos.FormularioQualificacao;
import br.com.centinela.marketing.leads.Lead;
import br.com.centinela.marketing.leads.LeadRepository;

/**
 * Lista os formulários do Landing que já trouxeram lead (ou já estão em regras do tenant) e lê o
 * resumo de cada um em {@code GET /api/landing/formularios/{id}/resumo} (A3, norteador §6 e §9.4).
 *
 * <p>Se o Landing não responder em 3 s ou o formulário não for encontrado lá, devolve o que o
 * Marketing já conhece pelas respostas salvas em {@code leads.dados_formulario}, sem derrubar a tela
 * (Contrato §9.6).
 */
@Service
public class FormulariosDaQualificacao {

    static final String CAMINHO_RESUMO = "/api/landing/formularios/{id}/resumo";

    private static final Logger LOG = LoggerFactory.getLogger(FormulariosDaQualificacao.class);

    private final JdbcTemplate jdbc;
    private final LeadRepository leads;
    private final ClientesDeModulos clientes;

    public FormulariosDaQualificacao(JdbcTemplate jdbc, LeadRepository leads, ClientesDeModulos clientes) {
        this.jdbc = jdbc;
        this.leads = leads;
        this.clientes = clientes;
    }

    record ResumoDoLanding(UUID id, String nome, String status, List<CampoDoLanding> campos) {
    }

    record CampoDoLanding(UUID id, Integer ordem, String rotulo, String tipo, Boolean obrigatorio,
            List<String> opcoes) {
    }

    public List<FormularioQualificacao> listar() {
        UUID tenant = TenantContexto.exigir();
        Set<UUID> ids = new LinkedHashSet<>();
        ids.addAll(jdbc.queryForList(
                "SELECT DISTINCT formulario_id FROM marketing.leads "
                        + "WHERE tenant_id = ? AND deleted_at IS NULL AND formulario_id IS NOT NULL",
                UUID.class, tenant));
        ids.addAll(jdbc.queryForList(
                "SELECT DISTINCT formulario_id FROM marketing.regras_pontuacao "
                        + "WHERE tenant_id = ? AND deleted_at IS NULL AND formulario_id IS NOT NULL",
                UUID.class, tenant));

        List<FormularioQualificacao> resultado = new ArrayList<>();
        for (UUID formularioId : ids) {
            resultado.add(lerFormulario(tenant, formularioId));
        }
        return resultado;
    }

    private FormularioQualificacao lerFormulario(UUID tenant, UUID formularioId) {
        try {
            ResumoDoLanding resumo = clientes.landing().get(tenant, CAMINHO_RESUMO, ResumoDoLanding.class, formularioId);
            if (resumo != null) {
                List<CampoFormularioResumo> campos = resumo.campos() == null ? List.of()
                        : resumo.campos().stream()
                                .map(c -> new CampoFormularioResumo(c.id(), c.rotulo(), c.tipo(),
                                        Boolean.TRUE.equals(c.obrigatorio()),
                                        c.opcoes() == null ? List.of() : c.opcoes()))
                                .toList();
                return new FormularioQualificacao(
                        resumo.id() != null ? resumo.id() : formularioId,
                        resumo.nome() != null ? resumo.nome() : "Formulário " + curto(formularioId),
                        resumo.status() != null ? resumo.status() : "ATIVO",
                        true,
                        campos);
            }
        } catch (FalhaNaChamadaDeModulo falha) {
            LOG.warn("Resumo do formulário {} no Landing indisponível ({}); usando campos conhecidos nos leads",
                    formularioId, falha.motivo());
        }
        return fallbackDosLeads(formularioId);
    }

    private FormularioQualificacao fallbackDosLeads(UUID formularioId) {
        Map<UUID, CampoFormularioResumo> camposConhecidos = new LinkedHashMap<>();
        for (Lead lead : leads.findAll()) {
            if (!formularioId.equals(lead.getFormularioId()) || lead.getDadosFormulario() == null) {
                continue;
            }
            for (Map<String, Object> item : lead.getDadosFormulario()) {
                UUID campoId = uuid(item.get("campoId"));
                if (campoId != null && !camposConhecidos.containsKey(campoId)) {
                    String rotulo = texto(item.get("rotulo"), "Campo " + curto(campoId));
                    String tipo = texto(item.get("tipo"), "TEXTO");
                    camposConhecidos.put(campoId, new CampoFormularioResumo(campoId, rotulo, tipo, false, List.of()));
                }
            }
        }
        return new FormularioQualificacao(
                formularioId,
                "Formulário " + curto(formularioId),
                "INDISPONIVEL",
                false,
                List.copyOf(camposConhecidos.values()));
    }

    private static UUID uuid(Object valor) {
        if (valor instanceof UUID id) {
            return id;
        }
        if (valor instanceof String texto) {
            try {
                return UUID.fromString(texto.strip());
            } catch (IllegalArgumentException ignorado) {
                return null;
            }
        }
        return null;
    }

    private static String texto(Object valor, String padrao) {
        return valor instanceof String s && !s.isBlank() ? s.strip() : padrao;
    }

    private static String curto(UUID id) {
        return id.toString().substring(0, 8);
    }
}
