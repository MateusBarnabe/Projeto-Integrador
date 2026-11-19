-- A4: fluxo SSE do painel. Cada conexão consulta lead_eventos do próprio tenant a cada poucos segundos,
-- "sequencia > último id enviado" (norteador §9.1). Só o índice único de sequencia não filtra por tenant:
-- este serve a consulta sem ler os fatos dos outros tenants.

CREATE INDEX ix_lead_eventos_tenant_sequencia ON marketing.lead_eventos (tenant_id, sequencia);
