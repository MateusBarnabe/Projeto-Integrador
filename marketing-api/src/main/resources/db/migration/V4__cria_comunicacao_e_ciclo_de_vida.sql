-- D3: modelos de mensagem, comunicações enviadas ao lead e configuração do ciclo de vida (norteador §3, §4.2, §4.3, §4.5).
-- Também permite zerar token_descadastro quando o lead é anonimizado (E10, §4.3).

CREATE TABLE marketing.modelos_mensagem (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    tipo                varchar(20) NOT NULL CHECK (tipo IN ('boas_vindas', 'aquecimento')),
    nome                varchar(120) NOT NULL,
    canal               varchar(20) NOT NULL DEFAULT 'email' CHECK (canal IN ('email')),
    -- Só em aquecimento: 0 ou 1; nulo vale para qualquer etapa fria
    etapa_alvo          smallint CHECK (etapa_alvo IN (0, 1)),
    -- Posição na cadência (1º toque, 2º toque...) e dias de espera desde o toque anterior
    ordem_sequencia     int CHECK (ordem_sequencia > 0),
    dias_apos_anterior  int CHECK (dias_apos_anterior >= 0),
    assunto             varchar(255) NOT NULL,
    corpo               text NOT NULL,
    ativo               boolean NOT NULL DEFAULT true,

    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    deleted_at          timestamptz,
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT ck_modelos_mensagem_cadencia CHECK (
        (tipo = 'boas_vindas' AND etapa_alvo IS NULL AND ordem_sequencia IS NULL AND dias_apos_anterior IS NULL)
        OR (tipo = 'aquecimento' AND ordem_sequencia IS NOT NULL AND dias_apos_anterior IS NOT NULL)
    )
);

CREATE INDEX ix_modelos_mensagem_tenant ON marketing.modelos_mensagem (tenant_id);
-- Só um modelo boas_vindas ativo por tenant
CREATE UNIQUE INDEX uk_modelos_mensagem_boas_vindas_ativo
    ON marketing.modelos_mensagem (tenant_id)
    WHERE tipo = 'boas_vindas' AND ativo AND deleted_at IS NULL;


CREATE TABLE marketing.lead_comunicacoes (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    lead_id     uuid NOT NULL REFERENCES marketing.leads (id),
    canal       varchar(20) NOT NULL DEFAULT 'email' CHECK (canal IN ('email')),
    modelo_id   uuid NOT NULL REFERENCES marketing.modelos_mensagem (id),
    status      varchar(20) NOT NULL CHECK (status IN ('enviado', 'entregue', 'aberto', 'clicado', 'falhou')),
    enviado_em  timestamptz NOT NULL,

    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    deleted_at  timestamptz,
    created_by  uuid,
    updated_by  uuid
);

CREATE INDEX ix_lead_comunicacoes_tenant ON marketing.lead_comunicacoes (tenant_id);
CREATE INDEX ix_lead_comunicacoes_lead ON marketing.lead_comunicacoes (lead_id, enviado_em DESC);


-- Uma linha por tenant
CREATE TABLE marketing.ciclo_vida_configuracoes (
    id                              uuid PRIMARY KEY,
    tenant_id                       uuid NOT NULL,
    max_tentativas_aquecimento      int NOT NULL CHECK (max_tentativas_aquecimento > 0),
    meses_retencao_pos_esgotamento  int NOT NULL CHECK (meses_retencao_pos_esgotamento > 0),
    meses_retencao_pos_optout       int NOT NULL CHECK (meses_retencao_pos_optout > 0),

    created_at                      timestamptz NOT NULL,
    updated_at                      timestamptz NOT NULL,
    deleted_at                      timestamptz,
    created_by                      uuid,
    updated_by                      uuid,

    CONSTRAINT uk_ciclo_vida_configuracoes_tenant UNIQUE (tenant_id)
);


-- Na anonimização (job 4.3, E10), o token_descadastro é zerado; enquanto não anonimizado, continua obrigatório
ALTER TABLE marketing.leads
    ALTER COLUMN token_descadastro DROP NOT NULL,
    ADD CONSTRAINT ck_leads_token_descadastro CHECK (anonimizado OR token_descadastro IS NOT NULL);
