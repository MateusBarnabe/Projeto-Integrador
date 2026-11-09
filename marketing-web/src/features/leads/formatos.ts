/** Textos e formatos compartilhados pela lista e pelo detalhe do lead (norteador §2 e §9.2). */

const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function dataHora(valor?: string | null) {
  return valor ? DATA_HORA.format(new Date(valor)) : null;
}

export const NOME_ETAPA: Record<number, string> = {
  0: 'Etapa 0 · Meta Ads',
  1: 'Etapa 1 · deixou contato',
  2: 'Etapa 2 · preenchimento parcial',
  3: 'Etapa 3 · enviou o formulário',
};

export const NOME_ORIGEM: Record<string, string> = {
  meta_ads: 'Meta Ads',
  landing_page: 'Landing page',
  manual: 'Manual',
};

export const NOME_CANAL: Record<string, string> = {
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

export const NOME_FONTE_REGRA: Record<string, string> = {
  campo_formulario: 'Resposta do formulário',
  campo_meta: 'Pergunta do Meta',
  utm: 'Origem (UTM)',
  comportamento: 'Comportamento na página',
};

export const NOME_METRICA: Record<string, string> = {
  tempo_na_pagina: 'tempo na página',
  profundidade_rolagem: 'rolagem',
  pagina_visitada: 'página visitada',
  elemento_clicado: 'elemento clicado',
  tipo_dispositivo: 'tipo de dispositivo',
};

/** Texto de cada item da timeline (`lead_eventos.tipo_evento`). */
export const NOME_EVENTO: Record<string, string> = {
  etapa_alterada: 'Etapa do funil alterada',
  respostas_lidas: 'Respostas lidas do Landing',
  comunicacao_enviada: 'E-mail enviado',
  consentimento_registrado: 'Consentimento registrado',
  qualificacao_alterada: 'Qualificação alterada',
  vendedor_atribuido: 'Vendedor atribuído',
  handoff_crm: 'Ligado à oportunidade do CRM',
  descadastrado: 'Descadastrado',
  anonimizado: 'Dados anonimizados',
  leads_unidos: 'Leads duplicados unidos',
  respostas_indisponiveis: 'Respostas indisponíveis no Landing',
  visita_vinculada: 'Veio do anúncio: visita ligada ao lead',
};

export function valorDaResposta(valor: unknown) {
  if (valor === null || valor === undefined || valor === '') return null;
  if (Array.isArray(valor)) return valor.join(', ');
  if (typeof valor === 'boolean') return valor ? 'Sim' : 'Não';
  return String(valor);
}
