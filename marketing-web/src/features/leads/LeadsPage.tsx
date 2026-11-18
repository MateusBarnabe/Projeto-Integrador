import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { AlertCircle, FilterX, RefreshCw, Users } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import {
  Badge,
  Button,
  EmptyState,
  FilterBar,
  Input,
  Pagination,
  SearchBar,
  Select,
  Spinner,
  statusBadge,
} from '@/components/ui';
import { Dispositivo } from '@/components/InteracaoVisitante';
import {
  listarLeads,
  listarVendedoresDaEscala,
  type FiltroLeads,
  type ItemLead,
  type Pagina,
  type VendedorDaEscala,
} from './api';
import { DetalheLeadModal } from './DetalheLeadModal';
import {
  NOME_CANAL,
  NOME_FAIXA,
  NOME_ORIGEM,
  NOME_PERIODO,
  dataHora,
  intervaloDoPeriodo,
  vendedorEncurtado,
} from './formatos';

const TAMANHO = 20;

/** Os filtros ficam na URL (`?campanha=...`), como a aba de Configurações guarda a `?aba=`. */
const CHAVES_DO_FILTRO = ['q', 'etapa', 'faixa', 'origem', 'canal', 'campanha', 'vendedorId', 'periodo'] as const;
type ChaveDoFiltro = (typeof CHAVES_DO_FILTRO)[number];

const PERIODO_PERSONALIZADO = 'intervalo';

/**
 * Tela Leads (W5, norteador §9.2): barra com contador → filtros → tabela → paginação; o detalhe abre
 * em modal e tem rota própria (`/leads/{id}`), que é a rota da busca global da casca. Os filtros
 * (busca, etapa, faixa, origem, canal, campanha, vendedor e período) vivem na query da URL.
 */
export function LeadsPage() {
  const { id } = useParams();
  const navegar = useNavigate();
  const { search } = useLocation();
  const [parametros, setParametros] = useSearchParams();
  const [busca, setBusca] = useState(parametros.get('q') ?? '');
  const [campanha, setCampanha] = useState(parametros.get('campanha') ?? '');
  const [datas, setDatas] = useState(() => intervaloDoPeriodo(parametros.get('periodo') ?? undefined));
  const [intervaloAberto, setIntervaloAberto] = useState(() => !!datas.de);
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<ItemLead> | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [escala, setEscala] = useState<VendedorDaEscala[] | null>(null);
  const [escalaIndisponivel, setEscalaIndisponivel] = useState(false);

  const filtro = useMemo(() => {
    const atual: FiltroLeads = {};
    for (const chave of CHAVES_DO_FILTRO) {
      const valor = parametros.get(chave);
      if (valor) atual[chave] = valor;
    }
    return atual;
  }, [parametros]);

  const parametrosAtuais = useRef(parametros);
  parametrosAtuais.current = parametros;

  /** Grava (ou tira) um filtro da URL, sem empilhar histórico, e volta para a primeira página. */
  const gravar = useCallback(
    (chave: ChaveDoFiltro, valor: string | undefined) => {
      if ((parametrosAtuais.current.get(chave) ?? '') === (valor ?? '')) return;
      setParametros(
        (anteriores) => {
          const proximos = new URLSearchParams(anteriores);
          if (valor) proximos.set(chave, valor);
          else proximos.delete(chave);
          return proximos;
        },
        { replace: true },
      );
      setPagina(0);
    },
    [setParametros],
  );

  // A busca e a campanha esperam a pessoa parar de digitar
  useEffect(() => {
    const espera = window.setTimeout(() => gravar('q', busca.trim().length >= 2 ? busca.trim() : undefined), 300);
    return () => window.clearTimeout(espera);
  }, [busca, gravar]);

  useEffect(() => {
    const espera = window.setTimeout(() => gravar('campanha', campanha.trim() || undefined), 300);
    return () => window.clearTimeout(espera);
  }, [campanha, gravar]);

  // A escala do rodízio alimenta o filtro de vendedor; sem `marketing.qualificacao.ver` a API responde 403
  useEffect(() => {
    let ativo = true;
    listarVendedoresDaEscala()
      .then((itens) => ativo && setEscala(itens))
      .catch(() => ativo && setEscalaIndisponivel(true));
    return () => {
      ativo = false;
    };
  }, []);

  const carregar = useCallback(() => {
    setCarregando(true);
    setErro(null);
    listarLeads(filtro, pagina, TAMANHO)
      .then(setDados)
      .catch((falha) => setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar os leads.'))
      .finally(() => setCarregando(false));
  }, [filtro, pagina]);

  useEffect(carregar, [carregar]);

  const intervaloInvalido = !!datas.de && !!datas.ate && datas.ate < datas.de;
  const seletorDePeriodo = intervaloAberto
    ? PERIODO_PERSONALIZADO
    : filtro.periodo && filtro.periodo in NOME_PERIODO
      ? filtro.periodo
      : '';

  function escolherPeriodo(valor: string) {
    if (valor === PERIODO_PERSONALIZADO) {
      setIntervaloAberto(true);
      gravar('periodo', datas.de && datas.ate && !intervaloInvalido ? `${datas.de},${datas.ate}` : undefined);
      return;
    }
    setIntervaloAberto(false);
    gravar('periodo', valor || undefined);
  }

  function escolherData(campo: 'de' | 'ate', valor: string) {
    const proximas = { ...datas, [campo]: valor };
    setDatas(proximas);
    const completo = proximas.de && proximas.ate && proximas.ate >= proximas.de;
    gravar('periodo', completo ? `${proximas.de},${proximas.ate}` : undefined);
  }

  function limparFiltros() {
    setBusca('');
    setCampanha('');
    setDatas({ de: '', ate: '' });
    setIntervaloAberto(false);
    setParametros(new URLSearchParams(), { replace: true });
    setPagina(0);
  }

  // O detalhe abre e fecha mantendo os filtros da lista na URL
  const abrirLead = (leadId: string) => navegar({ pathname: `/leads/${leadId}`, search });
  const filtrando = Object.keys(filtro).length > 0;

  return (
    <div className="flex flex-1 flex-col">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-borda bg-superficie px-5 py-3">
        <p className="text-sm text-texto-2">
          <span className="font-semibold text-titulo">{dados?.total ?? '—'}</span> leads
        </p>
        <Button variant="outline" size="sm" onClick={carregar} disabled={carregando}>
          <RefreshCw aria-hidden className={`h-3 w-3 shrink-0 ${carregando ? 'animate-spin' : ''}`} />
          Atualizar
        </Button>
      </div>

      <FilterBar>
        <SearchBar
          className="w-full sm:w-72"
          placeholder="Buscar por nome ou e-mail"
          value={busca}
          onChange={(evento) => setBusca(evento.target.value)}
        />
        <Select
          aria-label="Período de entrada"
          className="w-auto"
          value={seletorDePeriodo}
          onChange={(e) => escolherPeriodo(e.target.value)}
        >
          <option value="">Todo o período</option>
          {Object.entries(NOME_PERIODO).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
          <option value={PERIODO_PERSONALIZADO}>Intervalo de datas…</option>
        </Select>
        {intervaloAberto && (
          <span className="flex items-center gap-1.5 text-xs text-texto-3">
            <Input
              type="date"
              aria-label="Entrada a partir de"
              className="w-auto"
              value={datas.de}
              max={datas.ate || undefined}
              invalid={intervaloInvalido}
              onChange={(e) => escolherData('de', e.target.value)}
            />
            até
            <Input
              type="date"
              aria-label="Entrada até"
              className="w-auto"
              value={datas.ate}
              min={datas.de || undefined}
              invalid={intervaloInvalido}
              onChange={(e) => escolherData('ate', e.target.value)}
            />
          </span>
        )}
        <Select aria-label="Etapa" className="w-auto" value={filtro.etapa ?? ''} onChange={(e) => gravar('etapa', e.target.value || undefined)}>
          <option value="">Todas as etapas</option>
          <option value="0">Etapa 0</option>
          <option value="1">Etapa 1</option>
          <option value="2">Etapa 2</option>
          <option value="3">Etapa 3</option>
        </Select>
        <Select aria-label="Faixa" className="w-auto" value={filtro.faixa ?? ''} onChange={(e) => gravar('faixa', e.target.value || undefined)}>
          <option value="">Todas as faixas</option>
          {Object.entries(NOME_FAIXA).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
        <Select aria-label="Origem" className="w-auto" value={filtro.origem ?? ''} onChange={(e) => gravar('origem', e.target.value || undefined)}>
          <option value="">Todas as origens</option>
          {Object.entries(NOME_ORIGEM).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
        <Select aria-label="Canal" className="w-auto" value={filtro.canal ?? ''} onChange={(e) => gravar('canal', e.target.value || undefined)}>
          <option value="">Todos os canais</option>
          {Object.entries(NOME_CANAL).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
        <SearchBar
          className="w-full sm:w-52"
          aria-label="Campanha (utm_campaign)"
          placeholder="Campanha (utm_campaign)"
          value={campanha}
          onChange={(evento) => setCampanha(evento.target.value)}
        />
        <Select
          aria-label="Vendedor"
          className="w-auto"
          value={filtro.vendedorId ?? ''}
          disabled={escalaIndisponivel}
          title={escalaIndisponivel ? 'A escala do rodízio não está disponível para o seu perfil.' : undefined}
          onChange={(e) => gravar('vendedorId', e.target.value || undefined)}
        >
          <option value="">{escalaIndisponivel ? 'Vendedor indisponível' : 'Todos os vendedores'}</option>
          {filtro.vendedorId && !escala?.some((v) => v.vendedorId === filtro.vendedorId) && (
            <option value={filtro.vendedorId}>{vendedorEncurtado(filtro.vendedorId)}</option>
          )}
          {escala?.map((vendedor) => (
            <option key={vendedor.vendedorId} value={vendedor.vendedorId}>
              {vendedorEncurtado(vendedor.vendedorId)}
              {!vendedor.ativo && ' (inativo)'}
            </option>
          ))}
        </Select>
        {filtrando && (
          <Button variant="ghost" size="sm" onClick={limparFiltros}>
            <FilterX aria-hidden className="h-3 w-3 shrink-0" />
            Limpar filtros
          </Button>
        )}
        {intervaloInvalido && (
          <p role="alert" className="w-full text-xs text-red-600 dark:text-red-400">
            A data final precisa ser igual ou posterior à inicial.
          </p>
        )}
      </FilterBar>

      <div className="flex-1 p-5">
        <div className="overflow-hidden rounded-xl border border-borda bg-superficie">
          {erro ? (
            <EmptyState
              icone={AlertCircle}
              erro
              titulo="Não foi possível carregar os leads"
              descricao={erro}
              acao={<Button variant="outline" size="sm" onClick={carregar}>Tentar de novo</Button>}
            />
          ) : carregando && !dados ? (
            <Spinner />
          ) : !dados?.itens.length ? (
            <EmptyState icone={Users} titulo="Nenhum lead encontrado" descricao="Ajuste os filtros ou envie um formulário pelo placeholder do Landing." />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-borda bg-superficie-2 text-xs text-texto-3">
                  <tr>
                    <th scope="col" className="px-4 py-2.5 font-medium">Lead</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Etapa</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Qualificação</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Origem</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Campanha</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Aparelho</th>
                    <th scope="col" className="px-4 py-2.5 font-medium">Entrada</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-borda">
                  {dados.itens.map((lead) => (
                    <tr
                      key={lead.id}
                      tabIndex={0}
                      onClick={() => abrirLead(lead.id)}
                      onKeyDown={(evento) => evento.key === 'Enter' && abrirLead(lead.id)}
                      className="cursor-pointer hover:bg-superficie-2 focus-visible:bg-superficie-2"
                    >
                      <td className="px-4 py-2.5">
                        <p className="font-medium text-texto">
                          {lead.anonimizado ? 'Anonimizado' : lead.nome || 'Sem nome'}
                        </p>
                        <p className="text-xs text-texto-3">{lead.email}</p>
                      </td>
                      <td className="px-4 py-2.5">
                        <span className="flex flex-wrap items-center gap-1">
                          <Badge variant="indigo">{lead.etapa}</Badge>
                          {lead.temperatura && statusBadge(lead.temperatura)}
                        </span>
                      </td>
                      <td className="px-4 py-2.5">
                        {lead.faixaQualificacao ? (
                          <span className="flex items-center gap-1.5">
                            {statusBadge(lead.faixaQualificacao, NOME_FAIXA[lead.faixaQualificacao])}
                            <span className="text-xs text-texto-3">{lead.pontuacao} pts</span>
                          </span>
                        ) : (
                          <span className="text-texto-3">—</span>
                        )}
                      </td>
                      <td className="px-4 py-2.5 text-texto-2">
                        {NOME_ORIGEM[lead.origem] ?? lead.origem}
                        {lead.canal && <span className="text-xs text-texto-3"> · {NOME_CANAL[lead.canal]}</span>}
                        {lead.crmOportunidadeId && <span className="ml-1.5">{statusBadge('no_crm', 'no CRM')}</span>}
                      </td>
                      <td className="px-4 py-2.5 text-texto-2">{lead.utmCampaign ?? '—'}</td>
                      <td className="px-4 py-2.5">
                        {lead.tipoDispositivo ? <Dispositivo tipo={lead.tipoDispositivo} /> : <span className="text-texto-3">—</span>}
                      </td>
                      <td className="px-4 py-2.5 whitespace-nowrap text-texto-3">{dataHora(lead.criadoEm)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {dados && <Pagination pagina={dados.pagina} tamanho={dados.tamanho} total={dados.total} onChange={setPagina} />}
      </div>

      <DetalheLeadModal id={id ?? null} onClose={() => navegar({ pathname: '/leads', search })} />
    </div>
  );
}
