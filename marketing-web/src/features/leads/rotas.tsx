import type { RouteObject } from 'react-router';
import { LeadsPage } from './LeadsPage';

// /leads/{id} abre o detalhe por cima da lista (rota da busca global e da notificação da casca). Uma rota
// só, com o id opcional, para a lista não ser remontada (e perder os filtros) ao abrir e fechar o detalhe.
export const rotasLeads: RouteObject[] = [{ path: 'leads/:id?', element: <LeadsPage /> }];
