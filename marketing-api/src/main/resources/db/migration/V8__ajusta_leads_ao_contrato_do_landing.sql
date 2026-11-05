-- D8: ajusta as tabelas da V2 ao contrato publicado do Landing (norteador §3, revisão de 05/11/2026).
-- O Landing não tem visitante entre etapas nem versão de formulário. A captura do chatbot se liga ao
-- envio pela sessão, e as respostas, a origem e o consentimento do envio são lidos na API do Landing
-- depois do evento.

-- leads -----------------------------------------------------------------------------------------

ALTER TABLE marketing.leads DROP CONSTRAINT uk_leads_tenant_visitante;
ALTER TABLE marketing.leads DROP COLUMN visitante_id;
ALTER TABLE marketing.leads DROP COLUMN formulario_versao;

ALTER TABLE marketing.leads
    -- sessaoId do chatbot: liga a captura (etapa 1) ao envio da mesma conversa
    ADD COLUMN sessao_id uuid,
    -- Canal do envio no Landing; nulo em lead do Meta
    ADD COLUMN canal varchar(20) CHECK (canal IN ('formulario', 'landing_page', 'embed', 'chatbot')),
    -- Nulo com envio_id preenchido quer dizer leitura do envio pendente (job 4.6)
    ADD COLUMN envio_lido_em timestamptz,
    ADD CONSTRAINT uk_leads_tenant_sessao UNIQUE (tenant_id, sessao_id),
    ADD CONSTRAINT uk_leads_tenant_envio UNIQUE (tenant_id, envio_id);

CREATE INDEX ix_leads_envio_pendente ON marketing.leads (tenant_id)
    WHERE envio_id IS NOT NULL AND envio_lido_em IS NULL;
CREATE INDEX ix_leads_tenant_contato_crm ON marketing.leads (tenant_id, crm_contato_id);

-- lead_eventos ----------------------------------------------------------------------------------

-- respostas_lidas substitui formulario_atualizado, que vinha de um evento que o Landing não publica
ALTER TABLE marketing.lead_eventos DROP CONSTRAINT lead_eventos_tipo_evento_check;
UPDATE marketing.lead_eventos SET tipo_evento = 'respostas_lidas' WHERE tipo_evento = 'formulario_atualizado';
ALTER TABLE marketing.lead_eventos ADD CONSTRAINT ck_lead_eventos_tipo_evento CHECK (tipo_evento IN (
    'etapa_alterada', 'respostas_lidas', 'comunicacao_enviada', 'consentimento_registrado',
    'qualificacao_alterada', 'vendedor_atribuido', 'handoff_crm', 'descadastrado', 'anonimizado'));

-- lead_consentimentos ---------------------------------------------------------------------------

-- O Landing guarda o texto exibido no aceite, não uma versão do termo
ALTER TABLE marketing.lead_consentimentos RENAME COLUMN versao_termo TO texto_termo;
ALTER TABLE marketing.lead_consentimentos ALTER COLUMN texto_termo TYPE text;
ALTER TABLE marketing.lead_consentimentos DROP COLUMN formulario_versao;
ALTER TABLE marketing.lead_consentimentos ADD COLUMN sessao_id uuid;

-- Nenhuma rotina grava as origens antigas; a troca do CHECK não encontra linha com elas
ALTER TABLE marketing.lead_consentimentos DROP CONSTRAINT lead_consentimentos_origem_check;
ALTER TABLE marketing.lead_consentimentos ADD CONSTRAINT ck_lead_consentimentos_origem CHECK (origem IN (
    'landing.formulario.recebido', 'landing.chatbot.lead_capturado', 'descadastro'));
