import type { Plataforma } from './tipos';

/**
 * Implementação para o módulo aberto direto no navegador, fora da casca (desenvolvimento).
 * O token vem de VITE_TOKEN_DEV: um token real do identity para um usuário de teste
 * (ex.: marketing@empresa-a.dev). Sem ele, só as rotas sem token funcionam.
 */
export function criarPlataformaSozinha(): Plataforma {
  let token: string | null = import.meta.env.VITE_TOKEN_DEV || null;
  const ouvintes = new Set<(token: string | null) => void>();

  return {
    modo: 'sozinha',
    iniciar() {},
    obterToken: () => token,
    aguardarToken: () => Promise.resolve(token),
    aoTrocarToken(ouvinte) {
      ouvintes.add(ouvinte);
      return () => {
        ouvintes.delete(ouvinte);
      };
    },
    informarNavegacao() {},
    informarTokenExpirado() {
      token = null;
      ouvintes.forEach((ouvinte) => ouvinte(null));
    },
  };
}
