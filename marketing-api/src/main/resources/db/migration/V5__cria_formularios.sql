-- D4: definição dos campos do formulário da landing page, com versões imutáveis (norteador §3 e §9.5).
-- O id de formularios é o formularioId que o Landing manda nos eventos.

CREATE TABLE marketing.formularios (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    nome          varchar(200) NOT NULL,
    -- Versão que o Landing mostra; nula enquanto o formulário nunca foi publicado
    versao_ativa  int CHECK (versao_ativa >= 1),
    ativo         boolean NOT NULL DEFAULT true,

    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    deleted_at    timestamptz,
    created_by    uuid,
    updated_by    uuid
);

CREATE INDEX ix_formularios_tenant ON marketing.formularios (tenant_id);


CREATE TABLE marketing.formulario_versoes (
    id             uuid PRIMARY KEY,
    tenant_id      uuid NOT NULL,
    formulario_id  uuid NOT NULL REFERENCES marketing.formularios (id),
    versao         int NOT NULL CHECK (versao >= 1),
    -- Array no formato do schema Campo (§9.5), sem normalizar enquanto os campos não estão fechados
    campos         jsonb NOT NULL CHECK (jsonb_typeof(campos) = 'array'),
    publicado_em   timestamptz NOT NULL,

    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    deleted_at     timestamptz,
    created_by     uuid,
    updated_by     uuid,

    CONSTRAINT uk_formulario_versoes_versao UNIQUE (formulario_id, versao)
);

CREATE INDEX ix_formulario_versoes_tenant ON marketing.formulario_versoes (tenant_id);


-- Versão publicada nunca muda: cada lead guarda a versão que respondeu (leads.formulario_versao).
-- Só as colunas de controle (updated_*, deleted_at) podem ser alteradas.
CREATE FUNCTION marketing.impede_alterar_versao_publicada() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.formulario_id IS DISTINCT FROM OLD.formulario_id
            OR NEW.versao IS DISTINCT FROM OLD.versao
            OR NEW.campos IS DISTINCT FROM OLD.campos
            OR NEW.publicado_em IS DISTINCT FROM OLD.publicado_em
            OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id THEN
        RAISE EXCEPTION 'A versão % do formulário % já foi publicada e não pode mudar', OLD.versao, OLD.formulario_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_formulario_versoes_imutavel
    BEFORE UPDATE ON marketing.formulario_versoes
    FOR EACH ROW EXECUTE FUNCTION marketing.impede_alterar_versao_publicada();
