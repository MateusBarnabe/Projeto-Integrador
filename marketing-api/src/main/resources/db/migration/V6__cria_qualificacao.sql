-- D5: regras de pontuação, faixas de qualificação e escala do rodízio (norteador §3 e §9.3).
-- Os valores iniciais por tenant entram na D7, na primeira vez que o tenant usa o módulo.

CREATE TABLE marketing.regras_pontuacao (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    -- Nome amigável; cada critério conta uma vez por lead, valendo a regra de mais pontos
    criterio     varchar(100) NOT NULL,
    fonte        varchar(20) NOT NULL CHECK (fonte IN ('campo_formulario', 'utm')),
    -- chave do campo (§9.5), a mesma em todos os formulários do tenant
    campo_chave  varchar(100),
    -- Respostas que disparam a regra; em utm, objetos como {"source": "google", "medium": "cpc"}
    valores      jsonb NOT NULL CHECK (jsonb_typeof(valores) = 'array'),
    pontos       int NOT NULL,
    ativo        boolean NOT NULL DEFAULT true,

    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    deleted_at   timestamptz,
    created_by   uuid,
    updated_by   uuid,

    CONSTRAINT ck_regras_pontuacao_campo CHECK (fonte <> 'campo_formulario' OR campo_chave IS NOT NULL)
);

CREATE INDEX ix_regras_pontuacao_tenant ON marketing.regras_pontuacao (tenant_id);


-- Uma linha por tenant
CREATE TABLE marketing.qualificacao_configuracoes (
    id                        uuid PRIMARY KEY,
    tenant_id                 uuid NOT NULL,
    limite_mql_quente         int NOT NULL,
    -- Abaixo dele, a faixa é nutricao
    limite_mql_morno          int NOT NULL,
    rodizio_ativo             boolean NOT NULL,
    -- No fuso do tenant
    horario_comercial_inicio  time NOT NULL,
    horario_comercial_fim     time NOT NULL,
    -- 1 (segunda) a 7 (domingo)
    dias_atendimento          smallint[] NOT NULL,
    minutos_transbordo        int CHECK (minutos_transbordo > 0),

    created_at                timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL,
    deleted_at                timestamptz,
    created_by                uuid,
    updated_by                uuid,

    CONSTRAINT uk_qualificacao_configuracoes_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_qualificacao_configuracoes_limites CHECK (limite_mql_morno < limite_mql_quente),
    CONSTRAINT ck_qualificacao_configuracoes_horario CHECK (horario_comercial_inicio < horario_comercial_fim),
    CONSTRAINT ck_qualificacao_configuracoes_dias CHECK (dias_atendimento <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[])
);


CREATE TABLE marketing.vendedores_rodizio (
    id                    uuid PRIMARY KEY,
    tenant_id             uuid NOT NULL,
    -- Usuário do identity com perfil VENDEDOR; só o UUID
    vendedor_id           uuid NOT NULL,
    ativo                 boolean NOT NULL DEFAULT true,
    -- O rodízio escolhe o ativo com a atribuição mais antiga
    ultima_atribuicao_em  timestamptz,

    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    deleted_at            timestamptz,
    created_by            uuid,
    updated_by            uuid,

    CONSTRAINT uk_vendedores_rodizio_vendedor UNIQUE (tenant_id, vendedor_id)
);

CREATE INDEX ix_vendedores_rodizio_tenant ON marketing.vendedores_rodizio (tenant_id);
