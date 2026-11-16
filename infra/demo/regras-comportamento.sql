-- Demo do lead 0 (R1): regras de pontuação por comportamento e prazo curto de descarte para a Empresa A
-- (tenant do placeholder do Landing). Enquanto a tela de qualificação (W4) não existe, as regras entram
-- por aqui. Pode rodar mais de uma vez: apaga as regras de comportamento da demo antes de criar.
--
--   docker compose exec -T postgres psql -U postgres -d plataforma < infra/demo/regras-comportamento.sql

\set tenant '''a0000000-0000-4000-8000-00000000000a'''

DELETE FROM marketing.regras_pontuacao
 WHERE tenant_id = :tenant AND fonte = 'comportamento';

INSERT INTO marketing.regras_pontuacao
    (id, tenant_id, criterio, fonte, campo_comportamento, valores, pontos, ativo, created_at, updated_at)
VALUES
    (gen_random_uuid(), :tenant, 'Engajamento na página', 'comportamento', 'tempo_na_pagina',
     '[{"minimo": 30}]', 20, true, now(), now()),
    (gen_random_uuid(), :tenant, 'Interesse em preço', 'comportamento', 'pagina_visitada',
     '["precos"]', 15, true, now(), now()),
    (gen_random_uuid(), :tenant, 'Simulou o investimento', 'comportamento', 'elemento_clicado',
     '["simulador-preco"]', 10, true, now(), now()),
    (gen_random_uuid(), :tenant, 'Leu a página inteira', 'comportamento', 'profundidade_rolagem',
     '[{"minimo": 90}]', 5, true, now(), now());

-- Dois minutos sem enviar o formulário nem dar sinal: dá para ver o descarte durante a apresentação
INSERT INTO marketing.rastreamento_configuracoes
    (id, tenant_id, ativo, prazo_conversao_minutos, created_at, updated_at)
VALUES (gen_random_uuid(), :tenant, true, 2, now(), now())
ON CONFLICT (tenant_id) DO UPDATE SET ativo = true, prazo_conversao_minutos = 2, updated_at = now();
