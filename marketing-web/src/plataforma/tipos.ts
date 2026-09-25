/**
 * Tudo o que o módulo precisa da casca da plataforma, atrás de uma interface só.
 *
 * A integração com a casca é decidida por outro grupo (hoje: iframe). As telas usam só esta
 * interface e nunca falam com window.parent, postMessage ou storage de token. Se o modelo mudar
 * (micro-frontend, front único), basta uma nova implementação (norteador, seção 9).
 */
export type ModoPlataforma = 'embutida' | 'sozinha';

export interface Plataforma {
  readonly modo: ModoPlataforma;

  /** Prepara a comunicação com a casca. Chamado uma vez, antes de renderizar. */
  iniciar(): void;

  /** Token atual, só em memória. Nulo enquanto a casca não entregou nenhum. */
  obterToken(): string | null;

  /** Resolve com o primeiro token disponível (ou nulo, se não houver como obter um). */
  aguardarToken(): Promise<string | null>;

  /** Avisa quando o token é trocado (renovação a cada 15 min). Devolve a função para cancelar. */
  aoTrocarToken(ouvinte: (token: string | null) => void): () => void;

  /** Informa à casca a rota atual do módulo, relativa (ex.: /leads/9f1c). */
  informarNavegacao(rota: string): void;

  /** Informa à casca que a API respondeu 401. */
  informarTokenExpirado(): void;
}
