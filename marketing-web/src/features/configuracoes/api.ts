import { requisitar } from '@/api/cliente';
import type { Pagina } from '@/features/leads/api';

// --- Canais de anúncio (/canais-anuncio) -------------------------------------

export type CanalAnuncioCodigo = 'google_ads' | 'meta_ads';

export interface CanalAnuncio {
  id: string;
  canal: CanalAnuncioCodigo;
  orcamentoMensal: number | null;
  publicoAlvo: string | null;
  objetivo: string | null;
  ativo: boolean;
  metaPageId: string | null;
  metaAppSecretConfigurado: boolean;
  metaVerifyTokenConfigurado: boolean;
  metaPageAccessTokenConfigurado: boolean;
  webhookUrl: string | null;
  atualizadoEm: string | null;
}

export interface PedidoCanalAnuncio {
  canal: CanalAnuncioCodigo;
  orcamentoMensal: number | null;
  publicoAlvo: string | null;
  objetivo: string | null;
  ativo: boolean;
  metaPageId?: string | null;
  metaAppSecret?: string | null;
  metaVerifyToken?: string | null;
  metaPageAccessToken?: string | null;
}

export const listarCanaisAnuncio = () => requisitar<CanalAnuncio[]>('/canais-anuncio');

export const salvarCanalAnuncio = (pedido: PedidoCanalAnuncio) =>
  requisitar<CanalAnuncio>('/canais-anuncio', {
    method: 'PUT',
    body: JSON.stringify(pedido),
  });

// --- Modelos de mensagem (/modelos-mensagem) ---------------------------------

export type TipoModeloMensagem = 'boas_vindas' | 'aquecimento';

export interface ModeloMensagem {
  id: string;
  tipo: TipoModeloMensagem;
  nome: string;
  canal: string;
  etapaAlvo: number | null;
  ordemSequencia: number | null;
  diasAposAnterior: number | null;
  assunto: string;
  corpo: string;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface PedidoModeloMensagem {
  tipo: TipoModeloMensagem;
  nome: string;
  etapaAlvo: number | null;
  ordemSequencia: number | null;
  diasAposAnterior: number | null;
  assunto: string;
  corpo: string;
  ativo: boolean;
}

export const listarModelosMensagem = () =>
  requisitar<Pagina<ModeloMensagem>>('/modelos-mensagem?tamanho=100');

export const criarModeloMensagem = (pedido: PedidoModeloMensagem) =>
  requisitar<ModeloMensagem>('/modelos-mensagem', {
    method: 'POST',
    body: JSON.stringify(pedido),
  });

export const alterarModeloMensagem = (id: string, pedido: PedidoModeloMensagem) =>
  requisitar<ModeloMensagem>(`/modelos-mensagem/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(pedido),
  });

export const excluirModeloMensagem = (id: string) =>
  requisitar<null>(`/modelos-mensagem/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });

// --- Ciclo de vida (/ciclo-vida) ---------------------------------------------

export interface ConfiguracaoCicloVida {
  maxTentativasAquecimento: number;
  mesesRetencaoPosEsgotamento: number;
  mesesRetencaoPosOptout: number;
  atualizadoEm: string | null;
}

export const obterCicloVida = () => requisitar<ConfiguracaoCicloVida>('/ciclo-vida');

export const salvarCicloVida = (
  maxTentativasAquecimento: number,
  mesesRetencaoPosEsgotamento: number,
  mesesRetencaoPosOptout: number,
) =>
  requisitar<ConfiguracaoCicloVida>('/ciclo-vida', {
    method: 'PUT',
    body: JSON.stringify({
      maxTentativasAquecimento,
      mesesRetencaoPosEsgotamento,
      mesesRetencaoPosOptout,
    }),
  });

// --- Qualificação e distribuição (/qualificacao, /regras-pontuacao, /rodizio/vendedores) ---

export interface PreviaFaixas {
  mqlQuente: number;
  mqlMorno: number;
  nutricao: number;
  semPontuacao: number;
}

export interface ConfiguracaoQualificacao {
  limiteMqlQuente: number;
  limiteMqlMorno: number;
  rodizioAtivo: boolean;
  horarioComercialInicio: string;
  horarioComercialFim: string;
  diasAtendimento: number[];
  minutosTransbordo: number | null;
  previaFaixas: PreviaFaixas;
}

export interface PedidoQualificacao {
  limiteMqlQuente: number;
  limiteMqlMorno: number;
  rodizioAtivo: boolean;
  horarioComercialInicio: string;
  horarioComercialFim: string;
  diasAtendimento: number[];
  minutosTransbordo: number | null;
}

export interface CampoFormularioResumo {
  id: string;
  rotulo: string;
  tipo: string;
  obrigatorio: boolean;
  opcoes: string[];
}

export interface FormularioQualificacao {
  id: string;
  nome: string;
  status: string;
  disponivelNoLanding: boolean;
  campos: CampoFormularioResumo[];
}

export type FonteRegra = 'campo_formulario' | 'campo_meta' | 'utm' | 'comportamento';

export interface RegraPontuacao {
  id: string;
  criterio: string;
  fonte: FonteRegra;
  formularioId: string | null;
  campoId: string | null;
  campoMeta: string | null;
  campoComportamento: string | null;
  valores: unknown[];
  pontos: number;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface PedidoRegraPontuacao {
  criterio: string;
  fonte: FonteRegra;
  formularioId: string | null;
  campoId: string | null;
  campoMeta: string | null;
  campoComportamento: string | null;
  valores: unknown[];
  pontos: number;
  ativo: boolean;
}

export interface VendedorRodizio {
  id: string;
  vendedorId: string;
  ativo: boolean;
  ultimaAtribuicaoEm: string | null;
}

export const obterQualificacao = () => requisitar<ConfiguracaoQualificacao>('/qualificacao');

export const salvarQualificacao = (pedido: PedidoQualificacao) =>
  requisitar<ConfiguracaoQualificacao>('/qualificacao', {
    method: 'PUT',
    body: JSON.stringify(pedido),
  });

export const listarFormulariosQualificacao = () =>
  requisitar<FormularioQualificacao[]>('/qualificacao/formularios');

export const listarRegrasPontuacao = () => requisitar<RegraPontuacao[]>('/regras-pontuacao');

export const criarRegraPontuacao = (pedido: PedidoRegraPontuacao) =>
  requisitar<RegraPontuacao>('/regras-pontuacao', {
    method: 'POST',
    body: JSON.stringify(pedido),
  });

export const alterarRegraPontuacao = (id: string, pedido: PedidoRegraPontuacao) =>
  requisitar<RegraPontuacao>(`/regras-pontuacao/${encodeURIComponent(id)}`, {
    method: 'PUT',
    body: JSON.stringify(pedido),
  });

export const excluirRegraPontuacao = (id: string) =>
  requisitar<null>(`/regras-pontuacao/${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });

export const listarVendedoresRodizio = () => requisitar<VendedorRodizio[]>('/rodizio/vendedores');

export const salvarVendedoresRodizio = (vendedores: { vendedorId: string; ativo: boolean }[]) =>
  requisitar<VendedorRodizio[]>('/rodizio/vendedores', {
    method: 'PUT',
    body: JSON.stringify({ vendedores }),
  });
