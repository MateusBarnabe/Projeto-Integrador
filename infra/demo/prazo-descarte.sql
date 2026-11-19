-- Demo do lead 0 (R1): prazo curto de descarte das visitas de anúncio para a Empresa A (tenant do
-- placeholder do Landing). Pode rodar mais de uma vez.
--
-- As regras de pontuação por comportamento NÃO entram por aqui: a matriz inicial (RegrasIniciais, R5)
-- cria as de utm e as de comportamento na primeira pontuação do tenant, mas só se o tenant nunca teve
-- regra. Inserir regras antes disso (como este arquivo fazia) bloqueava a matriz inteira, e os leads
-- ficavam com 0 ponto. Para mudar as regras, use Configurações › Qualificação e distribuição.
--
--   docker compose exec -T postgres psql -U postgres -d plataforma < infra/demo/prazo-descarte.sql

\set tenant '''a0000000-0000-4000-8000-00000000000a'''

-- Dois minutos sem enviar o formulário nem dar sinal: dá para ver o descarte durante a apresentação
INSERT INTO marketing.rastreamento_configuracoes
    (id, tenant_id, ativo, prazo_conversao_minutos, created_at, updated_at)
VALUES (gen_random_uuid(), :tenant, true, 2, now(), now())
ON CONFLICT (tenant_id) DO UPDATE SET ativo = true, prazo_conversao_minutos = 2, updated_at = now();
