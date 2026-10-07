CREATE TABLE marketing.leads_entrada_buffer (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    deleted_at            timestamptz,
    created_by            uuid,
    updated_by            uuid,
    evento_id             uuid NOT NULL UNIQUE,
    tipo                  varchar(120) NOT NULL,
    visitante_id          uuid,
    nome                  varchar(200) NOT NULL,
    email                 varchar(320) NOT NULL,
    whatsapp              varchar(30),
    telefone              varchar(30),
    utm_source            varchar(120),
    utm_medium            varchar(120),
    utm_campaign          varchar(200),
    utm_content           varchar(200),
    referrer              text,
    dados_tecnicos        jsonb,
    dados_comportamentais jsonb,
    processado            boolean NOT NULL DEFAULT false
);

CREATE INDEX ix_leads_entrada_buffer_tenant
    ON marketing.leads_entrada_buffer (tenant_id);

CREATE INDEX ix_leads_entrada_buffer_created
    ON marketing.leads_entrada_buffer (created_at DESC);