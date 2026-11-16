import { requisitar } from '@/api/cliente';
import type { Comportamento, ContextoTecnico } from '@/components/InteracaoVisitante';

/** `{itens, pagina, tamanho, total}` (Contrato §8.3). */
export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
}

/** Linha de `GET /api/marketing/leads` (ItemListaLead). */
export interface ItemLead {
  id: string;
  nome: string | null;
  email: string | null;
  etapa: number;
  temperatura: string | null;
  faixaQualificacao: string | null;
  pontuacao: number | null;
  origem: string;
  canal: string | null;
  utmCampaign: string | null;
  vendedorId: string | null;
  crmOportunidadeId: string | null;
  anonimizado: boolean;
  criadoEm: string;
  tipoDispositivo: string | null;
}

export interface ItemTimeline {
  sequencia: number;
  tipo: string;
  etapaAnterior: number | null;
  etapaNova: number | null;
  payload: Record<string, unknown> | null;
  ocorridoEm: string;
}

export interface RespostaLida {
  campoId: string | null;
  rotulo: string | null;
  tipo: string | null;
  valor: unknown;
}

export interface ItemPontuacao {
  regraId: string | null;
  criterio: string;
  fonte: string;
  pontos: number;
  campoId?: string;
  campoMeta?: string;
  campoComportamento?: string;
}

export interface RegistroConsentimento {
  id: string;
  finalidade: string;
  aceito: boolean;
  registradoEm: string;
  origem: string;
  textoTermo: string | null;
  formularioId: string | null;
  envioId: string | null;
  sessaoId: string | null;
  ip: string | null;
  userAgent: string | null;
}

/** `GET /api/marketing/leads/{id}` (DetalheLead), em seções. */
export interface DetalheLead {
  id: string;
  etapa: number;
  temperatura: string | null;
  anonimizado: boolean;
  criadoEm: string;
  atualizadoEm: string;
  contato: { nome: string | null; email: string | null; telefone: string | null; whatsapp: string | null };
  origem: {
    origem: string;
    canal: string | null;
    utmSource: string | null;
    utmMedium: string | null;
    utmCampaign: string | null;
    utmTerm: string | null;
    utmContent: string | null;
    landingPageUrl: string | null;
    primeiroToque: string | null;
    ultimoToque: string | null;
    sessaoId: string | null;
    metaLeadId: string | null;
  };
  respostas: {
    formularioId: string | null;
    envioId: string | null;
    situacao: 'sem_envio' | 'aguardando_landing' | 'indisponiveis' | 'lidas';
    lidasEm: string | null;
    itens: RespostaLida[];
  };
  qualificacao: {
    pontuacao: number | null;
    faixaQualificacao: string | null;
    qualificadoEm: string | null;
    detalhe: ItemPontuacao[];
  };
  vendedor: { vendedorId: string | null; atribuidoEm: string | null };
  consentimento: { marketing: boolean; aceitoEm: string | null; historico: RegistroConsentimento[] };
  aquecimento: {
    status: string;
    tentativas: number;
    ultimaComunicacaoEm: string | null;
    boasVindasEnviadaEm: string | null;
    esgotadoEm: string | null;
    optOutEm: string | null;
  };
  crm: { empresaId: string | null; contatoId: string | null; oportunidadeId: string | null };
  timeline: ItemTimeline[];
  interacao: { contextoTecnico: ContextoTecnico | null; comportamento: Comportamento | null } | null;
}

export interface FiltroLeads {
  q?: string;
  etapa?: string;
  faixa?: string;
  origem?: string;
  canal?: string;
}

export function listarLeads(filtro: FiltroLeads, pagina: number, tamanho = 20) {
  const parametros = new URLSearchParams({ pagina: String(pagina), tamanho: String(tamanho) });
  Object.entries(filtro).forEach(([chave, valor]) => {
    if (valor) parametros.set(chave, valor);
  });
  return requisitar<Pagina<ItemLead>>(`/leads?${parametros}`);
}

export function detalharLead(id: string) {
  return requisitar<DetalheLead>(`/leads/${encodeURIComponent(id)}`);
}
