import { useEffect } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { LayoutDashboard, Lock, Megaphone, Settings, Users, type LucideIcon } from 'lucide-react';
import { PageHeader } from '@/components/ui';
import { plataforma, usarPermissao, usarTemSessao } from '@/plataforma';

interface Aba {
  rota: string;
  nome: string;
  icone: LucideIcon;
  /** Permissão que dá acesso à área (a mesma do itensSubmenu em Docs/modulos/marketing.json). */
  permissao: string;
}

const ABAS: Aba[] = [
  { rota: '/painel', nome: 'Painel', icone: LayoutDashboard, permissao: 'marketing.dashboard.ver' },
  { rota: '/leads', nome: 'Leads', icone: Users, permissao: 'marketing.lead.ver' },
  { rota: '/configuracoes', nome: 'Configurações', icone: Settings, permissao: 'marketing.configuracao.ver' },
];

/**
 * Abas que o usuário pode abrir. Esconder é só usabilidade: a API continua respondendo 403.
 * Sem token ainda (a casca não entregou a sessão), embutido não mostra nenhuma aba; sozinho, sem
 * VITE_TOKEN_DEV, mostra todas, porque não há como saber e a API responde 401 de qualquer jeito.
 */
function usarAbasPermitidas(): { abas: Aba[]; aguardandoSessao: boolean } {
  const temSessao = usarTemSessao();
  const podeVer = [
    usarPermissao(ABAS[0].permissao),
    usarPermissao(ABAS[1].permissao),
    usarPermissao(ABAS[2].permissao),
  ];
  if (!temSessao) {
    return plataforma.modo === 'sozinha' ? { abas: ABAS, aguardandoSessao: false } : { abas: [], aguardandoSessao: true };
  }
  return { abas: ABAS.filter((_, i) => podeVer[i]), aguardandoSessao: false };
}

/**
 * Sozinho (desenvolvimento): sidebar parecida com a da casca, só com o Marketing, faixa "Marketing" e abas
 * das áreas do módulo, uma por permissão.
 * Embutido: a casca já tem a sidebar, o header global e, no menu lateral, os itens do submenu (Painel, Leads,
 * Configurações), que ela abre carregando o iframe em /modulos/marketing/<rota>. O módulo desenha só a faixa
 * "Marketing" e a tela, sem abas, e sem altura mínima: a casca ajusta o iframe ao conteúdo.
 * As telas não desenham header próprio; ações da tela ficam no conteúdo.
 */
export function LayoutModulo() {
  const { pathname } = useLocation();
  const navegar = useNavigate();
  const { abas, aguardandoSessao } = usarAbasPermitidas();

  useEffect(() => {
    plataforma.informarNavegacao(pathname);
  }, [pathname]);

  // Rota de uma área sem permissão (o "/" manda para o Painel, por exemplo): vai para a primeira área liberada
  useEffect(() => {
    if (aguardandoSessao || abas.length === 0) return;
    if (!abas.some((aba) => pathname === aba.rota || pathname.startsWith(`${aba.rota}/`))) {
      navegar(abas[0].rota, { replace: true });
    }
  }, [abas, aguardandoSessao, pathname, navegar]);

  const semAcesso = !aguardandoSessao && abas.length === 0;

  const conteudo = (
    <main className="flex min-w-0 flex-1 flex-col">
      <PageHeader title="Marketing" />
      {/* Embutido, a casca já lista Painel, Leads e Configurações no menu lateral (itensSubmenu) e abre cada
          um carregando o iframe em /modulos/marketing/<rota>: abas aqui repetiriam o menu */}
      {plataforma.modo === 'sozinha' && <AbasModulo abas={abas} />}
      {semAcesso ? <SemAcesso /> : aguardandoSessao ? null : <Outlet />}
    </main>
  );

  if (plataforma.modo === 'embutida') {
    return <div className="flex">{conteudo}</div>;
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

function SemAcesso() {
  return (
    <div className="flex flex-col items-center gap-3 p-10 text-center">
      <Lock className="h-8 w-8 text-texto-3" />
      <p className="max-w-md text-sm text-texto-2">
        Seu perfil não tem acesso a nenhuma área do Marketing. Peça a um administrador a permissão de painel, leads ou
        configurações.
      </p>
    </div>
  );
}

function AbasModulo({ abas }: { abas: Aba[] }) {
  return (
    // A borda fica fora do <nav>: com overflow-x, qualquer sobra de altura vira rolagem vertical
    <div className="shrink-0 border-b border-borda bg-superficie">
      <nav aria-label="Áreas do Marketing" className="flex gap-1 overflow-x-auto overflow-y-hidden px-5">
        {abas.map(({ rota, nome, icone: Icone }) => (
          <NavLink
            key={rota}
            to={rota}
            className={({ isActive }) =>
              `flex shrink-0 items-center gap-2 border-b-2 px-3 py-3 text-sm font-medium transition-colors ${
                isActive
                  ? 'border-brand-700 text-brand-800 dark:text-brand-400'
                  : 'border-transparent text-texto-3 hover:border-borda-forte hover:text-texto'
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
