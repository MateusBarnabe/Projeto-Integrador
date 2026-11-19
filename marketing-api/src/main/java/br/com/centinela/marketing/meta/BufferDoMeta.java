package br.com.centinela.marketing.meta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.centinela.marketing.compartilhado.tenant.TenantContexto;

/**
 * SQL do buffer dos leads do Meta que o filtro de tenant do Hibernate não cobre: a gravação do webhook
 * ({@code ON CONFLICT}, para a reentrega concorrente não duplicar nem falhar) e as travas do job 4.1
 * ({@code FOR UPDATE SKIP LOCKED}). Toda consulta filtra {@code tenant_id} à mão.
 */
@Component
class BufferDoMeta {

    /** O que ainda espera o job: não processado, sem falha permanente e não excluído. */
    static final String PENDENTE = "processado = false AND falha_permanente = false AND deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    BufferDoMeta(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * Grava o {@code leadgen_id} no buffer do tenant do contexto.
     *
     * @return {@code true} se a linha é nova; {@code false} se o Meta reentregou um lead que já está lá
     */
    boolean gravar(String leadgenId, Map<String, Object> payloadBruto) {
        String payload;
        try {
            payload = json.writeValueAsString(payloadBruto);
        } catch (JsonProcessingException impossivel) {
            throw new IllegalArgumentException("Payload do webhook não pôde ser convertido em JSON.", impossivel);
        }
        return jdbc.update("""
                INSERT INTO marketing.leads_entrada_buffer
                       (id, tenant_id, meta_lead_id, payload_bruto, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, now(), now())
                ON CONFLICT (tenant_id, meta_lead_id) DO NOTHING
                """, UUID.randomUUID(), TenantContexto.exigir(), leadgenId, payload) > 0;
    }

    /** Tenants com linha esperando o job; ele percorre só estes. */
    List<UUID> tenantsComPendentes() {
        return jdbc.queryForList("SELECT DISTINCT tenant_id FROM marketing.leads_entrada_buffer WHERE " + PENDENTE,
                UUID.class);
    }

    /** Até {@code limite} linhas pendentes do tenant, as mais antigas primeiro, sem travar. */
    List<UUID> pendentes(int limite) {
        return jdbc.queryForList("SELECT id FROM marketing.leads_entrada_buffer WHERE tenant_id = ? AND "
                + PENDENTE + " ORDER BY created_at, id LIMIT ?", UUID.class, TenantContexto.exigir(), limite);
    }

    /**
     * Trava a linha se ela ainda está pendente e ninguém está com ela (outra execução ou outra
     * instância): {@code FOR UPDATE SKIP LOCKED}, até o fim da transação.
     */
    boolean travarPendente(UUID id) {
        return !jdbc.queryForList("SELECT id FROM marketing.leads_entrada_buffer WHERE id = ? AND tenant_id = ? "
                + "AND " + PENDENTE + " FOR UPDATE SKIP LOCKED", UUID.class, id, TenantContexto.exigir())
                .isEmpty();
    }
}
