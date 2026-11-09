-- R1: rastreamento do clique no anúncio, o "lead 0" (norteador §3, §4.7 e §5, passo 0). O anúncio aponta
-- para um link de campanha do Marketing; o clique vira uma visita, com o contexto técnico do aparelho, e
-- o script de rastreamento na landing page manda o comportamento. Se o visitante enviar o formulário, a
-- visita é ligada ao lead na leitura do envio; se não seguir em frente no prazo do tenant, os dados da
-- visita são descartados (job 4.7). Visita não é lead: não tem contato e não entra no funil.

-- Uma linha por tenant
CREATE TABLE marketing.rastreamento_configuracoes (
    id                       uuid PRIMARY KEY,
    tenant_id                uuid NOT NULL,
    -- Desligado, o link de campanha só redireciona, sem registrar a visita
    ativo                    boolean NOT NULL DEFAULT true,
    -- Tempo sem conversão nem sinal do visitante até o descarte (de 1 minuto a 7 dias)
    prazo_conversao_minutos  int NOT NULL CHECK (prazo_conversao_minutos BETWEEN 1 AND 10080),

    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    deleted_at               timestamptz,
    created_by               uuid,
    updated_by               uuid,

    CONSTRAINT uk_rastreamento_configuracoes_tenant UNIQUE (tenant_id)
);


-- Destino do anúncio. O código é público (vai na URL do anúncio) e único na plataforma: é por ele que
-- a rota pública acha o tenant, sem parâmetro alterável
CREATE TABLE marketing.links_campanha (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    codigo        varchar(16) NOT NULL,
    nome          varchar(120) NOT NULL,
    -- Landing page do anúncio; só http(s), para a rota pública não virar redirecionamento aberto
    url_destino   varchar(2048) NOT NULL CHECK (url_destino ~ '^https?://'),
    utm_source    varchar(255),
    utm_medium    varchar(255),
    utm_campaign  varchar(255),
    utm_term      varchar(255),
    utm_content   varchar(255),
    ativo         boolean NOT NULL DEFAULT true,

    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    deleted_at    timestamptz,
    created_by    uuid,
    updated_by    uuid,

    CONSTRAINT uk_links_campanha_codigo UNIQUE (codigo)
);

CREATE INDEX ix_links_campanha_tenant ON marketing.links_campanha (tenant_id);


-- Um clique no anúncio. O id é o mkt_visita que segue na URL da landing page. Sem dado pessoal: nem IP
-- (só a localização aproximada que sai dele), nem User-Agent bruto, nem o que o visitante digitou
CREATE TABLE marketing.visitas_anuncio (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    link_id           uuid NOT NULL REFERENCES marketing.links_campanha (id),
    situacao          varchar(20) NOT NULL CHECK (situacao IN ('ativa', 'convertida', 'descartada')),
    clicado_em        timestamptz NOT NULL,
    ultimo_sinal_em   timestamptz,
    -- Último sinal (ou o clique) mais o prazo do tenant; passou disso ativa, o job 4.7 descarta
    expira_em         timestamptz NOT NULL,
    -- UTM do link no momento do clique, para as contagens não mudarem quando o link for editado
    utm_source        varchar(255),
    utm_medium        varchar(255),
    utm_campaign      varchar(255),
    utm_term          varchar(255),
    utm_content       varchar(255),
    -- {tipoDispositivo, sistema, navegador, localizacao {cidade, uf, pais}, idioma, fuso, tela}
    contexto_tecnico  jsonb,
    -- {tempoNaPaginaSegundos, profundidadeRolagem, elementosClicados[], paginasVisitadas[]}
    comportamento     jsonb,
    lead_id           uuid REFERENCES marketing.leads (id),
    convertida_em     timestamptz,
    -- No descarte, contexto_tecnico e comportamento são zerados; a linha fica para as contagens
    descartada_em     timestamptz,

    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    deleted_at        timestamptz,
    created_by        uuid,
    updated_by        uuid,

    CONSTRAINT ck_visitas_anuncio_conversao CHECK ((situacao = 'convertida') = (lead_id IS NOT NULL))
);

CREATE INDEX ix_visitas_anuncio_tenant ON marketing.visitas_anuncio (tenant_id, clicado_em DESC);
CREATE INDEX ix_visitas_anuncio_lead ON marketing.visitas_anuncio (lead_id) WHERE lead_id IS NOT NULL;
-- Job 4.7: as ativas vencidas
CREATE INDEX ix_visitas_anuncio_expiracao ON marketing.visitas_anuncio (expira_em)
    WHERE situacao = 'ativa' AND deleted_at IS NULL;


-- O lead guarda o contexto técnico e o comportamento da visita que converteu. As colunas não dependem
-- da fonte: hoje vêm da visita de anúncio; um pixel próprio ou o Landing podem preenchê-las depois
ALTER TABLE marketing.leads
    ADD COLUMN contexto_tecnico jsonb,
    ADD COLUMN comportamento jsonb;

-- Timeline: visita_vinculada (a visita do anúncio foi ligada ao lead na leitura do envio)
ALTER TABLE marketing.lead_eventos DROP CONSTRAINT ck_lead_eventos_tipo_evento;
ALTER TABLE marketing.lead_eventos ADD CONSTRAINT ck_lead_eventos_tipo_evento CHECK (tipo_evento IN (
    'etapa_alterada', 'respostas_lidas', 'comunicacao_enviada', 'consentimento_registrado',
    'qualificacao_alterada', 'vendedor_atribuido', 'handoff_crm', 'descadastrado', 'anonimizado',
    'leads_unidos', 'respostas_indisponiveis', 'visita_vinculada'));

-- Pontuação: fonte comportamento, sobre uma métrica da visita (tempo na página, rolagem, página
-- visitada, elemento clicado ou tipo de dispositivo)
ALTER TABLE marketing.regras_pontuacao
    ADD COLUMN campo_comportamento varchar(40) CHECK (campo_comportamento IN (
        'tempo_na_pagina', 'profundidade_rolagem', 'pagina_visitada', 'elemento_clicado', 'tipo_dispositivo'));

ALTER TABLE marketing.regras_pontuacao DROP CONSTRAINT regras_pontuacao_fonte_check;
ALTER TABLE marketing.regras_pontuacao ADD CONSTRAINT ck_regras_pontuacao_fonte
    CHECK (fonte IN ('campo_formulario', 'campo_meta', 'utm', 'comportamento'));

ALTER TABLE marketing.regras_pontuacao DROP CONSTRAINT ck_regras_pontuacao_alvo;
ALTER TABLE marketing.regras_pontuacao ADD CONSTRAINT ck_regras_pontuacao_alvo CHECK (
    (fonte = 'campo_formulario' AND formulario_id IS NOT NULL AND campo_id IS NOT NULL AND campo_meta IS NULL
        AND campo_comportamento IS NULL)
    OR (fonte = 'campo_meta' AND campo_meta IS NOT NULL AND formulario_id IS NULL AND campo_id IS NULL
        AND campo_comportamento IS NULL)
    OR (fonte = 'utm' AND formulario_id IS NULL AND campo_id IS NULL AND campo_meta IS NULL
        AND campo_comportamento IS NULL)
    OR (fonte = 'comportamento' AND campo_comportamento IS NOT NULL AND formulario_id IS NULL AND campo_id IS NULL
        AND campo_meta IS NULL)
);
