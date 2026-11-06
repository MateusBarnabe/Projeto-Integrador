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
import { erro, ok } from './envelope.ts';
import { gravarEnvio } from './envios.ts';
import { buscarPorChave, formularioPublico, PAGINA_EXEMPLO } from './formulario.ts';
import { normalizarOrigem, validarRespostas } from './validacao.ts';

const app = express();
app.use(express.json());

app.get('/api/landing/health', (_req, res) => {
  res.json(ok({ status: 'UP' }));
});

// ── Superfície pública (sem token; o tenant vem da chave pública do formulário) ──────────

const naoEncontrado = () => erro('Formulário não encontrado.', [
  { campo: 'chavePublica', codigo: 'NAO_ENCONTRADO', detalhe: 'Chave inexistente, formulário excluído ou inativo.' },
]);

/** Schema público do formulário: campos, tipos e texto de consentimento. */
app.get('/public/landing/formularios/:chavePublica', (req, res) => {
  const formulario = buscarPorChave(req.params.chavePublica);
  if (!formulario) return void res.status(404).json(naoEncontrado());
  res.json(ok(formularioPublico(formulario)));
});

/** Recebe a resposta do visitante. Ordem: consentimento → validação dos campos → gravação. */
app.post('/public/landing/formularios/:chavePublica/envios', (req, res) => {
  const formulario = buscarPorChave(req.params.chavePublica);
  if (!formulario) return void res.status(404).json(naoEncontrado());

  const corpo = req.body ?? {};
  if (corpo.consentimento?.aceito !== true) {
    return void res.status(400).json(erro('Não foi possível enviar o formulário.', [
      { campo: 'consentimento', codigo: 'CONSENTIMENTO_OBRIGATORIO', detalhe: 'É preciso aceitar o termo de consentimento para enviar.' },
    ]));
  }

  const { valores, erros } = validarRespostas(formulario, corpo.respostas);
  if (erros.length > 0) return void res.status(400).json(erro('Não foi possível enviar o formulário.', erros));

  const naPagina = corpo.paginaSlug === PAGINA_EXEMPLO.slug;
  gravarEnvio(formulario, {
    paginaId: naPagina ? PAGINA_EXEMPLO.id : null,
    canal: naPagina ? 'landing_page' : 'formulario',
    respostas: valores,
    origem: normalizarOrigem(corpo.origem),
  });

  // A superfície pública recebe, não consulta: a resposta não traz o id do envio (§12.6)
  res.json(ok({ mensagemSucesso: formulario.mensagemSucesso, urlRedirecionamento: formulario.urlRedirecionamento }));
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
