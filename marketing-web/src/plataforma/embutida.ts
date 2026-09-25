import type { Plataforma } from './tipos';

/*
 * Implementação para o módulo aberto pela casca num <iframe> (Contrato §12).
 *
 * Os nomes das mensagens vêm do contrato; o formato exato do objeto ({ tipo, ... }) deve ser
 * conferido com exemplo-modulo/front/src/plataforma/sessao.ts do infra-integrador-2026.
 */

type MensagemDaCasca =
  | { tipo: 'plataforma:token'; token: string }
  | { tipo: 'plataforma:tema'; tema: string };

type MensagemParaCasca =
  | { tipo: 'modulo:pronto' }
  | { tipo: 'modulo:altura'; altura: number }
  | { tipo: 'modulo:navegar'; rota: string }
  | { tipo: 'modulo:token-expirado' };

export function criarPlataformaEmbutida(): Plataforma {
  let token: string | null = null;
  const ouvintes = new Set<(token: string | null) => void>();

  function enviar(mensagem: MensagemParaCasca) {
    window.parent.postMessage(mensagem, window.location.origin);
  }

  function aoTrocarToken(ouvinte: (token: string | null) => void) {
    ouvintes.add(ouvinte);
    return () => {
      ouvintes.delete(ouvinte);
    };
  }

  function receber(evento: MessageEvent<MensagemDaCasca>) {
    // Só aceita mensagens da própria origem vindas da janela que embute o módulo
    if (evento.origin !== window.location.origin || evento.source !== window.parent) return;

    const mensagem = evento.data;
    if (mensagem?.tipo === 'plataforma:token') {
      token = mensagem.token;
      ouvintes.forEach((ouvinte) => ouvinte(token));
    } else if (mensagem?.tipo === 'plataforma:tema') {
      document.documentElement.dataset.tema = mensagem.tema;
    }
  }

  return {
    modo: 'embutida',

    iniciar() {
      // O ouvinte precisa existir antes do modulo:pronto (correção da v0.7, §12.2)
      window.addEventListener('message', receber);
      enviar({ tipo: 'modulo:pronto' });

      let ultimaAltura = 0;
      new ResizeObserver(() => {
        const altura = document.documentElement.scrollHeight;
        if (altura !== ultimaAltura) {
          ultimaAltura = altura;
          enviar({ tipo: 'modulo:altura', altura });
        }
      }).observe(document.body);
    },

    obterToken: () => token,

    aguardarToken() {
      if (token) return Promise.resolve(token);
      return new Promise((resolver) => {
        const cancelar = aoTrocarToken((novo) => {
          cancelar();
          resolver(novo);
        });
      });
    },

    aoTrocarToken,

    informarNavegacao: (rota) => enviar({ tipo: 'modulo:navegar', rota }),
    informarTokenExpirado: () => enviar({ tipo: 'modulo:token-expirado' }),
  };
}
