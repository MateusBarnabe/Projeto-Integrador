import { acompanharAlturaEArea } from './altura';
import { tokenTemPermissao } from './permissoes';
import type { NivelDeAviso, Plataforma, Tema } from './tipos';

/*
 * Implementação para o módulo aberto pela casca num <iframe> (Contrato §12), no mesmo formato do
 * exemplo-modulo/front/src/plataforma/sessao.ts do infra-integrador-2026.
 *
 * Casca para o módulo: plataforma:sessao (primeira entrega: token, tenantId, usuário e tema),
 * plataforma:token (renovação) e plataforma:tema. Módulo para a casca: modulo:pronto, modulo:altura,
 * modulo:navegar, modulo:token-expirado e modulo:notificar.
 */

interface UsuarioDaSessao {
  id: string;
  nome: string;
  email?: string;
  tenantId?: string;
}

type MensagemDaCasca =
  | { tipo: 'plataforma:sessao'; token: string; tenantId: string; usuario: UsuarioDaSessao; tema: Tema }
  | { tipo: 'plataforma:token'; token: string }
  | { tipo: 'plataforma:tema'; tema: Tema };

type MensagemParaCasca =
  | { tipo: 'modulo:pronto' }
  | { tipo: 'modulo:altura'; altura: number }
  | { tipo: 'modulo:navegar'; rota: string }
  | { tipo: 'modulo:token-expirado' }
  | { tipo: 'modulo:notificar'; nivel: NivelDeAviso; texto: string };

function aplicarTema(tema: unknown) {
  if (tema === 'claro' || tema === 'escuro') document.documentElement.dataset.tema = tema;
}

export function criarPlataformaEmbutida(): Plataforma {
  // A sessão fica fora dos componentes: a casca responde ao modulo:pronto na hora, antes do primeiro render
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

  function trocarToken(novo: unknown) {
    if (typeof novo !== 'string' || !novo || novo === token) return;
    token = novo;
    ouvintes.forEach((ouvinte) => ouvinte(token));
  }

  function receber(evento: MessageEvent<MensagemDaCasca>) {
    // Só aceita mensagens da própria origem vindas da janela que embute o módulo
    if (evento.origin !== window.location.origin || evento.source !== window.parent) return;

    const mensagem = evento.data;
    switch (mensagem?.tipo) {
      case 'plataforma:sessao':
        // O tema vai antes do token: quem reage ao token já renderiza com as cores certas
        aplicarTema(mensagem.tema);
        trocarToken(mensagem.token);
        break;
      case 'plataforma:token':
        trocarToken(mensagem.token);
        break;
      case 'plataforma:tema':
        aplicarTema(mensagem.tema);
        break;
    }
  }

  return {
    modo: 'embutida',

    iniciar() {
      // O ouvinte precisa existir antes do modulo:pronto (correção da v0.7, §12.2)
      window.addEventListener('message', receber);
      enviar({ tipo: 'modulo:pronto' });
      acompanharAlturaEArea((altura) => enviar({ tipo: 'modulo:altura', altura }));
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

    temPermissao: (codigo) => tokenTemPermissao(token, codigo),

    notificar: (nivel, texto) => enviar({ tipo: 'modulo:notificar', nivel, texto }),
    informarNavegacao: (rota) => enviar({ tipo: 'modulo:navegar', rota }),
    informarTokenExpirado: () => enviar({ tipo: 'modulo:token-expirado' }),
  };
}
