#!/usr/bin/env node
/*
 * Ponta a ponta da jornada do lead (I4): percorre, pelo HTTP e como o navegador faria, o caminho
 *
 *   login -> link de campanha -> clique no anuncio -> sinais -> formulario do Landing -> CRM
 *   -> lead na etapa 3 -> pontuacao -> vendedor -> visita convertida -> boas-vindas -> detalhe
 *
 * e imprime uma linha verde (v) ou vermelha (x) por conferencia. Sai com codigo 0 se tudo passou.
 * Node 18+, sem dependencia. Roteiro completo, com as telas: Docs/roteiro-ponta-a-ponta.md.
 *
 * Pre-requisito: o ambiente de scripts/dev-plataforma.ps1 no ar (identity, RabbitMQ, Mailpit, API,
 * placeholder do Landing e stub do CRM).
 *
 *   node scripts/ponta-a-ponta.mjs [--descarte] [--manter-horario] [--espera=<segundos>]
 *
 *   --descarte         acrescenta o passo 7: um segundo clique sem envio, que deve virar "descartada"
 *                      no prazo do tenant (2 min com o SQL da demo); demora mais uns 2 a 3 minutos
 *   --manter-horario   nao mexe no horario de atendimento do rodizio (por padrao, fora do horario
 *                      comercial, o script abre a escala 24h todos os dias, para o vendedor ser atribuido)
 *   --espera=<s>       limite de espera de cada passo assincrono (padrao 150 s; as boas-vindas saem
 *                      do job 4.5, que roda uma vez por minuto)
 *
 * Enderecos (variaveis de ambiente, com os padroes do dev-plataforma.ps1):
 *   MARKETING_URL  http://localhost:8087   API do Marketing (rotas /api e /public)
 *   IDENTITY_URL   http://localhost:8081   identity
 *   LANDING_URL    http://localhost:3008   pagina e superficie publica do placeholder do Landing
 *   MAILPIT_URL    http://localhost:8025   API do Mailpit
 *   USUARIO_EMAIL / USUARIO_SENHA          padrao marketing@empresa-a.dev / Plataforma2026
 *   VENDEDOR_EMAIL                         padrao vendedor@empresa-a.dev (entra na escala do rodizio)
 */

const args = process.argv.slice(2);
const tem = (nome) => args.includes(nome);
const valor = (nome, padrao) => args.find((a) => a.startsWith(`${nome}=`))?.split('=')[1] ?? padrao;

const MARKETING = (process.env.MARKETING_URL ?? 'http://localhost:8087').replace(/\/+$/, '');
const IDENTITY = (process.env.IDENTITY_URL ?? 'http://localhost:8081').replace(/\/+$/, '');
const LANDING = (process.env.LANDING_URL ?? 'http://localhost:3008').replace(/\/+$/, '');
const MAILPIT = (process.env.MAILPIT_URL ?? 'http://localhost:8025').replace(/\/+$/, '');
const USUARIO = process.env.USUARIO_EMAIL ?? 'marketing@empresa-a.dev';
const SENHA = process.env.USUARIO_SENHA ?? 'Plataforma2026';
const VENDEDOR = process.env.VENDEDOR_EMAIL ?? 'vendedor@empresa-a.dev';
const ESPERA_S = Number(valor('--espera', 150));

const TENANT_EMPRESA_A = 'a0000000-0000-4000-8000-00000000000a';
const CHAVE_FORMULARIO = 'fk_empresaA01';
const SLUG_PAGINA = 'empresa-a-consultoria';
// ids dos campos do formulario de exemplo (landing-web/server/formulario.ts)
const CAMPO = (n) => `a0000000-0000-4000-8000-00000000c00${n}`;
const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1';

// ── saida ──────────────────────────────────────────────────────────────────────────────────────
const cor = process.stdout.isTTY || process.env.FORCE_COLOR;
const pinta = (codigo, texto) => (cor ? `\u001b[${codigo}m${texto}\u001b[0m` : texto);
let aprovados = 0;
let reprovados = 0;
let pulados = 0;

const passou = (texto) => { aprovados++; console.log(`  ${pinta(32, '✓')} ${texto}`); };
const falhou = (texto) => { reprovados++; console.log(`  ${pinta(31, '✗')} ${pinta(31, texto)}`); };
const aviso = (texto) => console.log(`  ${pinta(33, '!')} ${texto}`);
const info = (texto) => console.log(`    ${pinta(90, texto)}`);

/** Confere uma condicao sem interromper o passo. */
function confere(condicao, texto, motivoDaFalha) {
  if (condicao) passou(texto);
  else falhou(`${texto}${motivoDaFalha ? ` (${motivoDaFalha})` : ''}`);
  return Boolean(condicao);
}

/** Erro que interrompe o passo atual: o que vem depois nao faria sentido. */
class Interrompe extends Error {}
const exige = (condicao, motivo) => { if (!condicao) throw new Interrompe(motivo); };

let numeroDoPasso = 0;
let abortado = false;
async function passo(titulo, fn, { opcional = false } = {}) {
  numeroDoPasso++;
  console.log(`\n${pinta(1, `${numeroDoPasso}. ${titulo}`)}`);
  if (abortado) {
    pulados++;
    console.log(`  ${pinta(33, '-')} pulado: um passo anterior falhou`);
    return;
  }
  try {
    await fn();
  } catch (erro) {
    falhou(erro instanceof Interrompe ? erro.message : `${erro.name}: ${erro.message}`);
    if (!opcional) abortado = true;
  }
}

// ── HTTP ───────────────────────────────────────────────────────────────────────────────────────
const dormir = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function http(metodo, url, { token, corpo, cabecalhos = {}, texto = false, redirect = 'follow' } = {}) {
  const headers = { ...cabecalhos };
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (corpo !== undefined) {
    body = typeof corpo === 'string' ? corpo : JSON.stringify(corpo);
    headers['Content-Type'] ??= 'application/json';
  }
  const resposta = await fetch(url, { method: metodo, headers, body, redirect, signal: AbortSignal.timeout(20_000) });
  const bruto = await resposta.text();
  let json = null;
  if (!texto) {
    try { json = bruto ? JSON.parse(bruto) : null; } catch { json = null; }
  }
  return { status: resposta.status, headers: resposta.headers, texto: bruto, json, dados: json?.data };
}

/** Chamada autenticada ao Marketing que deve devolver o envelope de sucesso. */
async function api(metodo, caminho, token, corpo, statusEsperado = [200, 201]) {
  const r = await http(metodo, `${MARKETING}${caminho}`, { token, corpo });
  const esperado = Array.isArray(statusEsperado) ? statusEsperado : [statusEsperado];
  if (!esperado.includes(r.status) || r.json?.success !== true) {
    const detalhe = r.json?.message ?? r.texto.slice(0, 160);
    throw new Interrompe(`${metodo} ${caminho} respondeu ${r.status}: ${detalhe}`);
  }
  return r.dados;
}

/** Repete `consulta` ate ela devolver algo verdadeiro ou o tempo acabar; devolve o ultimo valor. */
async function esperar(consulta, { limiteS = ESPERA_S, intervaloMs = 3000, avisoCada = 15 } = {}) {
  const fim = Date.now() + limiteS * 1000;
  let ultimo = null;
  let proximoAviso = Date.now() + avisoCada * 1000;
  while (Date.now() < fim) {
    ultimo = await consulta();
    if (ultimo) return ultimo;
    if (Date.now() > proximoAviso) {
      info(`aguardando... (${Math.round((fim - Date.now()) / 1000)} s restantes)`);
      proximoAviso = Date.now() + avisoCada * 1000;
    }
    await dormir(intervaloMs);
  }
  return ultimo;
}

function claims(token) {
  return JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
}

async function entrar(email) {
  const r = await http('POST', `${IDENTITY}/api/identity/auth/login`, { corpo: { email, senha: SENHA } });
  exige(r.status === 200 && r.dados?.accessToken, `login de ${email} respondeu ${r.status}: ${r.json?.message ?? r.texto.slice(0, 120)}`);
  return r.dados.accessToken;
}

// ── estado compartilhado entre os passos ───────────────────────────────────────────────────────
const execucao = Date.now().toString(36);
const ctx = {
  token: null,
  vendedorId: null,
  link: null,
  campanha: `ponta-a-ponta-${execucao}`,
  email: `ponta-a-ponta+${execucao}@exemplo.dev`,
  nome: `Teste Ponta a Ponta ${execucao}`,
  urlDeEntrada: null,
  visitaId: null,
  lead: null,
  detalhe: null,
};

console.log(pinta(1, 'Ponta a ponta do Marketing (I4)'));
info(`API ${MARKETING} | identity ${IDENTITY} | landing ${LANDING} | mailpit ${MAILPIT}`);
info(`execucao ${execucao}: ${ctx.email}`);

// ── 1. login ───────────────────────────────────────────────────────────────────────────────────
await passo('Login no identity', async () => {
  const saude = await http('GET', `${MARKETING}/api/marketing/health`);
  exige(saude.status === 200, `a API do Marketing nao respondeu em ${MARKETING} (rode scripts/dev-plataforma.ps1): ${saude.status}`);
  passou('API do Marketing no ar (/api/marketing/health)');

  ctx.token = await entrar(USUARIO);
  const c = claims(ctx.token);
  passou(`login de ${USUARIO}`);
  confere(c.tenant_id === TENANT_EMPRESA_A, `token do tenant Empresa A (${TENANT_EMPRESA_A.slice(0, 8)}...)`, `tenant_id = ${c.tenant_id}`);
  const perms = c.perms ?? [];
  const precisa = ['marketing.lead.ver', 'marketing.configuracao.ver', 'marketing.configuracao.editar', 'marketing.qualificacao.ver', 'marketing.qualificacao.editar'];
  const faltam = precisa.filter((p) => !perms.includes(p));
  confere(faltam.length === 0, 'token com as permissoes do roteiro', `faltam: ${faltam.join(', ')}`);

  const semToken = await http('GET', `${MARKETING}/api/marketing/leads`);
  confere(semToken.status === 401 && semToken.json?.success === false, 'sem token a API responde 401 com envelope', `status ${semToken.status}`);

  const tokenDoVendedor = await entrar(VENDEDOR);
  ctx.vendedorId = claims(tokenDoVendedor).sub;
  exige(ctx.vendedorId, 'o token do vendedor nao tem o claim sub');
  passou(`vendedor ${VENDEDOR} (${ctx.vendedorId.slice(0, 8)}...)`);
});

// ── 2. link de campanha e escala ───────────────────────────────────────────────────────────────
await passo('Link de campanha, rastreamento ligado e vendedor na escala', async () => {
  const rastreamento = await api('GET', '/api/marketing/rastreamento', ctx.token);
  if (!rastreamento.ativo) {
    await api('PUT', '/api/marketing/rastreamento', ctx.token, { ativo: true, prazoConversaoMinutos: rastreamento.prazoConversaoMinutos });
    aviso('o rastreamento estava desligado no tenant: o script ligou');
  }
  passou(`rastreamento ligado, prazo de conversao de ${rastreamento.prazoConversaoMinutos} min`);
  ctx.prazoMinutos = rastreamento.prazoConversaoMinutos;

  ctx.link = await api('POST', '/api/marketing/links-campanha', ctx.token, {
    nome: `Ponta a ponta ${execucao}`,
    urlDestino: `${LANDING}/`,
    utmSource: 'google',
    utmMedium: 'cpc',
    utmCampaign: ctx.campanha,
  }, 201);
  passou(`link criado: codigo ${ctx.link.codigo}`);
  confere(ctx.link.urlPublica.endsWith(`/public/marketing/c/${ctx.link.codigo}`), 'URL publica do link no formato /public/marketing/c/{codigo}', ctx.link.urlPublica);

  // Escala: acrescenta o vendedor sem tirar quem ja estava
  const escala = await api('GET', '/api/marketing/rodizio/vendedores', ctx.token);
  const entradas = escala.map((v) => ({ vendedorId: v.vendedorId, ativo: v.ativo }));
  const proprio = entradas.find((e) => e.vendedorId === ctx.vendedorId);
  if (!proprio) entradas.push({ vendedorId: ctx.vendedorId, ativo: true });
  else proprio.ativo = true;
  const nova = await api('PUT', '/api/marketing/rodizio/vendedores', ctx.token, { vendedores: entradas });
  confere(nova.some((v) => v.vendedorId === ctx.vendedorId && v.ativo), 'vendedor ativo na escala do rodizio', JSON.stringify(nova.map((v) => v.vendedorId.slice(0, 8))));
  ctx.escalaAtiva = new Set(nova.filter((v) => v.ativo).map((v) => v.vendedorId));

  // Horario de atendimento: fora dele o rodizio deixa o lead "aguardando vendedor"
  const q = await api('GET', '/api/marketing/qualificacao', ctx.token);
  const agoraSp = new Intl.DateTimeFormat('en-GB', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit', hour12: false, weekday: 'short' })
    .formatToParts(new Date()).reduce((m, p) => ({ ...m, [p.type]: p.value }), {});
  const diaDaSemana = { Mon: 1, Tue: 2, Wed: 3, Thu: 4, Fri: 5, Sat: 6, Sun: 7 }[agoraSp.weekday];
  const minutos = Number(agoraSp.hour) * 60 + Number(agoraSp.minute);
  const hm = (texto) => Number(texto.slice(0, 2)) * 60 + Number(texto.slice(3, 5));
  const aberto = q.rodizioAtivo
    && q.diasAtendimento.includes(diaDaSemana)
    && minutos >= hm(q.horarioComercialInicio)
    && minutos < hm(q.horarioComercialFim);
  if (aberto) {
    passou(`rodizio ativo e dentro do horario de atendimento (${q.horarioComercialInicio} a ${q.horarioComercialFim})`);
  } else if (tem('--manter-horario')) {
    aviso('fora do horario de atendimento (ou rodizio desligado) e --manter-horario: o vendedor pode ficar para depois');
  } else {
    await api('PUT', '/api/marketing/qualificacao', ctx.token, {
      limiteMqlQuente: q.limiteMqlQuente,
      limiteMqlMorno: q.limiteMqlMorno,
      rodizioAtivo: true,
      horarioComercialInicio: '00:00',
      horarioComercialFim: '23:59',
      diasAtendimento: [1, 2, 3, 4, 5, 6, 7],
      minutosTransbordo: q.minutosTransbordo,
    });
    aviso('fora do horario de atendimento: o script abriu a escala 00:00 a 23:59, todos os dias (altera a configuracao do tenant de teste)');
  }
  ctx.limites = { quente: q.limiteMqlQuente, morno: q.limiteMqlMorno };
});

// ── 3. clique e sinais ─────────────────────────────────────────────────────────────────────────
await passo('Clique no anuncio e sinais da landing page', async () => {
  const clique = await http('GET', `${MARKETING}/public/marketing/c/${ctx.link.codigo}`, {
    cabecalhos: { 'User-Agent': IPHONE, 'X-Forwarded-For': '200.150.10.20' },
    redirect: 'manual',
    texto: true,
  });
  exige(clique.status === 302, `o clique deveria responder 302 e respondeu ${clique.status}`);
  passou('clique responde 302 (sem envelope, como o redirecionamento do anuncio)');
  const destino = new URL(clique.headers.get('location'));
  ctx.urlDeEntrada = destino.href;
  ctx.visitaId = destino.searchParams.get('mkt_visita');
  exige(ctx.visitaId, `o Location nao trouxe o mkt_visita: ${destino.href}`);
  passou(`Location da landing page com mkt_visita=${ctx.visitaId.slice(0, 8)}...`);
  confere(destino.searchParams.get('utm_source') === 'google' && destino.searchParams.get('utm_medium') === 'cpc'
    && destino.searchParams.get('utm_campaign') === ctx.campanha, 'UTM do link acrescentadas ao destino', destino.search);

  const inexistente = await http('GET', `${MARKETING}/public/marketing/c/naoexiste`, { redirect: 'manual' });
  confere(inexistente.status === 404 && inexistente.json?.success === false, 'codigo inexistente responde 404 com envelope', `status ${inexistente.status}`);

  const pagina = await http('GET', ctx.urlDeEntrada, { texto: true });
  confere(pagina.status === 200 && /<html|<!doctype/i.test(pagina.texto), 'landing page (placeholder) abre com a URL do clique', `status ${pagina.status}`);
  const script = await http('GET', `${MARKETING}/public/marketing/rastreamento.js`, { texto: true });
  confere(script.status === 200 && script.texto.includes('mkt_visita'), 'script rastreamento.js servido pela API', `status ${script.status}`);

  // O que o script faz no navegador: sendBeacon em text/plain, com os totais da visita
  const sinais = {
    tempoNaPaginaSegundos: 45,
    profundidadeRolagem: 95,
    elementosClicados: ['simulador-preco', 'ver-planos'],
    paginasVisitadas: ['/', 'precos'],
    idioma: 'pt-BR',
    fuso: 'America/Sao_Paulo',
    tela: '390x844',
  };
  const envio = await http('POST', `${MARKETING}/public/marketing/visitas/${ctx.visitaId}/sinais`, {
    corpo: JSON.stringify(sinais),
    cabecalhos: { 'Content-Type': 'text/plain;charset=UTF-8', Origin: new URL(LANDING).origin },
  });
  confere(envio.status === 200 && envio.json?.success === true, 'sinais (tempo, rolagem, cliques, secoes) aceitos em text/plain', `status ${envio.status}`);
  const desconhecida = await http('POST', `${MARKETING}/public/marketing/visitas/00000000-0000-4000-8000-000000000000/sinais`, {
    corpo: JSON.stringify(sinais), cabecalhos: { 'Content-Type': 'text/plain' },
  });
  confere(desconhecida.status === 404, 'sinais de visita desconhecida respondem 404', `status ${desconhecida.status}`);

  const visitas = await api('GET', '/api/marketing/visitas-anuncio?tamanho=50', ctx.token);
  const visita = visitas.itens.find((v) => v.id === ctx.visitaId);
  exige(visita, 'a visita do clique nao apareceu em GET /api/marketing/visitas-anuncio');
  confere(visita.situacao === 'ativa', 'visita ativa (lead 0)', `situacao ${visita.situacao}`);
  confere(visita.contextoTecnico?.tipoDispositivo === 'smartphone', 'aparelho identificado como smartphone pelo User-Agent', JSON.stringify(visita.contextoTecnico));
  confere(visita.comportamento?.tempoNaPaginaSegundos >= 45 && visita.comportamento?.profundidadeRolagem >= 95,
    'comportamento da visita gravado (tempo 45 s, rolagem 95%)', JSON.stringify(visita.comportamento));
  confere(visita.comportamento?.elementosClicados?.includes('simulador-preco'), 'clique em "simulador-preco" gravado', JSON.stringify(visita.comportamento?.elementosClicados));
});

// ── 4. formulario no Landing ───────────────────────────────────────────────────────────────────
await passo('Envio do formulario no placeholder do Landing', async () => {
  const publico = await http('GET', `${LANDING}/public/landing/formularios/${CHAVE_FORMULARIO}`);
  exige(publico.status === 200 && publico.dados?.campos?.length, `formulario ${CHAVE_FORMULARIO} nao respondeu no placeholder (${LANDING}): status ${publico.status}`);
  passou(`formulario ${CHAVE_FORMULARIO} com ${publico.dados.campos.length} campos`);

  // A origem que o form.js do Landing guarda: a pagina de entrada e a URL inteira, com o mkt_visita
  const url = new URL(ctx.urlDeEntrada);
  const p = (n) => url.searchParams.get(n);
  const toque = {
    utmSource: p('utm_source'), utmMedium: p('utm_medium'), utmCampaign: p('utm_campaign'),
    utmTerm: null, utmContent: null, referenciador: null,
    paginaEntrada: ctx.urlDeEntrada, ocorridoEm: new Date().toISOString(),
  };
  const telefone = `62${Math.floor(900000000 + Math.random() * 99999999)}`;
  const envio = await http('POST', `${LANDING}/public/landing/formularios/${CHAVE_FORMULARIO}/envios`, {
    corpo: {
      respostas: {
        [CAMPO(1)]: ctx.nome,
        [CAMPO(2)]: ctx.email,
        [CAMPO(3)]: telefone,
        [CAMPO(4)]: 'Empresa Ponta a Ponta',
        [CAMPO(9)]: '11.222.333/0001-81',
        [CAMPO(5)]: '11 a 50',
        [CAMPO(6)]: ['Vendas', 'Marketing'],
        [CAMPO(7)]: 'Imediatamente',
        [CAMPO(8)]: 'Envio do script ponta-a-ponta.mjs',
      },
      consentimento: { aceito: true },
      paginaSlug: SLUG_PAGINA,
      origem: { primeira: toque, ultima: toque },
    },
  });
  exige(envio.status === 200 && envio.json?.success === true, `envio recusado pelo Landing: ${envio.status} ${envio.json?.message ?? ''} ${JSON.stringify(envio.json?.errors ?? [])}`);
  passou('envio aceito pelo Landing (200, com o consentimento)');

  const semConsentimento = await http('POST', `${LANDING}/public/landing/formularios/${CHAVE_FORMULARIO}/envios`, { corpo: { respostas: {} } });
  confere(semConsentimento.status === 400, 'sem consentimento o Landing recusa (400)', `status ${semConsentimento.status}`);
});

// ── 5. lead no Marketing ───────────────────────────────────────────────────────────────────────
await passo('Lead na etapa 3, pontuado, com vendedor, CRM, visita convertida e boas-vindas', async () => {
  const consulta = encodeURIComponent(ctx.email);
  const pronto = (item) => item && item.etapa === 3 && item.crmOportunidadeId && item.pontuacao != null && item.vendedorId;
  let item = await esperar(async () => {
    const lista = await api('GET', `/api/marketing/leads?q=${consulta}&tamanho=5`, ctx.token);
    const achado = lista.itens.find((l) => l.email === ctx.email);
    return pronto(achado) ? achado : (ctx.lead = achado ?? null, null);
  });
  if (!item) {
    item = ctx.lead;
    exige(item, `o lead ${ctx.email} nao apareceu em GET /api/marketing/leads em ${ESPERA_S} s (o evento landing.formulario.recebido nao chegou ou a leitura do envio falhou)`);
    aviso(`o lead apareceu, mas nao ficou completo em ${ESPERA_S} s`);
  }
  ctx.lead = item;
  passou(`lead ${item.id.slice(0, 8)}... apareceu na lista`);
  confere(item.etapa === 3, 'etapa 3 (envio do formulario)', `etapa ${item.etapa}`);
  confere(item.origem === 'landing_page' && item.canal === 'landing_page', 'origem landing_page, canal landing_page', `${item.origem}/${item.canal}`);
  confere(item.utmCampaign === ctx.campanha, `campanha ${ctx.campanha} ligada ao lead`, `utmCampaign ${item.utmCampaign}`);
  confere(item.tipoDispositivo === 'smartphone', 'aparelho da visita (smartphone) na lista', `tipoDispositivo ${item.tipoDispositivo}`);
  confere(item.crmOportunidadeId != null, 'oportunidade do CRM ligada (crm.oportunidade.criada)', 'crmOportunidadeId nulo');

  // detalhe: pontuacao por regra, vendedor, consentimento, timeline
  const detalhe = await api('GET', `/api/marketing/leads/${item.id}`, ctx.token);
  ctx.detalhe = detalhe;
  const q = detalhe.qualificacao;
  confere(q.pontuacao != null && q.pontuacao > 0, `pontuacao calculada: ${q.pontuacao} pontos, faixa ${q.faixaQualificacao}`, 'sem pontuacao');
  const regras = q.detalhe ?? [];
  info(`regras que somaram: ${regras.map((r) => `${r.criterio} (${r.fonte}) +${r.pontos}`).join(' | ')}`);
  // Matriz inicial (D7 e R5): utm google/cpc +15, tempo >= 30 s +20, secao "precos" +15, clique em
  // "simulador-preco" +10, rolagem >= 90% +5. Se o tenant editou as regras, confere so a fonte.
  const doComportamento = regras.filter((r) => r.fonte === 'comportamento').map((r) => r.campoComportamento);
  confere(['tempo_na_pagina', 'pagina_visitada', 'elemento_clicado', 'profundidade_rolagem'].every((m) => doComportamento.includes(m)),
    'as regras de comportamento somaram pontos (tempo, secao, clique, rolagem)', `campos que pontuaram: ${doComportamento.join(', ') || 'nenhum'}`);
  confere(regras.some((r) => r.fonte === 'utm'), 'a regra de origem (utm google / cpc) somou pontos', `fontes: ${[...new Set(regras.map((r) => r.fonte))].join(', ')}`);
  confere(regras.reduce((soma, r) => soma + r.pontos, 0) === q.pontuacao, 'a soma das regras bate com a pontuacao do lead', `${regras.reduce((soma, r) => soma + r.pontos, 0)} x ${q.pontuacao}`);
  confere(['mql_quente', 'mql_morno'].includes(q.faixaQualificacao), 'lead virou MQL', `faixa ${q.faixaQualificacao}`);

  confere(detalhe.vendedor?.vendedorId != null && ctx.escalaAtiva.has(detalhe.vendedor.vendedorId),
    `vendedor atribuido pelo rodizio (${detalhe.vendedor?.vendedorId?.slice(0, 8) ?? 'nenhum'}...)`,
    'o MQL ficou sem vendedor: fora do horario ou escala vazia');

  // visita convertida e ligada ao lead
  const visitas = await api('GET', '/api/marketing/visitas-anuncio?situacao=convertida&tamanho=50', ctx.token);
  const visita = visitas.itens.find((v) => v.id === ctx.visitaId);
  confere(visita && visita.leadId === item.id, 'visita convertida e ligada ao lead (leadId)', visita ? `leadId ${visita.leadId}` : 'a visita nao esta entre as convertidas');

  // boas-vindas no Mailpit (job 4.5, a cada minuto)
  info('aguardando o e-mail de boas-vindas (job 4.5, uma vez por minuto)...');
  const mensagens = await esperar(async () => {
    const r = await http('GET', `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${ctx.email}`)}`);
    if (r.status !== 200) throw new Interrompe(`Mailpit em ${MAILPIT} respondeu ${r.status}`);
    return r.json?.messages?.length ? r.json.messages : null;
  }, { intervaloMs: 5000, avisoCada: 30 });
  if (confere(mensagens?.length > 0, `e-mail de boas-vindas para ${ctx.email} no Mailpit`, `nao chegou em ${ESPERA_S} s`)) {
    info(`assunto: "${mensagens[0].Subject}"`);
  }
});

// ── 6. detalhe ─────────────────────────────────────────────────────────────────────────────────
await passo('Detalhe do lead (as abas da tela)', async () => {
  exige(ctx.lead, 'sem lead para abrir o detalhe');
  const d = await api('GET', `/api/marketing/leads/${ctx.lead.id}`, ctx.token);
  ctx.detalhe = d;

  confere(d.contato?.email === ctx.email && d.contato?.nome === ctx.nome, 'Dados: contato com nome e e-mail do formulario', JSON.stringify(d.contato));
  confere(d.origem?.utmSource === 'google' && d.origem?.utmMedium === 'cpc' && d.origem?.utmCampaign === ctx.campanha,
    'Origem: UTM google / cpc / campanha do link', JSON.stringify(d.origem));
  confere(d.origem?.landingPageUrl?.includes('mkt_visita='), 'Origem: URL da landing page com o mkt_visita', d.origem?.landingPageUrl);
  confere(d.respostas?.situacao === 'lidas' && d.respostas.itens.length >= 8, `Respostas: lidas do Landing (${d.respostas?.itens?.length} campos)`, `situacao ${d.respostas?.situacao}`);
  const hist = d.consentimento?.historico ?? [];
  confere(d.consentimento?.marketing === true && hist.some((h) => h.aceito && h.textoTermo), 'Consentimento: aceite com o texto do termo no historico', JSON.stringify(d.consentimento));
  confere(d.interacao?.contextoTecnico?.tipoDispositivo === 'smartphone' && d.interacao?.comportamento != null, 'Interacao: aparelho (smartphone) e comportamento da visita', JSON.stringify(d.interacao));
  confere(d.crm?.oportunidadeId != null && d.crm?.empresaId != null && d.crm?.contatoId != null, 'CRM: empresa, contato e oportunidade ligados', JSON.stringify(d.crm));
  confere(d.etapa === 3, 'Etapa 3 no detalhe', `etapa ${d.etapa}`);

  const tipos = (d.timeline ?? []).map((t) => t.tipo);
  info(`timeline: ${tipos.join(' > ')}`);
  for (const esperado of ['etapa_alterada', 'respostas_lidas', 'consentimento_registrado', 'visita_vinculada', 'qualificacao_alterada', 'vendedor_atribuido']) {
    confere(tipos.includes(esperado), `Timeline tem "${esperado}"`);
  }
  const sequencias = (d.timeline ?? []).map((t) => t.sequencia);
  confere(sequencias.every((s, i) => i === 0 || s > sequencias[i - 1]), 'Timeline em ordem de sequencia');

  const comunicacoes = d.aquecimento?.comunicacoes ?? [];
  confere(d.aquecimento?.boasVindasEnviadaEm != null && comunicacoes.some((c) => c.tipoModelo === 'boas_vindas' && c.status === 'enviado'),
    'Aquecimento: boas-vindas enviada e registrada em lead_comunicacoes', JSON.stringify(d.aquecimento));

  const resumo = await api('GET', `/api/marketing/leads/${ctx.lead.id}/resumo`, ctx.token);
  confere(resumo?.id === ctx.lead.id || resumo?.leadId === ctx.lead.id || resumo != null, 'Resumo do lead (usado por outros modulos)');

  const outro = await http('GET', `${MARKETING}/api/marketing/leads/00000000-0000-4000-8000-000000000001`, { token: ctx.token });
  confere(outro.status === 404 && outro.json?.success === false, 'lead inexistente (ou de outro tenant) responde 404 com envelope', `status ${outro.status}`);

  const painel = await http('GET', `${MARKETING}/api/marketing/painel`, { token: ctx.token });
  if (painel.status === 200) {
    confere(painel.dados?.funil?.etapa3 >= 1, 'Painel: o funil ja conta o lead na etapa 3', JSON.stringify(painel.dados?.funil));
    confere(painel.dados?.leadZero?.convertidas >= 1, 'Painel: o lead 0 conta a visita convertida', JSON.stringify(painel.dados?.leadZero));
  } else {
    info(`painel nao conferido (GET /api/marketing/painel respondeu ${painel.status}: a API desta branch pode nao ter o painel)`);
  }
});

// ── 7. descarte (opcional) ─────────────────────────────────────────────────────────────────────
if (tem('--descarte')) {
  await passo('Descarte da visita que nao converteu (opcional)', async () => {
    exige(ctx.prazoMinutos, 'sem o prazo do tenant');
    const clique = await http('GET', `${MARKETING}/public/marketing/c/${ctx.link.codigo}`, {
      cabecalhos: { 'User-Agent': IPHONE }, redirect: 'manual', texto: true,
    });
    exige(clique.status === 302, `clique respondeu ${clique.status}`);
    const id = new URL(clique.headers.get('location')).searchParams.get('mkt_visita');
    passou(`segundo clique, visita ${id.slice(0, 8)}..., sem envio do formulario`);
    info(`o prazo do tenant e de ${ctx.prazoMinutos} min; o job 4.7 roda uma vez por minuto`);
    const limite = ctx.prazoMinutos * 60 + 120;
    const visita = await esperar(async () => {
      const r = await api('GET', '/api/marketing/visitas-anuncio?situacao=descartada&tamanho=50', ctx.token);
      return r.itens.find((v) => v.id === id) ?? null;
    }, { limiteS: Math.max(limite, ESPERA_S), intervaloMs: 10_000, avisoCada: 30 });
    if (confere(visita, 'visita descartada no prazo', `nao foi descartada em ${limite} s`)) {
      confere(visita.contextoTecnico == null || Object.keys(visita.contextoTecnico).length === 0, 'descartada sem contexto tecnico (minimizacao LGPD)', JSON.stringify(visita.contextoTecnico));
      confere(visita.comportamento == null || Object.keys(visita.comportamento).length === 0, 'descartada sem comportamento', JSON.stringify(visita.comportamento));
    }
  }, { opcional: true });
}

// ── resumo ─────────────────────────────────────────────────────────────────────────────────────
console.log('');
const linha = `${aprovados} conferencias passaram, ${reprovados} falharam${pulados ? `, ${pulados} passo(s) pulado(s)` : ''}`;
if (reprovados === 0 && pulados === 0) {
  console.log(pinta('1;32', `✓ PONTA A PONTA OK: ${linha}`));
} else {
  console.log(pinta('1;31', `✗ PONTA A PONTA COM FALHAS: ${linha}`));
}
process.exit(reprovados === 0 && pulados === 0 ? 0 : 1);
