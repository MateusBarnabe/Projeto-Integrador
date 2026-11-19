import { tokenTemPermissao } from './permissoes';
import type { Plataforma } from './tipos';

/**
 * Implementação para o módulo aberto direto no navegador, fora da casca (desenvolvimento).
 * O token vem de VITE_TOKEN_DEV: um token real do identity para um usuário de teste
 * (ex.: marketing@empresa-a.dev). Sem ele, só as rotas sem token funcionam.
 * O tema escuro se testa com ?tema=escuro na URL.
 */
export function criarPlataformaSozinha(): Plataforma {
  let token: string | null = import.meta.env.VITE_TOKEN_DEV || null;
  const ouvintes = new Set<(token: string | null) => void>();

  return {
    modo: 'sozinha',
    iniciar() {
      if (new URLSearchParams(window.location.search).get('tema') === 'escuro') {
        document.documentElement.dataset.tema = 'escuro';
      }
    },
    obterToken: () => token,
    aguardarToken: () => Promise.resolve(token),
    aoTrocarToken(ouvinte) {
      ouvintes.add(ouvinte);
      return () => {
        ouvintes.delete(ouvinte);
      };
    },
    temPermissao: (codigo) => tokenTemPermissao(token, codigo),
    notificar: (nivel, texto) => console.info(`[aviso à casca · ${nivel}] ${texto}`),
    informarNavegacao() {},
    informarTokenExpirado() {
      token = null;
      ouvintes.forEach((ouvinte) => ouvinte(null));
    },
  };
}
