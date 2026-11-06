/*
 * Handoff do envio para o CRM, como o Landing real faz (landing.yaml, "Como o CRM recebe o lead",
 * e Contrato §12.7): depois de gravar o envio e antes de publicar `landing.formulario.recebido`,
 * cria ou reaproveita a empresa e o contato e cria a oportunidade, por `POST /api/crm/**`
 * (crm.yaml), com o tenant do formulário em `X-Tenant-Id` e timeout de 3 s por chamada.
 *
 * O token vai como um Bearer qualquer: o stub do CRM não confere (o identity não é simulado).
 * Os dados de empresa e contato saem do `mapeamentoCrm` dos campos do formulário.
 */
import { config } from './config.ts';
import type { EnvioGravado } from './envios.ts';
import type { Formulario } from './formulario.ts';

const TIMEOUT_MS = 3000;
const TOKEN_DE_SERVICO = 'placeholder-landing';

export interface IdsDoCrm {
  empresaId: string | null;
  contatoId: string | null;
  oportunidadeId: string | null;
}

/** Falha do CRM com o motivo já sem dado pessoal (só status e códigos de erro). */
class FalhaCrm extends Error {}

interface CorpoCrm {
  success?: boolean;
  data?: Record<string, unknown> | null;
  errors?: { campo: string; codigo: string }[];
}

/**
 * `POST` no CRM. Devolve o id criado (`201`) ou, no `409` de duplicidade indicado, o id existente
 * que vem em `data[campoDoId]`. Qualquer outra resposta, timeout ou conexão recusada vira `FalhaCrm`.
 */
async function postar(
  caminho: string,
  tenantId: string,
  corpo: Record<string, unknown>,
  correlacaoId: string | null,
  duplicidade?: { codigo: string; campoDoId: string },
): Promise<{ id: string; reaproveitado: boolean }> {
  let resposta: Response;
  try {
    resposta = await fetch(`${config.crmBaseUrl}${caminho}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN_DE_SERVICO}`,
        'X-Tenant-Id': tenantId,
        ...(correlacaoId ? { 'X-Request-Id': correlacaoId } : {}),
      },
      body: JSON.stringify(corpo),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (falha) {
    // ECONNREFUSED chega em `cause`; timeout, como TimeoutError
    const causa = (falha as { cause?: { code?: string } }).cause?.code;
    const nome = falha instanceof Error ? falha.name : String(falha);
    throw new FalhaCrm(`${caminho}: ${causa ?? (nome === 'TimeoutError' ? `sem resposta em ${TIMEOUT_MS} ms` : nome)}`);
  }

  const json = (await resposta.json().catch(() => null)) as CorpoCrm | null;
  const idCriado = json?.data?.id;
  if (resposta.status === 201 && typeof idCriado === 'string') return { id: idCriado, reaproveitado: false };

  const idExistente = duplicidade && json?.data?.[duplicidade.campoDoId];
  if (resposta.status === 409 && json?.errors?.some((e) => e.codigo === duplicidade?.codigo) && typeof idExistente === 'string') {
    return { id: idExistente, reaproveitado: true };
  }
  const codigos = json?.errors?.map((e) => `${e.campo}:${e.codigo}`).join(', ') || 'sem detalhe';
  throw new FalhaCrm(`${caminho}: HTTP ${resposta.status} (${codigos})`);
}

/** Valores das respostas por destino do `mapeamentoCrm` (`empresa.<atributo>` e `contato.<atributo>`). */
function mapearRespostas(formulario: Formulario, respostas: EnvioGravado['respostas']) {
  const empresa: Record<string, unknown> = {};
  const contato: Record<string, unknown> = {};
  for (const campo of formulario.campos) {
    const [destino, atributo] = campo.mapeamentoCrm?.split('.') ?? [];
    const valor = respostas[campo.id];
    if (!atributo || valor === null || valor === undefined || valor === '') continue;
    // `empresa.nome` é a razão social do NovaEmpresa
    if (destino === 'empresa') empresa[atributo === 'nome' ? 'razaoSocial' : atributo] = valor;
    else if (destino === 'contato') contato[atributo] = valor;
  }
  return { empresa, contato };
}

/**
 * Cria ou reaproveita a empresa e o contato e cria a oportunidade (`origem: landing`,
 * `origemModuloId` = id do envio). Nunca lança: na primeira falha, para e devolve os ids que
 * conseguiu (os demais `null`), e o envio segue sem eles, como manda o contrato.
 */
export async function criarNoCrm(envio: EnvioGravado, formulario: Formulario, correlacaoId: string | null): Promise<IdsDoCrm> {
  const ids: IdsDoCrm = { empresaId: null, contatoId: null, oportunidadeId: null };
  const { empresa, contato } = mapearRespostas(formulario, envio.respostas);
  const rastreio = { origem: 'landing', origemModuloId: envio.id };
  const ultima = envio.origem.ultima;
  const situacao: string[] = [];

  try {
    const e = await postar('/api/crm/empresas', envio.tenantId, {
      ...empresa,
      // Sem campo de empresa no formulário, a pessoa vira a própria empresa (o CRM exige razão social)
      razaoSocial: empresa.razaoSocial ?? contato.nome ?? contato.email ?? 'Sem nome',
      ...rastreio,
    }, correlacaoId, { codigo: 'EMPRESA_DUPLICADA', campoDoId: 'empresaId' });
    ids.empresaId = e.id;
    situacao.push(`empresa ${e.id} ${e.reaproveitado ? 'reaproveitada' : 'criada'}`);

    const c = await postar('/api/crm/contatos', envio.tenantId, {
      ...contato,
      empresaId: e.id,
      nome: contato.nome ?? contato.email ?? contato.telefone ?? 'Sem nome',
      // Sem aceite nada é gravado; o termo aceito é o do formulário
      consentimentoLgpd: 'concedido',
      utmSource: ultima.utmSource,
      utmMedium: ultima.utmMedium,
      utmCampaign: ultima.utmCampaign,
      ...rastreio,
    }, correlacaoId, { codigo: 'CONTATO_DUPLICADO', campoDoId: 'contatoId' });
    ids.contatoId = c.id;
    situacao.push(`contato ${c.id} ${c.reaproveitado ? 'reaproveitado' : 'criado'}`);

    // Título sem dado pessoal: ele vai no crm.oportunidade.criada
    const o = await postar('/api/crm/oportunidades', envio.tenantId, {
      titulo: `Formulário: ${formulario.nome}`.slice(0, 255),
      empresaId: e.id,
      contatoId: c.id,
      tipo: 'nova',
      ...rastreio,
    }, correlacaoId);
    ids.oportunidadeId = o.id;
    situacao.push(`oportunidade ${o.id} criada`);
    console.log(`Envio ${envio.id} no CRM: ${situacao.join(', ')}`);
  } catch (falha) {
    const motivo = falha instanceof FalhaCrm ? falha.message : String(falha);
    const feito = situacao.length ? `${situacao.join(', ')}; ` : '';
    console.error(`Envio ${envio.id}: CRM falhou (${motivo}); ${feito}o evento sai sem os ids que faltaram`);
  }
  return ids;
}
