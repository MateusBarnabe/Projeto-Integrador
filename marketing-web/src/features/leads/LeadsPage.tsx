import { useEffect, useState } from 'react';
import {
  RefreshCw,
  Radio,
  Users,
  Eye,
  X,
  Laptop,
  Activity,
  Globe,
  FileText,
  Clock,
  Phone,
  Mail,
  User,
  Compass,
} from 'lucide-react';
import { requisitar } from '@/api/cliente';
import { Badge, Button, PageHeader } from '@/components/ui';

interface DadosTecnicos {
  device_type?: string | null;
  os?: string | null;
  browser?: string | null;
  location?: string | null;
  timezone?: string | null;
  language?: string | null;
}

interface DadosComportamentais {
  dwell_time_seconds?: number | null;
  scroll_depth_percent?: number | null;
  clicked_elements?: string[] | null;
  pages_visited?: string[] | null;
}

interface EntradaLead {
  id?: string;
  nome: string;
  email: string;
  whatsapp?: string | null;
  telefone?: string | null;
  campanha: string | null;
  origem: string | null;
  tipo: string;
  utmMedium?: string | null;
  utmContent?: string | null;
  referrer?: string | null;
  dadosTecnicos?: DadosTecnicos | null;
  dadosComportamentais?: DadosComportamentais | null;
  processado: boolean;
}

interface PaginaEntradas {
  itens: EntradaLead[];
  pagina: number;
  tamanho: number;
  total: number;
}

export function LeadsPage() {
  const [pagina, setPagina] = useState<PaginaEntradas | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [detalhe, setDetalhe] = useState<EntradaLead | null>(null);

  async function carregar() {
    setCarregando(true);
    setErro(null);
    try {
      setPagina(await requisitar<PaginaEntradas>('/entradas?tamanho=50&ordenar=criadoEm,desc'));
    } catch (excecao) {
      setErro(excecao instanceof Error ? excecao.message : 'Não foi possível carregar as entradas.');
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    void carregar();
  }, []);

  return (
    <div className="min-h-full bg-gray-50">
      <PageHeader
        title="Leads recebidos"
        subtitle="Ingestão local de campanhas e metadados de tracking"
        actions={(
          <Button variant="outline" size="sm" onClick={() => void carregar()} disabled={carregando}>
            <RefreshCw size={14} className={carregando ? 'animate-spin' : ''} />
            Atualizar
          </Button>
        )}
      />

      <main className="space-y-5 p-5">
        <section className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Entradas no buffer</span>
              <Users size={18} className="text-brand-700" />
            </div>
            <strong className="mt-2 block text-3xl text-brand-950">{pagina?.total ?? 0}</strong>
          </div>
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
            <div className="flex items-center justify-between">
              <span className="text-sm text-gray-500">Fonte da demonstração</span>
              <Radio size={18} className="text-brand-700" />
            </div>
            <strong className="mt-2 block text-lg text-brand-950">RabbitMQ local</strong>
          </div>
        </section>

        {erro && <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">{erro}</div>}

        <section className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-3">Lead</th>
                  <th className="px-4 py-3">Campanha</th>
                  <th className="px-4 py-3">Origem</th>
                  <th className="px-4 py-3">Evento</th>
                  <th className="px-4 py-3">Estado</th>
                  <th className="px-4 py-3 text-right">Ações</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {pagina?.itens.map((entrada) => (
                  <tr key={`${entrada.email}-${entrada.tipo}`} className="hover:bg-gray-50">
                    <td className="px-4 py-4">
                      <div className="font-semibold text-gray-900">{entrada.nome}</div>
                      <div className="text-xs text-gray-500">{entrada.email}</div>
                    </td>
                    <td className="px-4 py-4 text-gray-700">{entrada.campanha || 'Orgânica'}</td>
                    <td className="px-4 py-4 text-gray-700">{entrada.origem || 'Não informado'}</td>
                    <td className="px-4 py-4 font-mono text-xs text-gray-600">{entrada.tipo}</td>
                    <td className="px-4 py-4">
                      <Badge variant={entrada.processado ? 'green' : 'yellow'}>
                        {entrada.processado ? 'Processado' : 'Aguardando'}
                      </Badge>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => setDetalhe(entrada)}
                        className="gap-1 text-brand-800 hover:text-brand-900"
                      >
                        <Eye size={13} />
                        Ver detalhes
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!carregando && !erro && !pagina?.itens.length && (
            <div className="px-4 py-12 text-center text-sm text-gray-500">
              Publique o payload de demonstração no RabbitMQ para visualizar o primeiro lead.
            </div>
          )}
          {carregando && <div className="px-4 py-12 text-center text-sm text-gray-500">Carregando entradas...</div>}
        </section>
      </main>

      {/* Modal de Detalhamento do Lead */}
      {detalhe && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs">
          <div className="max-h-[90vh] w-full max-w-3xl overflow-y-auto rounded-2xl border border-gray-200 bg-white p-6 shadow-2xl">
            <div className="flex items-start justify-between border-b border-gray-100 pb-4">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className="text-xl font-bold text-gray-900">{detalhe.nome}</h2>
                  <Badge variant={detalhe.processado ? 'green' : 'yellow'}>
                    {detalhe.processado ? 'Processado' : 'Buffer de Ingestão'}
                  </Badge>
                </div>
                <p className="mt-1 font-mono text-xs text-gray-500">{detalhe.tipo}</p>
              </div>
              <button
                type="button"
                onClick={() => setDetalhe(null)}
                className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
              >
                <X size={20} />
              </button>
            </div>

            <div className="mt-5 space-y-6">
              {/* 1. Dados Declarados */}
              <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-4">
                <h3 className="flex items-center gap-2 font-semibold text-gray-900 text-sm">
                  <User size={16} className="text-brand-700" />
                  1. Dados Declarados (Formulário)
                </h3>
                <dl className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 text-sm">
                  <div>
                    <dt className="text-xs text-gray-500">Nome Completo</dt>
                    <dd className="font-medium text-gray-800">{detalhe.nome}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">E-mail</dt>
                    <dd className="flex items-center gap-1.5 font-medium text-gray-800">
                      <Mail size={13} className="text-gray-400" />
                      {detalhe.email}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">WhatsApp</dt>
                    <dd className="flex items-center gap-1.5 font-medium text-gray-800">
                      <Phone size={13} className="text-gray-400" />
                      {detalhe.whatsapp || 'Não informado'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Telefone</dt>
                    <dd className="font-medium text-gray-800">{detalhe.telefone || 'Não informado'}</dd>
                  </div>
                </dl>
              </div>

              {/* 2. Origem e Marketing (UTMs) */}
              <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-4">
                <h3 className="flex items-center gap-2 font-semibold text-gray-900 text-sm">
                  <Compass size={16} className="text-brand-700" />
                  2. Dados de Origem e Marketing (UTMs & URL)
                </h3>
                <dl className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3 text-sm">
                  <div>
                    <dt className="text-xs text-gray-500">Origem (utm_source)</dt>
                    <dd className="font-medium text-gray-800">{detalhe.origem || 'Orgânico / Direto'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Mídia (utm_medium)</dt>
                    <dd className="font-medium text-gray-800">{detalhe.utmMedium || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Campanha (utm_campaign)</dt>
                    <dd className="font-medium text-gray-800">{detalhe.campanha || 'Não informado'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Conteúdo (utm_content)</dt>
                    <dd className="font-medium text-gray-800">{detalhe.utmContent || 'Não informado'}</dd>
                  </div>
                  <div className="sm:col-span-2">
                    <dt className="text-xs text-gray-500">Referrer (URL de origem)</dt>
                    <dd className="truncate font-mono text-xs text-gray-700">{detalhe.referrer || 'Não informado'}</dd>
                  </div>
                </dl>
              </div>

              {/* 3. Contexto Técnico */}
              <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-4">
                <h3 className="flex items-center gap-2 font-semibold text-gray-900 text-sm">
                  <Laptop size={16} className="text-brand-700" />
                  3. Dados de Contexto Técnico (Device & Rede)
                </h3>
                <dl className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 text-sm">
                  <div>
                    <dt className="text-xs text-gray-500">Dispositivo</dt>
                    <dd className="font-medium capitalize text-gray-800">{detalhe.dadosTecnicos?.device_type || 'N/D'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Sistema Operacional</dt>
                    <dd className="font-medium text-gray-800">{detalhe.dadosTecnicos?.os || 'N/D'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Navegador</dt>
                    <dd className="font-medium text-gray-800">{detalhe.dadosTecnicos?.browser || 'N/D'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Localização</dt>
                    <dd className="flex items-center gap-1 font-medium text-gray-800">
                      <Globe size={13} className="text-gray-400" />
                      {detalhe.dadosTecnicos?.location || 'N/D'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Fuso Horário</dt>
                    <dd className="font-medium text-gray-800">{detalhe.dadosTecnicos?.timezone || 'N/D'}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Idioma</dt>
                    <dd className="font-medium text-gray-800">{detalhe.dadosTecnicos?.language || 'N/D'}</dd>
                  </div>
                </dl>
              </div>

              {/* 4. Dados Comportamentais */}
              <div className="rounded-xl border border-gray-100 bg-gray-50/70 p-4">
                <h3 className="flex items-center gap-2 font-semibold text-gray-900 text-sm">
                  <Activity size={16} className="text-brand-700" />
                  4. Dados Comportamentais (On-site Tracking)
                </h3>
                <div className="mt-3 grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div className="flex items-center gap-3 rounded-lg border border-gray-200/60 bg-white p-3">
                    <Clock size={20} className="text-brand-700" />
                    <div>
                      <div className="text-xs text-gray-500">Tempo de Permanência</div>
                      <div className="text-base font-bold text-gray-900">
                        {detalhe.dadosComportamentais?.dwell_time_seconds ?? 0} segundos
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-3 rounded-lg border border-gray-200/60 bg-white p-3">
                    <Activity size={20} className="text-brand-700" />
                    <div>
                      <div className="text-xs text-gray-500">Profundidade de Rolagem</div>
                      <div className="text-base font-bold text-gray-900">
                        {detalhe.dadosComportamentais?.scroll_depth_percent ?? 0}% da página
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-3 space-y-2">
                  <div>
                    <span className="text-xs font-medium text-gray-500">CTAs e Botões Clicados:</span>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {detalhe.dadosComportamentais?.clicked_elements?.length ? (
                        detalhe.dadosComportamentais.clicked_elements.map((cta) => (
                          <span
                            key={cta}
                            className="inline-flex items-center rounded-md bg-blue-50 px-2 py-1 font-mono text-xs text-blue-700 border border-blue-100"
                          >
                            {cta}
                          </span>
                        ))
                      ) : (
                        <span className="text-xs text-gray-400">Nenhum botão registrado</span>
                      )}
                    </div>
                  </div>

                  <div>
                    <span className="text-xs font-medium text-gray-500">Páginas Visitadas na Sessão:</span>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {detalhe.dadosComportamentais?.pages_visited?.length ? (
                        detalhe.dadosComportamentais.pages_visited.map((pagina) => (
                          <span
                            key={pagina}
                            className="inline-flex items-center gap-1 rounded-md bg-gray-100 px-2 py-1 font-mono text-xs text-gray-700"
                          >
                            <FileText size={11} className="text-gray-400" />
                            {pagina}
                          </span>
                        ))
                      ) : (
                        <span className="text-xs text-gray-400">Nenhuma página registrada</span>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end border-t border-gray-100 pt-4">
              <Button variant="secondary" size="md" onClick={() => setDetalhe(null)}>
                Fechar
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
