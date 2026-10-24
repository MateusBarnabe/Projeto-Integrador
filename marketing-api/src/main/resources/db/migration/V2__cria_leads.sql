-- D1: registro central do lead, timeline e evidência de consentimento (norteador §3).
-- As três tabelas têm as sete colunas obrigatórias (§7.2); created_by e updated_by ficam nulos
-- quando a gravação vem de rotina automática ou rota pública.

CREATE TABLE marketing.leads (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,

    etapa                   smallint NOT NULL CHECK (etapa BETWEEN 0 AND 3),
    -- Espelho da etapa, sem override manual por ora (§2)
    temperatura             varchar(10) GENERATED ALWAYS AS (
                                CASE
                                    WHEN etapa IN (0, 1) THEN 'frio'
                                    WHEN etapa = 2 THEN 'medio'
                                    WHEN etapa = 3 THEN 'quente'
                                END
                            ) STORED,
    origem                  varchar(20) NOT NULL CHECK (origem IN ('meta_ads', 'landing_page', 'manual')),

    nome                    varchar(200),
    email                   varchar(320),
    telefone                varchar(30),
    whatsapp                varchar(30),

    utm_source              varchar(255),
    utm_medium              varchar(255),
    utm_campaign            varchar(255),
    utm_term                varchar(255),
    utm_content             varchar(255),
    landing_page_url        varchar(2048),
    first_touch             timestamptz,
    last_touch              timestamptz,

    visitante_id            uuid,
    envio_id                uuid,
    -- Sem FK para formularios: um formulário desconhecido não pode travar a gravação do lead
    formulario_id           uuid,
    formulario_versao       int,
    dados_formulario        jsonb,

    consentimento_marketing boolean NOT NULL DEFAULT false,
    consentimento_em        timestamptz,
    boas_vindas_enviada_em  timestamptz,

    meta_lead_id            varchar(100),
    -- Só o UUID de outro módulo, nunca FK entre schemas (§7.1)
    crm_empresa_id          uuid,
    crm_contato_id          uuid,
    crm_oportunidade_id     uuid,

    pontuacao               int,
    faixa_qualificacao      varchar(20) CHECK (faixa_qualificacao IN ('mql_quente', 'mql_morno', 'nutricao')),
    pontuacao_detalhe       jsonb,
    qualificado_em          timestamptz,
    vendedor_id             uuid,
    vendedor_atribuido_em   timestamptz,

    ultima_comunicacao_em   timestamptz,
    tentativas_aquecimento  int NOT NULL DEFAULT 0,
    status_aquecimento      varchar(20) NOT NULL DEFAULT 'ativo'
                            CHECK (status_aquecimento IN ('ativo', 'esgotado', 'opt_out')),
    esgotado_em             timestamptz,
    opt_out_em              timestamptz,
    token_descadastro       varchar(64) NOT NULL,
    anonimizado             boolean NOT NULL DEFAULT false,

    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    deleted_at              timestamptz,
    created_by              uuid,
    updated_by              uuid,

    -- A unicidade é por tenant; vários nulos são permitidos (lead do Meta não tem visitante_id)
    CONSTRAINT uk_leads_tenant_visitante UNIQUE (tenant_id, visitante_id),
    CONSTRAINT uk_leads_tenant_meta_lead UNIQUE (tenant_id, meta_lead_id),
    CONSTRAINT uk_leads_token_descadastro UNIQUE (token_descadastro)
);

CREATE INDEX ix_leads_tenant ON marketing.leads (tenant_id);
CREATE INDEX ix_leads_tenant_email ON marketing.leads (tenant_id, lower(email));
CREATE INDEX ix_leads_tenant_telefone ON marketing.leads (tenant_id, telefone);
CREATE INDEX ix_leads_tenant_criado ON marketing.leads (tenant_id, created_at DESC);


CREATE TABLE marketing.lead_eventos (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    -- Ordem global, usada como id das mensagens SSE do painel (§9.1)
    sequencia     bigint GENERATED ALWAYS AS IDENTITY,
    lead_id       uuid NOT NULL REFERENCES marketing.leads (id),
    tipo_evento   varchar(40) NOT NULL CHECK (tipo_evento IN (
                      'etapa_alterada', 'formulario_atualizado', 'comunicacao_enviada',
                      'consentimento_registrado', 'qualificacao_alterada', 'vendedor_atribuido',
                      'handoff_crm', 'descadastrado', 'anonimizado')),
    etapa_anterior smallint CHECK (etapa_anterior BETWEEN 0 AND 3),
    etapa_nova    smallint CHECK (etapa_nova BETWEEN 0 AND 3),
    payload       jsonb,

    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    deleted_at    timestamptz,
    created_by    uuid,
    updated_by    uuid,

    CONSTRAINT uk_lead_eventos_sequencia UNIQUE (sequencia)
);

CREATE INDEX ix_lead_eventos_tenant ON marketing.lead_eventos (tenant_id);
CREATE INDEX ix_lead_eventos_lead ON marketing.lead_eventos (lead_id, sequencia);


-- Só inclusão: uma linha por aceite ou revogação (LGPD 8.1, RC03). O estado atual fica em leads.
CREATE TABLE marketing.lead_consentimentos (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    lead_id           uuid NOT NULL REFERENCES marketing.leads (id),
    finalidade        varchar(40) NOT NULL,
    aceito            boolean NOT NULL,
    registrado_em     timestamptz NOT NULL,
    -- ip e user_agent são zerados na anonimização (4.3)
    ip                varchar(45),
    user_agent        varchar(500),
    versao_termo      varchar(40),
    origem            varchar(40) NOT NULL CHECK (origem IN (
                          'landing.contato.informado', 'landing.formulario.atualizado',
                          'landing.formulario.recebido', 'descadastro')),
    formulario_id     uuid,
    formulario_versao int,
    envio_id          uuid,
    evento_id         uuid,

    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    deleted_at        timestamptz,
    created_by        uuid,
    updated_by        uuid
);

CREATE INDEX ix_lead_consentimentos_tenant ON marketing.lead_consentimentos (tenant_id);
CREATE INDEX ix_lead_consentimentos_lead ON marketing.lead_consentimentos (lead_id, registrado_em DESC);
