import { requisitar } from '@/api/cliente';

/**
 * Tipos de `GET /api/marketing/painel` (norteador §9.1, com o bloco `leadZero` da R6) e dos dados das
 * mensagens SSE de `GET /api/marketing/painel/eventos`. Percentuais vêm em pontos (22.2222 = 22,22%) e
 * dinheiro com 2 casas (§8.7).
 */

export interface Funil {
  etapa0: number;
  etapa1: number;
  etapa2: number;
  etapa3: number;
}

export interface Qualificacao {
  mqlQuente: number;
  mqlMorno: number;
  nutricao: number;
  semPontuacao: number;
}

export interface PontoDaSerie {
  /** `AAAA-MM-DD` */
  data: string;
  novosLeads: number;
  origem: string;
}

export interface TaxaConversao {
  etapa1ParaEtapa3: number;
  leadsParaMql: number;
}

export interface CampanhaDoPainel {
  utmCampaign: string;
  canal: string;
  leads: number;
  mql: number;
  etapa3: number;
}

export interface CanalDoPainel {
  canal: string;
  orcamentoMensal: number | null;
  leads: number;
  mql: number;
  custoPorLead: number | null;
  custoPorMql: number | null;
}

export interface VendedorDoPainel {
  vendedorId: string;
  /** Sempre nulo até a X7 (nome vem do identity). */
  nome: string | null;
  atribuidos: number;
}

export interface Distribuicao {
  porVendedor: VendedorDoPainel[];
  aguardandoVendedor: number;
}

export interface LeadRecente {
  id: string;
  /** Nulo em lead anonimizado. */
  nome: string | null;
  etapa: number;
  faixa: string | null;
  origem: string;
  canal: string | null;
  criadoEm: string;
  crmOportunidadeId: string | null;
  /** Só no front: linha criada por `lead_criado`, antes de a próxima leitura trazer os dados do lead. */
  provisorio?: boolean;
}

export interface LeadZeroPorCampanha {
  utmCampaign: string;
  cliques: number;
  convertidas: number;
  descartadas: number;
}

export interface LeadZeroPorDispositivo {
  tipoDispositivo: string;
  cliques: number;
  convertidas: number;
}

/** Ponto de IoT (R1 a R3): visitas vindas de anúncio, antes de virarem lead. */
export interface LeadZero {
  cliques: number;
  ativas: number;
  convertidas: number;
  descartadas: number;
  taxaConversao: number;
  porCampanha: LeadZeroPorCampanha[];
  porDispositivo: LeadZeroPorDispositivo[];
}

export interface Painel {
  funil: Funil;
  qualificacao: Qualificacao;
  serieTemporal: PontoDaSerie[];
  taxaConversao: TaxaConversao;
  campanhas: CampanhaDoPainel[];
  canais: CanalDoPainel[];
  distribuicao: Distribuicao;
  leadsRecentes: LeadRecente[];
  leadZero: LeadZero;
}

export interface FiltroPainel {
  /** `7d`, `30d`, `90d` ou `AAAA-MM-DD,AAAA-MM-DD`. */
  periodo: string;
  /** `meta_ads`, `landing_page` ou vazio (todas). */
  origem: string;
  /** `utm_campaign` exato; vazio = todas. */
  campanha: string;
}

export const FILTRO_INICIAL: FiltroPainel = { periodo: '30d', origem: '', campanha: '' };

/** Dados das mensagens do fluxo (só ids e números, nunca dado pessoal). */
export interface EventosDoPainel {
  lead_criado: { leadId: string; etapa: number; origem: string };
  lead_etapa_alterada: { leadId: string; etapaAnterior: number; etapaNova: number };
  lead_qualificado: { leadId: string; faixa: string | null; pontuacao: number | null };
  lead_atribuido: { leadId: string; vendedorId: string };
  lead_zero_atualizado: LeadZero;
}

export function obterPainel(filtro: FiltroPainel, sinal?: AbortSignal) {
  const parametros = new URLSearchParams({ periodo: filtro.periodo || '30d' });
  if (filtro.origem) parametros.set('origem', filtro.origem);
  if (filtro.campanha.trim()) parametros.set('campanha', filtro.campanha.trim());
  return requisitar<Painel>(`/painel?${parametros}`, { signal: sinal });
}
