-- E5/E11: leitura do envio do Landing na chegada de landing.formulario.recebido e job 4.6 (norteador
-- §4.6 e §5, passo 3). Um lead com envio_id e envio_lido_em nulo está com a leitura pendente; o job
-- conta as falhas seguidas e desiste na décima, para o detalhe mostrar "respostas indisponíveis".

ALTER TABLE marketing.leads
    -- Falhas seguidas ao ler GET /api/landing/envios/{id}; volta a 0 na leitura e num envio novo
    ADD COLUMN leitura_envio_falhas smallint NOT NULL DEFAULT 0 CHECK (leitura_envio_falhas >= 0),
    -- Quando o job 4.6 desistiu de ler o envio (10 falhas seguidas)
    ADD COLUMN leitura_envio_desistida_em timestamptz;

-- O índice da V8 não sabia da desistência; este serve à descoberta dos tenants e ao lote do job
DROP INDEX marketing.ix_leads_envio_pendente;
CREATE INDEX ix_leads_leitura_envio_pendente ON marketing.leads (tenant_id, leitura_envio_falhas, created_at, id)
    WHERE envio_id IS NOT NULL
      AND envio_lido_em IS NULL
      AND leitura_envio_desistida_em IS NULL
      AND deleted_at IS NULL
      AND anonimizado = false;

-- Timeline: leads_unidos (merge de duplicados de mesmo e-mail ou telefone, §5 passo 3.4) e
-- respostas_indisponiveis (o job 4.6 desistiu de ler o envio)
ALTER TABLE marketing.lead_eventos DROP CONSTRAINT ck_lead_eventos_tipo_evento;
ALTER TABLE marketing.lead_eventos ADD CONSTRAINT ck_lead_eventos_tipo_evento CHECK (tipo_evento IN (
    'etapa_alterada', 'respostas_lidas', 'comunicacao_enviada', 'consentimento_registrado',
    'qualificacao_alterada', 'vendedor_atribuido', 'handoff_crm', 'descadastrado', 'anonimizado',
    'leads_unidos', 'respostas_indisponiveis'));
