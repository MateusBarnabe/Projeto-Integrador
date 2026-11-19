-- D2: área de espera dos leads do Meta Ads (norteador §3, `leads_entrada_buffer`). O webhook responde na
-- hora e grava só os identificadores; o job 4.1 busca os dados na Graph API depois. O Landing não passa
-- por aqui: os eventos dele são processados na chegada. Sem dado pessoal: nome, e-mail e respostas vão
-- direto para `leads`.

CREATE TABLE marketing.leads_entrada_buffer (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    -- leadgen_id do webhook; a reentrega do mesmo lead não duplica a linha
    meta_lead_id      varchar(100) NOT NULL,
    -- Só leadgen_id, page_id, form_id e ad_id, como o Meta mandou
    payload_bruto     jsonb NOT NULL,
    processado        boolean NOT NULL DEFAULT false,
    processado_em     timestamptz,
    -- Falhas do job 4.1 (busca na Graph API ou gravação do lead)
    tentativas        int NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    -- Marcado depois do limite de tentativas, para investigação manual
    falha_permanente  boolean NOT NULL DEFAULT false,

    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    deleted_at        timestamptz,
    created_by        uuid,
    updated_by        uuid,

    CONSTRAINT uk_leads_entrada_buffer_tenant_meta_lead UNIQUE (tenant_id, meta_lead_id)
);

CREATE INDEX ix_leads_entrada_buffer_tenant ON marketing.leads_entrada_buffer (tenant_id);

-- O que o job 4.1 puxa: as linhas que ainda esperam, da mais antiga para a mais nova
CREATE INDEX ix_leads_entrada_buffer_pendentes ON marketing.leads_entrada_buffer (tenant_id, created_at)
    WHERE processado = false AND falha_permanente = false AND deleted_at IS NULL;
