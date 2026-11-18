/**
 * Tudo o que o módulo precisa da casca da plataforma, atrás de uma interface só.
 *
 * A integração com a casca é decidida por outro grupo (hoje: iframe). As telas usam só esta
 * interface e nunca falam com window.parent, postMessage ou storage de token. Se o modelo mudar
 * (micro-frontend, front único), basta uma nova implementação (norteador, seção 9).
 */
export type ModoPlataforma = 'embutida' | 'sozinha';

/** Tema da casca (Contrato §12.1). Aplicado em data-tema no <html>. */
export type Tema = 'claro' | 'escuro';

/** Nível do aviso mostrado pela casca (Contrato §12.2, modulo:notificar). */
export type NivelDeAviso = 'sucesso' | 'erro' | 'info';

export interface Plataforma {
  readonly modo: ModoPlataforma;

  /** Prepara a comunicação com a casca. Chamado uma vez, antes de renderizar. */
  iniciar(): void;

  /** Token atual, só em memória. Nulo enquanto a casca não entregou nenhum. */
  obterToken(): string | null;

  /** Resolve com o primeiro token disponível (ou nulo, se não houver como obter um). */
  aguardarToken(): Promise<string | null>;

  /** Avisa quando o token é trocado (entrega inicial e renovação a cada 15 min). Devolve a função para cancelar. */
  aoTrocarToken(ouvinte: (token: string | null) => void): () => void;

  /**
   * O usuário tem a permissão? Lê o claim `perms` do token atual, sem validar a assinatura (quem
   * valida é o back, que continua respondendo 403). Serve só para esconder o que o usuário não
   * pode usar. Sem token, devolve false. Em tela, prefira o hook `usarPermissao`, que reage à troca
   * do token.
   */
  temPermissao(codigo: string): boolean;

  /** Pede à casca um aviso para o usuário (toast). No modo sozinho, vai para o console. */
  notificar(nivel: NivelDeAviso, texto: string): void;

  /** Informa à casca a rota atual do módulo, relativa (ex.: /leads/9f1c). */
  informarNavegacao(rota: string): void;

  /** Informa à casca que a API respondeu 401. */
  informarTokenExpirado(): void;
}
