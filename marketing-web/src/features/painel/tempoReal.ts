import type { EventosDoPainel, FiltroPainel, Funil, LeadRecente, Painel, Qualificacao } from './api';
import { paraIso } from './formatos';

/**
 * Como cada mensagem do fluxo (`/painel/eventos`) muda o painel já na tela, sem buscar tudo de novo
 * (norteador §9.1: o servidor manda eventos pontuais, não o resumo recalculado).
 *
 * As contas locais são uma estimativa até a próxima leitura completa, que o hook faz com o fluxo no ar
 * (a cada minuto). Taxas de conversão e custos só mudam nessa leitura: dependem do histórico.
 */

const MAXIMO_RECENTES = 10;
const CHAVE_DA_ETAPA = ['etapa0', 'etapa1', 'etapa2', 'etapa3'] as const;

const FAIXA_PARA_CAMPO: Record<string, keyof Qualificacao> = {
  mql_quente: 'mqlQuente',
  mql_morno: 'mqlMorno',
  nutricao: 'nutricao',
};

/**
 * Se o evento dá para somar na tela. Com filtro de campanha ou intervalo de datas fechado, o evento
 * não traz o que é preciso para saber se o lead entra no recorte: a tela busca de novo.
 */
export function podeAplicarLocalmente(filtro: FiltroPainel) {
  return !filtro.campanha.trim() && /^\d+d$/.test(filtro.periodo);
}

/**
 * O bloco do lead 0 do fluxo é o do tenant inteiro, no período padrão. Com outro período ou com filtro de
 * campanha, ele não serve para a tela: ela busca de novo.
 */
export function leadZeroDoFluxoVale(filtro: FiltroPainel) {
  return !filtro.campanha.trim() && filtro.periodo === '30d';
}

const limitar = (valor: number) => Math.max(0, valor);

function mudarEtapa(funil: Funil, de: number | null, para: number | null): Funil {
  const novo = { ...funil };
  if (de !== null && CHAVE_DA_ETAPA[de]) novo[CHAVE_DA_ETAPA[de]] = limitar(novo[CHAVE_DA_ETAPA[de]] - 1);
  if (para !== null && CHAVE_DA_ETAPA[para]) novo[CHAVE_DA_ETAPA[para]] += 1;
  return novo;
}

function mudarFaixa(qualificacao: Qualificacao, de: string | null, para: string | null): Qualificacao {
  const nova = { ...qualificacao };
  const campoDe: keyof Qualificacao = (de && FAIXA_PARA_CAMPO[de]) || 'semPontuacao';
  const campoPara: keyof Qualificacao = (para && FAIXA_PARA_CAMPO[para]) || 'semPontuacao';
  if (campoDe === campoPara) return nova;
  nova[campoDe] = limitar(nova[campoDe] - 1);
  nova[campoPara] += 1;
  return nova;
}

function somarNaSerie(painel: Painel, origem: string): Painel['serieTemporal'] {
  const hoje = paraIso(new Date());
  const serie = painel.serieTemporal.map((ponto) => ({ ...ponto }));
  const existente = serie.find((ponto) => ponto.data === hoje && ponto.origem === origem);
  if (existente) existente.novosLeads += 1;
  else serie.push({ data: hoje, novosLeads: 1, origem });
  return serie;
}

type Nome = keyof EventosDoPainel;

/** Devolve o painel com o evento aplicado. Evento que não se aplica (ou que não entende) devolve o mesmo painel. */
export function aplicarEvento<N extends Nome>(painel: Painel, nome: N, dados: EventosDoPainel[N], filtro: FiltroPainel): Painel {
  if (nome === 'lead_zero_atualizado') {
    // O bloco vem inteiro e não depende de origem nem de tipo de evento
    return { ...painel, leadZero: dados as EventosDoPainel['lead_zero_atualizado'] };
  }

  switch (nome) {
    case 'lead_criado': {
      const evento = dados as EventosDoPainel['lead_criado'];
      // O filtro de origem vale para o lead criado; os demais eventos só trazem o id do lead
      if (filtro.origem && filtro.origem !== evento.origem) return painel;
      if (painel.leadsRecentes.some((lead) => lead.id === evento.leadId)) return painel;
      const provisorio: LeadRecente = {
        id: evento.leadId,
        nome: null,
        etapa: evento.etapa,
        faixa: null,
        origem: evento.origem,
        canal: null,
        criadoEm: new Date().toISOString(),
        crmOportunidadeId: null,
        provisorio: true,
      };
      return {
        ...painel,
        funil: mudarEtapa(painel.funil, null, evento.etapa),
        // Lead novo ainda não tem pontuação
        qualificacao: { ...painel.qualificacao, semPontuacao: painel.qualificacao.semPontuacao + 1 },
        serieTemporal: somarNaSerie(painel, evento.origem),
        leadsRecentes: [provisorio, ...painel.leadsRecentes].slice(0, MAXIMO_RECENTES),
      };
    }

    case 'lead_etapa_alterada': {
      const evento = dados as EventosDoPainel['lead_etapa_alterada'];
      return {
        ...painel,
        funil: mudarEtapa(painel.funil, evento.etapaAnterior, evento.etapaNova),
        leadsRecentes: painel.leadsRecentes.map((lead) => (lead.id === evento.leadId ? { ...lead, etapa: evento.etapaNova } : lead)),
      };
    }

    case 'lead_qualificado': {
      const evento = dados as EventosDoPainel['lead_qualificado'];
      const recente = painel.leadsRecentes.find((lead) => lead.id === evento.leadId);
      // Sem o lead nos recentes, não sabemos a faixa anterior: o mais comum é a primeira pontuação
      return {
        ...painel,
        qualificacao: mudarFaixa(painel.qualificacao, recente?.faixa ?? null, evento.faixa),
        leadsRecentes: painel.leadsRecentes.map((lead) => (lead.id === evento.leadId ? { ...lead, faixa: evento.faixa } : lead)),
      };
    }

    case 'lead_atribuido': {
      const evento = dados as EventosDoPainel['lead_atribuido'];
      const porVendedor = painel.distribuicao.porVendedor.map((vendedor) => ({ ...vendedor }));
      const existente = porVendedor.find((vendedor) => vendedor.vendedorId === evento.vendedorId);
      if (existente) existente.atribuidos += 1;
      else porVendedor.push({ vendedorId: evento.vendedorId, nome: null, atribuidos: 1 });
      return {
        ...painel,
        distribuicao: {
          porVendedor,
          aguardandoVendedor: limitar(painel.distribuicao.aguardandoVendedor - 1),
        },
      };
    }

    default:
      return painel;
  }
}
