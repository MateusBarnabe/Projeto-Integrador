import { useEffect, useState } from 'react';
import { RefreshCw, Radio, Users } from 'lucide-react';
import { requisitar } from '@/api/cliente';
import { Badge, Button, PageHeader } from '@/components/ui';

interface EntradaLead {
  nome: string;
  email: string;
  campanha: string | null;
  origem: string | null;
  tipo: string;
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
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs uppercase tracking-wide text-gray-500">
                <tr>
                  <th className="px-4 py-3">Lead</th>
                  <th className="px-4 py-3">Campanha</th>
                  <th className="px-4 py-3">Origem</th>
                  <th className="px-4 py-3">Evento</th>
                  <th className="px-4 py-3">Estado</th>
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
    </div>
  );
}
