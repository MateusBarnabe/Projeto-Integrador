/*
 * Servidor do placeholder do Landing. Faz o papel do back-end do Grupo 7 para os testes locais:
 * superfície pública (formulário e chatbot), gravação dos envios, publicação dos eventos
 * landing.* no RabbitMQ e as duas rotas que o Marketing lê, conforme o landing.yaml e o
 * landing.asyncapi.yaml do infra e Docs/contratos/landing-requisitos.md.
 *
 * Usa o mesmo nome de serviço e a mesma porta do Landing real (landing:8088), para a troca
 * pelo módulo do Grupo 7 ser só de configuração.
 */
import { randomUUID } from 'node:crypto';
import { existsSync } from 'node:fs';
import path from 'node:path';
import express, { type Request, type Response } from 'express';
import { buscarSessao, consentir, encerrarSessao, iniciarSessao, passo, responder } from './chatbot.ts';
import { config } from './config.ts';
import { erro, ok } from './envelope.ts';
import { buscarEnvio, envioParaContrato, gravarEnvio } from './envios.ts';
import { publicarFormularioRecebido, publicarLeadCapturado } from './eventos.ts';
import {
  buscarPorChave,
  buscarPorId,
  contatoDasRespostas,
  formularioPublico,
  formularioResumo,
  PAGINA_EXEMPLO,
  type Formulario,
} from './formulario.ts';
import { normalizarOrigem, validarRespostas } from './validacao.ts';

const app = express();
app.use(express.json());

/** `X-Request-Id` da requisição (o gateway sempre manda), repassado em `correlacaoId`. */
const correlacao = (req: Request) => req.header('X-Request-Id') ?? randomUUID();
const paginaDoSlug = (slug: unknown) => (slug === PAGINA_EXEMPLO.slug ? PAGINA_EXEMPLO.id : null);

app.get('/api/landing/health', (_req, res) => {
  res.json(ok({ status: 'UP' }));
});

// ── Superfície pública (sem token; o tenant vem da chave pública do formulário) ──────────

const formularioNaoEncontrado = () => erro('Formulário não encontrado.', [
  { campo: 'chavePublica', codigo: 'NAO_ENCONTRADO', detalhe: 'Chave inexistente, formulário excluído ou inativo.' },
]);
const sessaoNaoEncontrada = () => erro('Conversa não encontrada.', [
  { campo: 'sessaoId', codigo: 'NAO_ENCONTRADO', detalhe: 'Sessão inexistente, de outro formulário ou já concluída.' },
]);
const semConsentimento = () => erro('Não foi possível enviar o formulário.', [
  { campo: 'consentimento', codigo: 'CONSENTIMENTO_OBRIGATORIO', detalhe: 'É preciso aceitar o termo de consentimento para enviar.' },
]);
const confirmacao = (formulario: Formulario) =>
  ok({ mensagemSucesso: formulario.mensagemSucesso, urlRedirecionamento: formulario.urlRedirecionamento });

function formularioDaChave(req: Request, res: Response): Formulario | undefined {
  const formulario = buscarPorChave(String(req.params.chavePublica));
  if (!formulario) res.status(404).json(formularioNaoEncontrado());
  return formulario;
}

/** Schema público do formulário: campos, tipos e texto de consentimento. */
app.get('/public/landing/formularios/:chavePublica', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (formulario) res.json(ok(formularioPublico(formulario)));
});

/**
 * Recebe a resposta do visitante. Ordem: consentimento → validação dos campos → gravação →
 * `landing.formulario.recebido` (depois de gravar). A chamada ao CRM antes do evento é a L4.
 */
app.post('/public/landing/formularios/:chavePublica/envios', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (!formulario) return;

  const corpo = req.body ?? {};
  if (corpo.consentimento?.aceito !== true) return void res.status(400).json(semConsentimento());

  const { valores, erros } = validarRespostas(formulario, corpo.respostas);
  if (erros.length > 0) return void res.status(400).json(erro('Não foi possível enviar o formulário.', erros));

  const paginaId = paginaDoSlug(corpo.paginaSlug);
  const envio = gravarEnvio(formulario, {
    paginaId,
    canal: paginaId ? 'landing_page' : 'formulario',
    respostas: valores,
    origem: normalizarOrigem(corpo.origem),
  });
  void publicarFormularioRecebido(envio, correlacao(req));

  // A superfície pública recebe, não consulta: a resposta não traz o id do envio (§12.6)
  res.json(confirmacao(formulario));
});

/** Inicia a conversa do chatbot de exemplo e devolve o primeiro passo. */
app.post('/public/landing/formularios/:chavePublica/sessoes', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (!formulario) return;
  const corpo = req.body ?? {};
  const sessao = iniciarSessao(formulario, paginaDoSlug(corpo.paginaSlug), normalizarOrigem(corpo.origem));
  res.status(201)
    .location(`/public/landing/formularios/${formulario.chavePublica}/sessoes/${sessao.id}`)
    .json(ok(passo(sessao)));
});

app.post('/public/landing/formularios/:chavePublica/sessoes/:sessaoId/consentimento', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (!formulario) return;
  const sessao = buscarSessao(formulario, String(req.params.sessaoId));
  if (!sessao) return void res.status(404).json(sessaoNaoEncontrada());
  if (req.body?.aceito !== true) return void res.status(400).json(semConsentimento());
  consentir(sessao);
  res.json(ok(passo(sessao)));
});

/** Responde a pergunta atual. Na primeira com e-mail ou telefone e consentimento, publica a captura. */
app.post('/public/landing/formularios/:chavePublica/sessoes/:sessaoId/respostas', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (!formulario) return;
  const sessao = buscarSessao(formulario, String(req.params.sessaoId));
  if (!sessao) return void res.status(404).json(sessaoNaoEncontrada());

  const resultado = responder(sessao, req.body?.campoId, req.body?.valor);
  if ('erro' in resultado) return void res.status(400).json(erro('Resposta inválida.', [resultado.erro]));

  if (resultado.capturou) {
    void publicarLeadCapturado({
      tenantId: formulario.tenantId,
      sessaoId: sessao.id,
      formularioId: formulario.id,
      paginaId: sessao.paginaId,
      contato: contatoDasRespostas(formulario, sessao.respostas),
      ultimaOrigem: sessao.origem.ultima,
      aceitoEm: sessao.aceitoEm!,
    }, correlacao(req));
  }
  res.json(ok(passo(sessao)));
});

/** Conclui a conversa: vira um envio do canal `chatbot`, pelo mesmo caminho do formulário. */
app.post('/public/landing/formularios/:chavePublica/sessoes/:sessaoId/concluir', (req, res) => {
  const formulario = formularioDaChave(req, res);
  if (!formulario) return;
  const sessao = buscarSessao(formulario, String(req.params.sessaoId));
  if (!sessao) return void res.status(404).json(sessaoNaoEncontrada());
  if (sessao.aceitoEm === null) return void res.status(400).json(semConsentimento());

  const { valores, erros } = validarRespostas(formulario, sessao.respostas);
  if (erros.length > 0) return void res.status(400).json(erro('Faltam respostas obrigatórias.', erros));

  const envio = gravarEnvio(formulario, {
    paginaId: sessao.paginaId,
    canal: 'chatbot',
    respostas: valores,
    origem: sessao.origem,
    aceitoEm: sessao.aceitoEm,
  });
  encerrarSessao(sessao);
  void publicarFormularioRecebido(envio, correlacao(req));
  res.json(confirmacao(formulario));
});

// ── Rotas lidas pelo Marketing (token de serviço + X-Tenant-Id, direto em landing:8088) ─────
// Decisão X10: exige `Authorization: Bearer <token>` e `X-Tenant-Id`, mas não confere a
// assinatura do token (é placeholder local; o Landing real valida pelo JWKS do identity).

const naoEncontrado = (campo: string) =>
  erro('Registro não encontrado.', [{ campo, codigo: 'NAO_ENCONTRADO', detalhe: 'Não existe neste tenant.' }]);

const naoAutenticado = (campo: string, detalhe: string) =>
  erro('Não autenticado.', [{ campo, codigo: 'NAO_AUTENTICADO', detalhe }]);

/** Tenant da chamada de serviço; sem token Bearer ou sem `X-Tenant-Id`, responde `401` e devolve `undefined`. */
function tenantDaRequisicao(req: Request, res: Response): string | undefined {
  const token = /^Bearer\s+(\S+)$/i.exec(req.header('Authorization') ?? '')?.[1];
  if (!token) {
    res.status(401).json(naoAutenticado('Authorization', 'Chamada de serviço precisa do cabeçalho Authorization: Bearer <token>.'));
    return undefined;
  }
  const tenant = req.header('X-Tenant-Id');
  if (!tenant) {
    res.status(401).json(naoAutenticado('X-Tenant-Id', 'Chamada de serviço precisa do cabeçalho X-Tenant-Id.'));
    return undefined;
  }
  return tenant.toLowerCase();
}

/** `GET /api/landing/envios/{id}` (`landing.envio.ver`): envio de outro tenant responde 404. */
app.get('/api/landing/envios/:id', (req, res) => {
  const tenant = tenantDaRequisicao(req, res);
  if (!tenant) return;
  const envio = buscarEnvio(String(req.params.id));
  const formulario = envio && buscarPorId(envio.formularioId);
  if (!envio || !formulario || envio.tenantId !== tenant) return void res.status(404).json(naoEncontrado('id'));
  res.json(ok(envioParaContrato(envio, formulario)));
});

/** `GET /api/landing/formularios/{id}/resumo` (`landing.formulario.ver_resumo`). */
app.get('/api/landing/formularios/:id/resumo', (req, res) => {
  const tenant = tenantDaRequisicao(req, res);
  if (!tenant) return;
  const formulario = buscarPorId(String(req.params.id));
  if (!formulario || formulario.tenantId !== tenant) return void res.status(404).json(naoEncontrado('id'));
  res.json(ok(formularioResumo(formulario)));
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
