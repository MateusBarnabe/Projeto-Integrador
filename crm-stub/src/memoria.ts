/*
 * Armazenamento em memória, separado por tenant. Some ao reiniciar o processo: é stub de teste.
 * Formatos dos schemas Empresa, Contato, Funil/Etapa e Oportunidade do crm.yaml (0.5.0).
 */
import { randomUUID } from 'node:crypto';

export interface Empresa {
  id: string;
  razaoSocial: string;
  nomeFantasia: string | null;
  cnpj: string | null;
  segmento: string | null;
  porte: string | null;
  cidade: string | null;
  estado: string | null;
  origem: string;
  origemModuloId: string;
  vendedorResponsavel: string | null;
  statusComercial: 'lead' | 'prospect' | 'cliente_ativo' | 'inativo';
  criadoEm: string;
}

export interface Contato {
  id: string;
  empresaId: string;
  nome: string;
  cargo: string | null;
  email: string | null;
  telefone: string | null;
  papel: string | null;
  consentimentoLgpd: string | null;
  preferenciaComunicacao: string | null;
  origem: string | null;
  origemModuloId: string | null;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
}

export interface Etapa {
  id: string;
  funilId: string;
  nome: string;
  ordem: number;
  cor: string | null;
}

export interface Funil {
  id: string;
  nome: string;
  padrao: boolean;
  etapas: Etapa[];
}

export interface Oportunidade {
  id: string;
  titulo: string;
  empresaId: string;
  contatoId: string | null;
  unidadeId: string | null;
  funilId: string;
  etapaId: string;
  tipo: 'nova' | 'upsell' | 'cross_sell';
  produtoId: string | null;
  valorImplantacao: number | null;
  valorMrr: number | null;
  probabilidade: number | null;
  dataPrevistaFechamento: string | null;
  proximoPasso: string | null;
  dataProximoPasso: string | null;
  proximoPassoAtrasado: boolean;
  responsavelId: string | null;
  equipeId: string | null;
  status: 'aberta' | 'ganha' | 'perdida';
  motivoPerda: string | null;
  fechadaEm: string | null;
  origem: string | null;
  origemModuloId: string | null;
  criadoEm: string;
}

interface DadosDoTenant {
  empresas: Map<string, Empresa>;
  contatos: Map<string, Contato>;
  oportunidades: Map<string, Oportunidade>;
  funil: Funil;
}

const tenants = new Map<string, DadosDoTenant>();

/** Funil padrão do tenant, criado no primeiro uso. A oportunidade sem etapaId entra na primeira etapa. */
function novoFunilPadrao(): Funil {
  const funilId = randomUUID();
  const etapas = ['Primeiro contato', 'Diagnóstico', 'Proposta', 'Negociação'].map((nome, i) => ({
    id: randomUUID(),
    funilId,
    nome,
    ordem: i + 1,
    cor: null,
  }));
  return { id: funilId, nome: 'Vendas novas', padrao: true, etapas };
}

/** Dados do tenant; tudo o que um tenant enxerga sai daqui, então registro de outro tenant "não existe". */
export function doTenant(tenantId: string): DadosDoTenant {
  let dados = tenants.get(tenantId);
  if (!dados) {
    dados = { empresas: new Map(), contatos: new Map(), oportunidades: new Map(), funil: novoFunilPadrao() };
    tenants.set(tenantId, dados);
  }
  return dados;
}
