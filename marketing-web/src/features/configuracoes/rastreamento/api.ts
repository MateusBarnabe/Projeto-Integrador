import { requisitar } from '@/api/cliente';
import type { Comportamento, ContextoTecnico } from '@/components/InteracaoVisitante';
import type { Pagina } from '@/features/leads/api';

/** `GET/PUT /api/marketing/rastreamento` (R1). */
export interface ConfiguracaoRastreamento {
  ativo: boolean;
  prazoConversaoMinutos: number;
  /** Endereço do script que a landing page carrega. */
  urlScript: string;
}

export interface LinkCampanha {
  id: string;
  codigo: string;
  nome: string;
  urlDestino: string;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  utmTerm: string | null;
  utmContent: string | null;
  ativo: boolean;
  /** O que vai no anúncio. */
  urlPublica: string;
  visitas: number;
  criadoEm: string;
}

export type PedidoLink = Pick<
  LinkCampanha,
  'nome' | 'urlDestino' | 'utmSource' | 'utmMedium' | 'utmCampaign' | 'utmTerm' | 'utmContent' | 'ativo'
>;

export type SituacaoVisita = 'ativa' | 'convertida' | 'descartada';

/** Visita de anúncio: o "lead 0". */
export interface VisitaAnuncio {
  id: string;
  linkId: string;
  linkNome: string | null;
  situacao: SituacaoVisita;
  clicadoEm: string;
  ultimoSinalEm: string | null;
  expiraEm: string;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  contextoTecnico: ContextoTecnico | null;
  comportamento: Comportamento | null;
  leadId: string | null;
  convertidaEm: string | null;
  descartadaEm: string | null;
}

export interface ResumoVisitas {
  ativas: number;
  convertidas: number;
  descartadas: number;
  total: number;
  /** Pontos percentuais (12.5 = 12,5%). */
  taxaConversao: number;
}

export const obterConfiguracao = () => requisitar<ConfiguracaoRastreamento>('/rastreamento');

export const salvarConfiguracao = (ativo: boolean, prazoConversaoMinutos: number) =>
  requisitar<ConfiguracaoRastreamento>('/rastreamento', {
    method: 'PUT',
    body: JSON.stringify({ ativo, prazoConversaoMinutos }),
  });

export const listarLinks = () => requisitar<Pagina<LinkCampanha>>('/links-campanha?tamanho=100');

export const criarLink = (pedido: PedidoLink) =>
  requisitar<LinkCampanha>('/links-campanha', { method: 'POST', body: JSON.stringify(pedido) });

export const alterarLink = (id: string, pedido: PedidoLink) =>
  requisitar<LinkCampanha>(`/links-campanha/${id}`, { method: 'PUT', body: JSON.stringify(pedido) });

export const excluirLink = (id: string) => requisitar<null>(`/links-campanha/${id}`, { method: 'DELETE' });

export const listarVisitas = (situacao?: SituacaoVisita) =>
  requisitar<Pagina<VisitaAnuncio>>(`/visitas-anuncio?tamanho=20${situacao ? `&situacao=${situacao}` : ''}`);

export const resumirVisitas = () => requisitar<ResumoVisitas>('/visitas-anuncio/resumo');
