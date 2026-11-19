import { useCallback, useSyncExternalStore } from 'react';
import { plataforma } from './instancia';

/** Reage à entrega e à renovação do token: a tela se atualiza quando as permissões chegam. */
export function usarPermissao(codigo: string): boolean {
  return useSyncExternalStore(
    plataforma.aoTrocarToken,
    useCallback(() => plataforma.temPermissao(codigo), [codigo]),
  );
}

/** O módulo já tem um token (a casca entregou a sessão ou VITE_TOKEN_DEV está definido)? */
export function usarTemSessao(): boolean {
  return useSyncExternalStore(plataforma.aoTrocarToken, () => plataforma.obterToken() !== null);
}
