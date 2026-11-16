import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { AlertCircle, RefreshCw, Users } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import {
  Badge,
  Button,
  EmptyState,
  FilterBar,
  Pagination,
  SearchBar,
  Select,
  Spinner,
  statusBadge,
} from '@/components/ui';
import { Dispositivo } from '@/components/InteracaoVisitante';
import { listarLeads, type FiltroLeads, type ItemLead, type Pagina } from './api';
import { DetalheLeadModal } from './DetalheLeadModal';
import { NOME_CANAL, NOME_FAIXA, NOME_ORIGEM, dataHora } from './formatos';

const TAMANHO = 20;

/**
 * Tela Leads (W5, norteador §9.2): barra com contador → filtros → tabela → paginação; o detalhe abre
 * em modal e tem rota própria (`/leads/{id}`), que é a rota da busca global da casca.
 */
export function LeadsPage() {
  const { id } = useParams();
  const navegar = useNavigate();
  const [filtro, setFiltro] = useState<FiltroLeads>({});
  const [busca, setBusca] = useState('');
  const [pagina, setPagina] = useState(0);
  const [dados, setDados] = useState<Pagina<ItemLead> | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);

  // A busca espera a pessoa parar de digitar
  useEffect(() => {
    const espera = window.setTimeout(() => {
      setFiltro((atual) => ({ ...atual, q: busca.trim().length >= 2 ? busca.trim() : undefined }));
      setPagina(0);
    }, 300);
    return () => window.clearTimeout(espera);
  }, [busca]);

  const carregar = useCallback(() => {
    setCarregando(true);
    setErro(null);
    listarLeads(filtro, pagina, TAMANHO)
      .then(setDados)
      .catch((falha) => setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar os leads.'))
      .finally(() => setCarregando(false));
  }, [filtro, pagina]);

  useEffect(carregar, [carregar]);

  function filtrar(chave: keyof FiltroLeads, valor: string) {
    setFiltro((atual) => ({ ...atual, [chave]: valor || undefined }));
    setPagina(0);
  }

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
        <Select aria-label="Etapa" className="w-auto" value={filtro.etapa ?? ''} onChange={(e) => filtrar('etapa', e.target.value)}>
          <option value="">Todas as etapas</option>
          <option value="0">Etapa 0</option>
          <option value="1">Etapa 1</option>
          <option value="2">Etapa 2</option>
          <option value="3">Etapa 3</option>
        </Select>
        <Select aria-label="Faixa" className="w-auto" value={filtro.faixa ?? ''} onChange={(e) => filtrar('faixa', e.target.value)}>
          <option value="">Todas as faixas</option>
          {Object.entries(NOME_FAIXA).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
        <Select aria-label="Origem" className="w-auto" value={filtro.origem ?? ''} onChange={(e) => filtrar('origem', e.target.value)}>
          <option value="">Todas as origens</option>
          {Object.entries(NOME_ORIGEM).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
        <Select aria-label="Canal" className="w-auto" value={filtro.canal ?? ''} onChange={(e) => filtrar('canal', e.target.value)}>
          <option value="">Todos os canais</option>
          {Object.entries(NOME_CANAL).map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </Select>
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
                      onClick={() => navegar(`/leads/${lead.id}`)}
                      onKeyDown={(evento) => evento.key === 'Enter' && navegar(`/leads/${lead.id}`)}
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

      <DetalheLeadModal id={id ?? null} onClose={() => navegar('/leads')} />
    </div>
  );
}
