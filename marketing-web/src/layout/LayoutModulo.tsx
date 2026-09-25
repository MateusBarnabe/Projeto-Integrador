import { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { FileText, LayoutDashboard, Megaphone, Settings, Users } from 'lucide-react';
import { PageHeader } from '@/components/ui';
import { plataforma } from '@/plataforma';

const ABAS = [
  { rota: '/painel', nome: 'Painel', icone: LayoutDashboard },
  { rota: '/leads', nome: 'Leads', icone: Users },
  { rota: '/formularios', nome: 'Formulários', icone: FileText },
  { rota: '/configuracoes', nome: 'Configurações', icone: Settings },
];

/**
 * Na sidebar da casca o módulo é um item só ("Marketing"). O conteúdo tem, nos dois modos:
 * faixa fixa com "Marketing" → abas das áreas do módulo → tela da aba.
 * As telas não desenham header próprio; ações da tela ficam no conteúdo.
 * Embutido, o módulo não desenha sidebar nem header global: são da casca.
 * Sozinho (desenvolvimento), desenha uma sidebar parecida com a da casca, só com o Marketing.
 */
export function LayoutModulo() {
  const { pathname } = useLocation();

  useEffect(() => {
    plataforma.informarNavegacao(pathname);
  }, [pathname]);

  const conteudo = (
    <main className="flex min-w-0 flex-1 flex-col">
      <PageHeader title="Marketing" />
      <AbasModulo />
      <Outlet />
    </main>
  );

  if (plataforma.modo === 'embutida') {
    return <div className="flex min-h-screen">{conteudo}</div>;
  }

  return (
    <div className="flex min-h-screen">
      <aside className="flex w-56 shrink-0 flex-col bg-brand-950 text-gray-300">
        <div className="px-4 py-5">
          <p className="font-bold text-white">Centinela</p>
          <p className="text-xs text-brand-300">modo sozinho</p>
        </div>
        <nav className="flex flex-col gap-0.5 px-2">
          <Link
            to="/"
            aria-current="page"
            className="flex items-center gap-2 rounded-lg bg-brand-700 px-3 py-2 text-sm text-white"
          >
            <Megaphone className="h-4 w-4 shrink-0" />
            Marketing
          </Link>
        </nav>
      </aside>
      {conteudo}
    </div>
  );
}

function AbasModulo() {
  return (
    // A borda fica fora do <nav>: com overflow-x, qualquer sobra de altura vira rolagem vertical
    <div className="shrink-0 border-b border-gray-200 bg-white">
      <nav aria-label="Áreas do Marketing" className="flex gap-1 overflow-x-auto overflow-y-hidden px-5">
        {ABAS.map(({ rota, nome, icone: Icone }) => (
          <NavLink
            key={rota}
            to={rota}
            className={({ isActive }) =>
              `flex shrink-0 items-center gap-2 border-b-2 px-3 py-3 text-sm font-medium transition-colors ${
                isActive
                  ? 'border-brand-700 text-brand-800'
                  : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'
              }`
            }
          >
            <Icone className="h-4 w-4 shrink-0" />
            {nome}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
