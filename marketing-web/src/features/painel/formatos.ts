/** Textos e formatos do Painel (norteador §9.1). Percentuais chegam em pontos; dinheiro, com 2 casas. */

const NUMERO = new Intl.NumberFormat('pt-BR');
const PERCENTUAL = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });
const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const DIA_MES = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });

export const numero = (valor: number) => NUMERO.format(valor);

/** `22.2222` (pontos) vira `22,2%`. */
export const percentual = (pontos: number | null | undefined) =>
  pontos === null || pontos === undefined ? '—' : `${PERCENTUAL.format(pontos)}%`;

export const moeda = (valor: number | null | undefined) =>
  valor === null || valor === undefined ? '—' : MOEDA.format(valor);

export const dataHora = (valor: string) => DATA_HORA.format(new Date(valor));

/** `AAAA-MM-DD` sem passar por fuso (new Date('2026-11-01') viraria 31/10 no Brasil). */
export function dataLocal(iso: string) {
  const [ano, mes, dia] = iso.split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

export const diaEMes = (iso: string) => DIA_MES.format(dataLocal(iso));

export function paraIso(data: Date) {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

export const NOME_ORIGEM: Record<string, string> = {
  meta_ads: 'Meta Ads',
  landing_page: 'Landing page',
  manual: 'Manual',
};

export const NOME_CANAL: Record<string, string> = {
  meta_ads: 'Meta Ads',
  google_ads: 'Google Ads',
  formulario: 'Formulário',
  landing_page: 'Landing page',
  embed: 'Embed',
  chatbot: 'Chatbot',
};

export const NOME_FAIXA: Record<string, string> = {
  mql_quente: 'MQL quente',
  mql_morno: 'MQL morno',
  nutricao: 'Nutrição',
};

export const NOME_ETAPA = ['Etapa 0 · Meta Ads', 'Etapa 1 · deixou contato', 'Etapa 2 · preenchimento parcial', 'Etapa 3 · enviou o formulário'];

export const NOME_DISPOSITIVO: Record<string, string> = {
  smartphone: 'Celular',
  desktop: 'Computador',
  tablet: 'Tablet',
  outro: 'Outro',
};

export const rotulo = (tabela: Record<string, string>, chave: string) => tabela[chave] ?? chave.replace(/_/g, ' ');

/** Id do vendedor encurtado; o nome só vem com a X7. */
export const idCurto = (id: string) => id.slice(0, 8);
