/*
 * POST /api/crm/empresas, /contatos e /oportunidades, no formato do crm.yaml (0.5.0).
 *
 * O tenant vem do cabeçalho X-Tenant-Id, como nas chamadas com token de serviço (§9.2); o stub
 * não confere o token (o identity não é simulado aqui). Registros de outro tenant não existem.
 */
import { randomUUID } from 'node:crypto';
import { Router, type Request, type Response, type NextFunction } from 'express';
import { erro, ok } from './envelope.ts';
import { doTenant, type Contato, type Empresa, type Oportunidade } from './memoria.ts';
import { publicar } from './publicador.ts';
import { ehUuid, Leitor } from './validacao.ts';

export const rotas = Router();

function tenantDe(res: Response): string {
  return res.locals.tenantId as string;
}

function exigirTenant(req: Request, res: Response, next: NextFunction): void {
  const tenantId = req.header('X-Tenant-Id');
  if (!ehUuid(tenantId)) {
    res.status(400).json(
      erro('Informe o tenant no cabeçalho X-Tenant-Id.', [
        { campo: 'X-Tenant-Id', codigo: 'CAMPO_OBRIGATORIO', detalhe: 'Cabeçalho X-Tenant-Id ausente ou fora do formato UUID.' },
      ]),
    );
    return;
  }
  res.locals.tenantId = tenantId.toLowerCase();
  next();
}

function dadosInvalidos(res: Response, mensagem: string, leitor: Leitor): void {
  res.status(400).json(erro(mensagem, leitor.erros));
}

function criado<T>(res: Response, rota: string, id: string, data: T): void {
  res.status(201).location(`${rota}/${id}`).json(ok(data));
}

rotas.use('/api/crm/empresas', exigirTenant);
rotas.use('/api/crm/contatos', exigirTenant);
rotas.use('/api/crm/oportunidades', exigirTenant);

rotas.post('/api/crm/empresas', (req, res) => {
  const dados = doTenant(tenantDe(res));
  const l = new Leitor(req.body);
  const razaoSocial = l.texto('razaoSocial', { obrigatorio: true, max: 255 });
  const nomeFantasia = l.texto('nomeFantasia', { max: 255 });
  const cnpj = l.texto('cnpj', { padrao: /^[0-9]{14}$/ });
  const segmento = l.texto('segmento');
  const porte = l.texto('porte');
  const cidade = l.texto('cidade');
  const estado = l.texto('estado', { padrao: /^[A-Za-z]{2}$/ });
  const origem = l.texto('origem', { obrigatorio: true });
  const origemModuloId = l.texto('origemModuloId', { obrigatorio: true });
  if (l.erros.length) return dadosInvalidos(res, 'Não foi possível salvar a empresa.', l);

  if (cnpj) {
    const existente = [...dados.empresas.values()].find((e) => e.cnpj === cnpj);
    if (existente) {
      res.status(409).json(
        erro(
          'Já existe empresa com este CNPJ.',
          [{ campo: 'cnpj', codigo: 'EMPRESA_DUPLICADA', detalhe: 'CNPJ já cadastrado para este tenant.' }],
          { empresaId: existente.id },
        ),
      );
      return;
    }
  }

  const empresa: Empresa = {
    id: randomUUID(),
    razaoSocial: razaoSocial!,
    nomeFantasia,
    cnpj,
    segmento,
    porte,
    cidade,
    estado,
    origem: origem!,
    origemModuloId: origemModuloId!,
    vendedorResponsavel: null,
    statusComercial: 'lead',
    criadoEm: new Date().toISOString(),
  };
  dados.empresas.set(empresa.id, empresa);
  console.log(`[crm] empresa ${empresa.id} criada (tenant ${tenantDe(res)}, origem ${empresa.origem})`);
  criado(res, '/api/crm/empresas', empresa.id, empresa);
});

rotas.post('/api/crm/contatos', (req, res) => {
  const dados = doTenant(tenantDe(res));
  const l = new Leitor(req.body);
  const empresaId = l.uuid('empresaId', true);
  const nome = l.texto('nome', { obrigatorio: true, max: 255 });
  const cargo = l.texto('cargo', { max: 100 });
  const email = l.email('email');
  const telefone = l.texto('telefone');
  const papel = l.opcao('papel', ['decisor', 'influenciador', 'tecnico', 'financeiro', 'proprietario', 'outro'] as const);
  const consentimentoLgpd = l.opcao('consentimentoLgpd', ['concedido', 'pendente', 'revogado'] as const);
  const preferenciaComunicacao = l.opcao('preferenciaComunicacao', ['telefone', 'whatsapp', 'email'] as const);
  const origem = l.texto('origem');
  const origemModuloId = l.texto('origemModuloId');
  const utmSource = l.texto('utmSource');
  const utmMedium = l.texto('utmMedium');
  const utmCampaign = l.texto('utmCampaign');
  if (empresaId && !dados.empresas.has(empresaId)) {
    l.erro('empresaId', 'NAO_ENCONTRADO', 'Empresa não encontrada.');
  }
  if (l.erros.length) return dadosInvalidos(res, 'Não foi possível salvar o contato.', l);

  if (email) {
    const existente = [...dados.contatos.values()].find(
      (c) => c.empresaId === empresaId && c.email?.toLowerCase() === email.toLowerCase(),
    );
    if (existente) {
      res.status(409).json(
        erro(
          'Já existe contato com este e-mail nesta empresa.',
          [{ campo: 'email', codigo: 'CONTATO_DUPLICADO', detalhe: 'E-mail já cadastrado para esta empresa.' }],
          { contatoId: existente.id },
        ),
      );
      return;
    }
  }

  const contato: Contato = {
    id: randomUUID(),
    empresaId: empresaId!,
    nome: nome!,
    cargo,
    email,
    telefone,
    papel,
    consentimentoLgpd,
    preferenciaComunicacao,
    origem,
    origemModuloId,
    utmSource,
    utmMedium,
    utmCampaign,
  };
  dados.contatos.set(contato.id, contato);
  console.log(`[crm] contato ${contato.id} criado na empresa ${contato.empresaId} (tenant ${tenantDe(res)})`);
  criado(res, '/api/crm/contatos', contato.id, contato);
});

rotas.post('/api/crm/oportunidades', async (req, res) => {
  const tenantId = tenantDe(res);
  const dados = doTenant(tenantId);
  const l = new Leitor(req.body);
  const titulo = l.texto('titulo', { obrigatorio: true, max: 255 });
  const empresaId = l.uuid('empresaId', true);
  const contatoId = l.uuid('contatoId');
  const unidadeId = l.uuid('unidadeId');
  const etapaId = l.uuid('etapaId');
  const tipo = l.opcao('tipo', ['nova', 'upsell', 'cross_sell'] as const) ?? 'nova';
  const produtoId = l.uuid('produtoId');
  const valorImplantacao = l.dinheiro('valorImplantacao');
  const valorMrr = l.dinheiro('valorMrr');
  const probabilidade = l.inteiro('probabilidade', 0, 100);
  const dataPrevistaFechamento = l.data('dataPrevistaFechamento');
  const proximoPasso = l.texto('proximoPasso', { max: 255 });
  const dataProximoPasso = l.data('dataProximoPasso');
  const responsavelId = l.uuid('responsavelId');
  const origem = l.texto('origem', { max: 30 });
  const origemModuloId = l.texto('origemModuloId', { max: 100 });

  if (empresaId && !dados.empresas.has(empresaId)) l.erro('empresaId', 'NAO_ENCONTRADO', 'Empresa não encontrada.');
  if (contatoId && !dados.contatos.has(contatoId)) l.erro('contatoId', 'NAO_ENCONTRADO', 'Contato não encontrado.');
  // O stub não guarda unidades: qualquer unidadeId informado é inexistente
  if (unidadeId) l.erro('unidadeId', 'NAO_ENCONTRADO', 'Unidade não encontrada.');
  const etapa = etapaId ? dados.funil.etapas.find((e) => e.id === etapaId) : dados.funil.etapas[0];
  if (!etapa) l.erro('etapaId', 'NAO_ENCONTRADO', 'Etapa não encontrada.');
  if (l.erros.length || !etapa) return dadosInvalidos(res, 'Não foi possível salvar a oportunidade.', l);

  // Criada por serviço (o caso do Landing): sem responsável e, sem próximo passo, "Fazer o primeiro contato" hoje
  const hoje = new Date().toISOString().slice(0, 10);
  const oportunidade: Oportunidade = {
    id: randomUUID(),
    titulo: titulo!,
    empresaId: empresaId!,
    contatoId,
    unidadeId: null,
    funilId: dados.funil.id,
    etapaId: etapa.id,
    tipo,
    produtoId,
    valorImplantacao,
    valorMrr,
    probabilidade,
    dataPrevistaFechamento,
    proximoPasso: proximoPasso ?? 'Fazer o primeiro contato',
    dataProximoPasso: dataProximoPasso ?? hoje,
    proximoPassoAtrasado: false,
    responsavelId,
    equipeId: null,
    status: 'aberta',
    motivoPerda: null,
    fechadaEm: null,
    origem,
    origemModuloId,
    criadoEm: new Date().toISOString(),
  };
  dados.oportunidades.set(oportunidade.id, oportunidade);
  console.log(
    `[crm] oportunidade ${oportunidade.id} criada (tenant ${tenantId}, origem ${origem ?? '-'}, origemModuloId ${origemModuloId ?? '-'})`,
  );

  // "Depois do commit": a oportunidade já está gravada; se o RabbitMQ falhar, ela continua criada
  try {
    const evento = await publicar(
      'crm.oportunidade.criada',
      tenantId,
      {
        oportunidadeId: oportunidade.id,
        titulo: oportunidade.titulo,
        empresaId: oportunidade.empresaId,
        contatoId: oportunidade.contatoId,
        funilId: oportunidade.funilId,
        etapaId: oportunidade.etapaId,
        tipo: oportunidade.tipo,
        responsavelId: oportunidade.responsavelId,
        valorImplantacao: oportunidade.valorImplantacao,
        valorMrr: oportunidade.valorMrr,
        origem: oportunidade.origem,
        origemModuloId: oportunidade.origemModuloId,
      },
      { usuarioId: null, correlacaoId: req.header('X-Request-Id') ?? null },
    );
    console.log(`[crm] evento ${evento.id} crm.oportunidade.criada publicado em crm.eventos`);
  } catch (e) {
    const motivo = (e as Error & { code?: string }).message || (e as { code?: string }).code || String(e);
    console.error(`[crm] crm.oportunidade.criada NÃO publicado (oportunidade ${oportunidade.id}): ${motivo}`);
  }

  criado(res, '/api/crm/oportunidades', oportunidade.id, oportunidade);
});
