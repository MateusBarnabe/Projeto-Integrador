/*
 * Servidor do placeholder do Landing. Faz o papel do back-end do Grupo 7 para os testes locais:
 * é aqui que entram a publicação dos eventos landing.* no RabbitMQ, a gravação dos envios e
 * GET /api/landing/envios/{envioId}, conforme Docs/contratos/landing-requisitos.md.
 *
 * Usa o mesmo nome de serviço e a mesma porta do Landing real (landing:8088), para a troca
 * pelo módulo do Grupo 7 ser só de configuração.
 */
import { existsSync } from 'node:fs';
import path from 'node:path';
import express from 'express';
import { config } from './config.ts';
import { ok } from './envelope.ts';

const app = express();
app.use(express.json());

app.get('/api/landing/health', (_req, res) => {
  res.json(ok({ status: 'UP' }));
});

// Em container, o mesmo servidor entrega o build da página (em desenvolvimento, quem entrega é o Vite)
const dist = path.resolve(import.meta.dirname, '../dist');
if (existsSync(dist)) {
  app.use(express.static(dist));
  app.get('/{*caminho}', (_req, res) => res.sendFile(path.join(dist, 'index.html')));
}

app.listen(config.porta, () => {
  console.log(`Placeholder do Landing ouvindo em http://localhost:${config.porta}`);
});
