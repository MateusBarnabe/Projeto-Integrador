import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { AlertCircle, Clock, Percent, RefreshCw, Users, UserX, Wifi, WifiOff } from 'lucide-react';
import { Badge, Button, EmptyState, FilterBar, Input, Select, Spinner, statusBadge } from '@/components/ui';
import { FILTRO_INICIAL, type FiltroPainel, type Painel } from './api';
import { BarrasHorizontais } from './componentes/BarrasHorizontais';
import { Bloco, SemDados } from './componentes/Bloco';
import { CartaoNumero } from './componentes/CartaoNumero';
import { SerieTemporal } from './componentes/SerieTemporal';
import { LeadZeroBloco } from './LeadZeroBloco';
import {
  NOME_CANAL,
  NOME_ETAPA,
  NOME_FAIXA,
  NOME_ORIGEM,
  dataHora,
  idCurto,
  moeda,
  numero,
  percentual,
  rotulo,
} from './formatos';
import { usePainel, type TempoReal } from './usePainel';

type TipoDePeriodo = '7d' | '30d' | '90d' | 'intervalo';

const COR_DA_FAIXA = {
  mqlQuente: 'bg-orange-500',
  mqlMorno: 'bg-yellow-400',
  nutricao: 'bg-blue-500',
  semPontuacao: 'bg-borda-forte',
} as const;

const CABECALHO = 'px-4 py-2.5 font-medium';

/**
 * Tela Painel (W6, norteador §9.1): filtros, números do topo, funil e qualificação, série temporal,
 * campanhas e canais, distribuição e leads recentes, e o lead 0 (R6). Lê o resumo uma vez e o mantém
 * vivo pelo fluxo SSE; sem o fluxo, relê a cada 30 s.
 */
export function PainelPage() {
  const [tipo, setTipo] = useState<TipoDePeriodo>('30d');
  const [de, setDe] = useState('');
  const [ate, setAte] = useState('');
  const [origem, setOrigem] = useState('');
  const [campanhaDigitada, setCampanhaDigitada] = useState('');
  const [campanha, setCampanha] = useState('');

  // A campanha espera a pessoa parar de digitar
  useEffect(() => {
    const espera = window.setTimeout(() => setCampanha(campanhaDigitada.trim()), 400);
    return () => window.clearTimeout(espera);
  }, [campanhaDigitada]);

  const intervaloIncompleto = tipo === 'intervalo' && (!de || !ate || de > ate);
  const filtro = useMemo<FiltroPainel | null>(() => {
    if (intervaloIncompleto) return null;
    return { ...FILTRO_INICIAL, periodo: tipo === 'intervalo' ? `${de},${ate}` : tipo, origem, campanha };
  }, [tipo, de, ate, origem, campanha, intervaloIncompleto]);

  const { painel, erro, carregando, tempoReal, recarregar } = usePainel(filtro);

  return (
    <div className="flex flex-1 flex-col">
      <FilterBar label="Filtros do painel">
        <Select aria-label="Período" className="w-auto" value={tipo} onChange={(e) => setTipo(e.target.value as TipoDePeriodo)}>
          <option value="7d">Últimos 7 dias</option>
          <option value="30d">Últimos 30 dias</option>
          <option value="90d">Últimos 90 dias</option>
          <option value="intervalo">Intervalo de datas</option>
        </Select>
        {tipo === 'intervalo' && (
          <>
            <Input type="date" aria-label="Data inicial" className="w-auto" value={de} max={ate || undefined} onChange={(e) => setDe(e.target.value)} />
            <span className="text-sm text-texto-3">até</span>
            <Input type="date" aria-label="Data final" className="w-auto" value={ate} min={de || undefined} onChange={(e) => setAte(e.target.value)} />
          </>
        )}
        <Select aria-label="Origem" className="w-auto" value={origem} onChange={(e) => setOrigem(e.target.value)}>
          <option value="">Todas as origens</option>
          <option value="meta_ads">Meta Ads</option>
          <option value="landing_page">Landing page</option>
        </Select>
        <Input
          aria-label="Campanha (utm_campaign)"
          className="w-full sm:w-56"
          placeholder="Campanha (utm_campaign)"
          value={campanhaDigitada}
          onChange={(e) => setCampanhaDigitada(e.target.value)}
        />
        <div className="ml-auto flex items-center gap-3">
          <IndicadorTempoReal estado={tempoReal} />
          <Button variant="outline" size="sm" onClick={recarregar} disabled={carregando || !filtro}>
            <RefreshCw aria-hidden className={`h-3 w-3 shrink-0 ${carregando ? 'animate-spin' : ''}`} />
            Atualizar
          </Button>
        </div>
      </FilterBar>

      <div className="flex flex-1 flex-col gap-4 p-5">
        {intervaloIncompleto ? (
          <EmptyState icone={Clock} titulo="Escolha o intervalo" descricao="Informe a data inicial e a final, com a inicial antes da final." />
        ) : erro ? (
          <div className="rounded-xl border border-borda bg-superficie">
            <EmptyState
              icone={AlertCircle}
              erro
              titulo="Não foi possível carregar o painel"
              descricao={erro}
              acao={
                <Button variant="outline" size="sm" onClick={recarregar}>
                  Tentar de novo
                </Button>
              }
            />
          </div>
        ) : !painel ? (
          carregando && <Spinner />
        ) : (
          <ConteudoDoPainel painel={painel} periodo={filtro?.periodo ?? '30d'} />
        )}
      </div>
    </div>
  );
}

function IndicadorTempoReal({ estado }: { estado: TempoReal }) {
  if (estado === 'conectado') {
    return (
      <Badge variant="green">
        <Wifi aria-hidden className="h-3 w-3 shrink-0" />
        Ao vivo
      </Badge>
    );
  }
  if (estado === 'indisponivel') {
    return (
      <Badge variant="yellow">
        <WifiOff aria-hidden className="h-3 w-3 shrink-0" />
        Atualização em tempo real indisponível: relendo a cada 30 s
      </Badge>
    );
  }
  return <Badge>Conectando…</Badge>;
}

function ConteudoDoPainel({ painel, periodo }: { painel: Painel; periodo: string }) {
  const { funil, qualificacao, taxaConversao, campanhas, canais, distribuicao, leadsRecentes } = painel;
  const etapas = [funil.etapa0, funil.etapa1, funil.etapa2, funil.etapa3];
  const totalDeLeads = etapas.reduce((soma, valor) => soma + valor, 0);
  const totalMql = qualificacao.mqlQuente + qualificacao.mqlMorno;
  const semLeadsNaSerie = painel.serieTemporal.every((ponto) => ponto.novosLeads === 0);

  return (
    <>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <CartaoNumero rotulo="Leads no período" valor={numero(totalDeLeads)} dica="Somados pela etapa atual" icone={Users} />
        <CartaoNumero
          rotulo="Leads que viram MQL"
          valor={percentual(taxaConversao.leadsParaMql)}
          dica={`${numero(totalMql)} MQL (quente e morno)`}
          icone={Percent}
          destaque
        />
        <CartaoNumero
          rotulo="Conversão da etapa 1 para a 3"
          valor={percentual(taxaConversao.etapa1ParaEtapa3)}
          dica="Deixaram o contato e enviaram o formulário"
          icone={Percent}
          destaque
        />
        <CartaoNumero
          rotulo="Aguardando vendedor"
          valor={numero(distribuicao.aguardandoVendedor)}
          dica="MQL fora do horário ou sem vendedor na escala"
          icone={UserX}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Bloco titulo="Funil por etapa" descricao="Quantos leads estão em cada etapa agora.">
          {totalDeLeads === 0 ? (
            <SemDados />
          ) : (
            <BarrasHorizontais
              itens={etapas.map((valor, indice) => ({
                chave: String(indice),
                rotulo: NOME_ETAPA[indice],
                valor,
                complemento: indice === 2 && valor === 0 ? 'sem fonte ainda' : undefined,
              }))}
            />
          )}
        </Bloco>

        <Bloco
          titulo="Qualificação"
          descricao="Faixa de pontuação dos leads."
          nota={<Badge variant="green">{percentual(taxaConversao.leadsParaMql)} viram MQL</Badge>}
        >
          {totalDeLeads === 0 ? (
            <SemDados />
          ) : (
            <BarrasHorizontais
              itens={[
                { chave: 'mqlQuente', rotulo: NOME_FAIXA.mql_quente, valor: qualificacao.mqlQuente, cor: COR_DA_FAIXA.mqlQuente },
                { chave: 'mqlMorno', rotulo: NOME_FAIXA.mql_morno, valor: qualificacao.mqlMorno, cor: COR_DA_FAIXA.mqlMorno },
                { chave: 'nutricao', rotulo: NOME_FAIXA.nutricao, valor: qualificacao.nutricao, cor: COR_DA_FAIXA.nutricao },
                { chave: 'semPontuacao', rotulo: 'Sem pontuação', valor: qualificacao.semPontuacao, cor: COR_DA_FAIXA.semPontuacao },
              ]}
            />
          )}
        </Bloco>
      </div>

      <Bloco titulo="Novos leads por dia" descricao="Por origem, no período do filtro.">
        {semLeadsNaSerie ? <SemDados /> : <SerieTemporal pontos={painel.serieTemporal} periodo={periodo} />}
      </Bloco>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Bloco titulo="Campanhas" descricao="Por utm_campaign.">
          {campanhas.length === 0 ? (
            <SemDados>Nenhum lead com campanha no período.</SemDados>
          ) : (
            <div className="-mx-4 -my-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-borda bg-superficie-2 text-xs text-texto-3">
                  <tr>
                    <th scope="col" className={CABECALHO}>Campanha</th>
                    <th scope="col" className={CABECALHO}>Canal</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Leads</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>MQL</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Etapa 3</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-borda">
                  {campanhas.map((campanha) => (
                    <tr key={`${campanha.utmCampaign}|${campanha.canal}`}>
                      <td className="px-4 py-2.5 font-medium text-titulo">{campanha.utmCampaign}</td>
                      <td className="px-4 py-2.5 text-texto-2">{rotulo(NOME_CANAL, campanha.canal)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.leads)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.mql)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.etapa3)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Bloco>

        <Bloco
          titulo="Canais e custo"
          descricao="Orçamento mensal dividido pelos leads e MQL do canal no período."
          nota={<Badge variant="yellow">Custo estimado</Badge>}
        >
          {canais.length === 0 ? (
            <SemDados>Nenhum canal com leads no período.</SemDados>
          ) : (
            <div className="-mx-4 -my-4 overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-borda bg-superficie-2 text-xs text-texto-3">
                  <tr>
                    <th scope="col" className={CABECALHO}>Canal</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Orçamento mensal</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Leads</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>MQL</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Custo por lead (estimado)</th>
                    <th scope="col" className={`${CABECALHO} text-right`}>Custo por MQL (estimado)</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-borda">
                  {canais.map((canal) => (
                    <tr key={canal.canal}>
                      <td className="px-4 py-2.5 font-medium text-titulo">{rotulo(NOME_CANAL, canal.canal)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{moeda(canal.orcamentoMensal)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{numero(canal.leads)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{numero(canal.mql)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{moeda(canal.custoPorLead)}</td>
                      <td className="px-4 py-2.5 text-right tabular-nums">{moeda(canal.custoPorMql)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Bloco>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Bloco titulo="Distribuição entre vendedores" descricao="Leads atribuídos pelo rodízio no período.">
          {distribuicao.porVendedor.length === 0 && distribuicao.aguardandoVendedor === 0 ? (
            <SemDados>Nenhum lead atribuído no período.</SemDados>
          ) : (
            <BarrasHorizontais
              itens={[
                ...distribuicao.porVendedor.map((vendedor) => ({
                  chave: vendedor.vendedorId,
                  rotulo: vendedor.nome ?? (
                    <span title="Nome indisponível até a X7 (usuários do identity)">Vendedor {idCurto(vendedor.vendedorId)}</span>
                  ),
                  valor: vendedor.atribuidos,
                })),
                {
                  chave: 'aguardando',
                  rotulo: 'Aguardando vendedor',
                  valor: distribuicao.aguardandoVendedor,
                  cor: 'bg-yellow-400',
                },
              ]}
            />
          )}
        </Bloco>

        <Bloco titulo="Leads recentes" descricao="Os últimos a chegar. Clique para abrir o lead.">
          {leadsRecentes.length === 0 ? (
            <SemDados>Nenhum lead no período.</SemDados>
          ) : (
            <ul className="-my-2 divide-y divide-borda">
              {leadsRecentes.map((lead) => (
                <li key={lead.id}>
                  <Link
                    to={`/leads/${lead.id}`}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg px-2 py-2.5 hover:bg-superficie-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-titulo">
                        {lead.nome ?? (lead.provisorio ? 'Novo lead (carregando os dados…)' : 'Lead anonimizado')}
                      </p>
                      <p className="text-xs text-texto-3">
                        {NOME_ETAPA[lead.etapa] ?? `Etapa ${lead.etapa}`} · {rotulo(NOME_ORIGEM, lead.origem)}
                        {lead.canal ? ` · ${rotulo(NOME_CANAL, lead.canal)}` : ''} · {dataHora(lead.criadoEm)}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-1.5">
                      {lead.faixa && statusBadge(lead.faixa, rotulo(NOME_FAIXA, lead.faixa))}
                      {lead.crmOportunidadeId && statusBadge('no_crm', 'no CRM')}
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Bloco>
      </div>

      <LeadZeroBloco leadZero={painel.leadZero} />
    </>
  );
}
