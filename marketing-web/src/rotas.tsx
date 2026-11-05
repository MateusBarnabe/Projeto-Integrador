import { Navigate, createBrowserRouter } from 'react-router';
import { LayoutModulo } from '@/layout/LayoutModulo';
import { rotasPainel } from '@/features/painel/rotas';
import { rotasLeads } from '@/features/leads/rotas';
import { rotasConfiguracoes } from '@/features/configuracoes/rotas';

/**
 * Cada área exporta as próprias rotas, relativas ao caminho base do módulo. Assim elas podem
 * ser montadas em outro app (um front único, por exemplo) sem reescrever as telas.
 */
export const roteador = createBrowserRouter(
  [
    {
      path: '/',
      element: <LayoutModulo />,
      children: [
        { index: true, element: <Navigate to="/painel" replace /> },
        ...rotasPainel,
        ...rotasLeads,
        ...rotasConfiguracoes,
      ],
    },
  ],
  { basename: import.meta.env.BASE_URL },
);
