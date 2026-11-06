-- Q2: leads "aguardando vendedor" (norteador §9.3 e job 4.4). O estado é derivado, sem coluna nova:
-- MQL (quente ou morno), sem vendedor, ativo e não anonimizado. O índice parcial serve à descoberta dos
-- tenants com pendentes e ao lote do job, que puxa os mais antigos com FOR UPDATE SKIP LOCKED.

CREATE INDEX ix_leads_aguardando_vendedor ON marketing.leads (tenant_id, qualificado_em, id)
    WHERE vendedor_id IS NULL
      AND faixa_qualificacao IN ('mql_quente', 'mql_morno')
      AND deleted_at IS NULL
      AND anonimizado = false;
