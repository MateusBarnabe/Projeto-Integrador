/*
 * Stub do CRM (Grupo 6) para o ambiente local do Grupo 4. Não é o nosso módulo: imita o pedaço do
 * contrato do CRM (contratos/crm.yaml e crm.asyncapi.yaml do infra) que a jornada do lead usa.
 * O Landing (placeholder) cria empresa, contato e oportunidade aqui; ao criar a oportunidade, o
 * stub publica crm.oportunidade.criada em crm.eventos, que o Marketing consome.
 *
 * Usa o mesmo nome de serviço e a mesma porta do CRM real (crm:8082), para a troca ser só de
 * configuração.
 */
import express, { type NextFunction, type Request, type Response } from 'express';
import { config } from './config.ts';
import { erro, ok } from './envelope.ts';
import { rotas } from './rotas.ts';

const app = express();
app.use(express.json());

app.get('/api/crm/health', (_req, res) => {
  res.json(ok({ status: 'UP' }));
});

app.use(rotas);

app.use((_req, res) => {
  res.status(404).json(erro('Rota não existe no stub do CRM.'));
});

// Corpo que não é JSON válido e qualquer erro inesperado também saem no envelope
app.use((e: Error & { status?: number }, _req: Request, res: Response, _next: NextFunction) => {
  const status = e.status && e.status >= 400 && e.status < 500 ? e.status : 500;
  if (status === 500) console.error(e);
  res.status(status).json(erro(status === 500 ? 'Erro inesperado no stub do CRM.' : 'Corpo da requisição inválido.'));
});

app.listen(config.porta, () => {
  console.log(`Stub do CRM ouvindo em http://localhost:${config.porta}`);
});
