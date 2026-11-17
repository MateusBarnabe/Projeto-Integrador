-- D6: preferências por canal de anúncio e credenciais do Meta Ads cifradas em repouso (norteador §3 e §9.4).
-- Os segredos do Meta são gravados cifrados e nunca devolvidos pela API.

CREATE TABLE marketing.canais_anuncio (
    id                      uuid PRIMARY KEY,
    tenant_id               uuid NOT NULL,
    canal                   varchar(20) NOT NULL CHECK (canal IN ('google_ads', 'meta_ads')),
    orcamento_mensal        numeric(15, 2) CHECK (orcamento_mensal IS NULL OR orcamento_mensal >= 0),
    -- {publicoAlvo, objetivo}
    configuracao            jsonb,
    -- Só na linha meta_ads; cifrados pela aplicação
    meta_app_secret         text,
    meta_verify_token       text,
    meta_page_id            varchar(120),
    meta_page_access_token  text,
    ativo                   boolean NOT NULL DEFAULT false,

    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    deleted_at              timestamptz,
    created_by              uuid,
    updated_by              uuid,

    CONSTRAINT uk_canais_anuncio_tenant_canal UNIQUE (tenant_id, canal),
    CONSTRAINT ck_canais_anuncio_meta CHECK (
        canal = 'meta_ads'
        OR (meta_app_secret IS NULL AND meta_verify_token IS NULL
            AND meta_page_id IS NULL AND meta_page_access_token IS NULL)
    )
);

CREATE INDEX ix_canais_anuncio_tenant ON marketing.canais_anuncio (tenant_id);
