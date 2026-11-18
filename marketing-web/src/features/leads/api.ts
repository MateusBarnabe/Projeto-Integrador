import { requisitar, requisitarModulo } from '@/api/cliente';
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

export interface ComunicacaoEnviada {
  id: string;
  canal: string;
  modeloId: string;
  modeloNome: string | null;
  tipoModelo: string | null;
  status: string;
  enviadoEm: string;
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
    comunicacoes?: ComunicacaoEnviada[];
  };
  crm: { empresaId: string | null; contatoId: string | null; oportunidadeId: string | null };
  timeline: ItemTimeline[];
  interacao: { contextoTecnico: ContextoTecnico | null; comportamento: Comportamento | null } | null;
}

/** Filtros da lista, com os mesmos nomes dos parâmetros de `GET /api/marketing/leads`. */
export interface FiltroLeads {
  q?: string;
  etapa?: string;
  faixa?: string;
  origem?: string;
  canal?: string;
  /** `utm_campaign` exato. */
  campanha?: string;
  vendedorId?: string;
  /** `7d`, `30d`, `90d` ou `AAAA-MM-DD,AAAA-MM-DD`. */
  periodo?: string;
}

/** Item de `GET /api/marketing/rodizio/vendedores` (escala do rodízio). */
export interface VendedorDaEscala {
  id: string;
  vendedorId: string;
  ativo: boolean;
  ultimaAtribuicaoEm: string | null;
}

/** `PUT /api/marketing/leads/{id}/vendedor`. */
export interface VendedorDoLead {
  leadId: string;
  vendedorId: string | null;
  vendedorAtribuidoEm: string | null;
}

/** `GET /api/crm/empresas/{id}/resumo` (crm.yaml, EmpresaResumo). */
export interface ResumoEmpresaCrm {
  id: string;
  razaoSocial: string;
  cnpj: string | null;
  cidade: string | null;
}

/** `GET /api/crm/contatos/{id}/resumo` (crm.yaml, ContatoResumo). */
export interface ResumoContatoCrm {
  id: string;
  nome: string;
  cargo: string | null;
  email: string | null;
  telefone: string | null;
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

/** Escala do rodízio (precisa de `marketing.qualificacao.ver`; sem ela a API responde `403`). */
export function listarVendedoresDaEscala() {
  return requisitar<VendedorDaEscala[]>('/rodizio/vendedores');
}

/** Reatribuição manual (precisa de `marketing.qualificacao.editar`). `422 VENDEDOR_FORA_DA_ESCALA` se não está na escala. */
export function reatribuirVendedor(leadId: string, vendedorId: string) {
  return requisitar<VendedorDoLead>(`/leads/${encodeURIComponent(leadId)}/vendedor`, {
    method: 'PUT',
    body: JSON.stringify({ vendedorId }),
  });
}

/** Resumos que o CRM oferece a outros módulos, com o token do usuário (norteador §9.2 e §9.6). */
export function resumirEmpresaNoCrm(id: string) {
  return requisitarModulo<ResumoEmpresaCrm>('crm', `/empresas/${encodeURIComponent(id)}/resumo`);
}

export function resumirContatoNoCrm(id: string) {
  return requisitarModulo<ResumoContatoCrm>('crm', `/contatos/${encodeURIComponent(id)}/resumo`);
}
