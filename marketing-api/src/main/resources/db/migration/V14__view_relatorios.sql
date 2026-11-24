-- View pública para o motor de relatórios do Grupo 3 (Contrato de Integração §9.8, decisão D9).
-- Somente leitura e SEM dados pessoais (nome, e-mail, telefone, WhatsApp, respostas do formulário,
-- IP): serve para contar e agrupar leads, não para identificá-los. O consumidor filtra tenant_id
-- pelo token do usuário.
CREATE VIEW marketing.vw_pub_leads AS
  SELECT id,
         tenant_id,
         etapa,
         temperatura,
         origem,
         canal,
         utm_source,
         utm_medium,
         utm_campaign,
         formulario_id,
         pontuacao,
         faixa_qualificacao,
         qualificado_em,
         vendedor_id,
         vendedor_atribuido_em,
         crm_empresa_id,
         crm_oportunidade_id,
         consentimento_marketing,
         status_aquecimento,
         anonimizado,
         created_at
    FROM marketing.leads
   WHERE deleted_at IS NULL;

-- O IF EXISTS deixa a migration rodar nos testes, onde usr_chamados não existe.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'usr_chamados') THEN
    GRANT USAGE  ON SCHEMA marketing      TO usr_chamados;
    GRANT SELECT ON marketing.vw_pub_leads TO usr_chamados;
  END IF;
END $$;
