import { plataforma } from '@/plataforma';

/**
 * Cliente de SSE (Server-Sent Events) por `fetch` + `ReadableStream`.
 *
 * `EventSource` não manda cabeçalho, e a autenticação do módulo é só pelo `Authorization` (nunca por
 * query), então o fluxo é lido à mão (norteador §9.1). O cliente:
 * - interpreta o formato SSE (`id`, `event`, `data`, `retry` e comentários, como o `: ping`);
 * - reconecta com espera crescente, mandando `Last-Event-ID` para o servidor reenviar só o que perdeu;
 * - reabre quando o token muda (renovação a cada 15 min) e para quando o token some;
 * - avisa a plataforma no `401`.
 */

const BASE = import.meta.env.VITE_API_URL || '/api/marketing';

/** Uma mensagem do fluxo. Comentários (`: ping`) não chegam aqui. */
export interface EventoSse {
  /** `id:` da mensagem; nulo quando ela não trouxe. */
  id: string | null;
  evento: string;
  dados: string;
}

/**
 * - `conectando`: primeira tentativa, ainda sem resposta.
 * - `conectado`: o servidor respondeu com `text/event-stream`.
 * - `reconectando`: caiu, esperando para tentar de novo.
 * - `indisponivel`: várias tentativas seguidas falharam (gateway cortando, `429`, rede). Continua
 *   tentando em segundo plano; a tela deve cair para releitura periódica.
 */
export type EstadoDoFluxo = 'conectando' | 'conectado' | 'reconectando' | 'indisponivel';

export interface OpcoesDoFluxo {
  aoMudarEstado?: (estado: EstadoDoFluxo) => void;
  /** Falhas seguidas até declarar `indisponivel`. Padrão: 3. */
  tentativasAteIndisponivel?: number;
}

const ESPERA_INICIAL_MS = 1000;
const ESPERA_MAXIMA_MS = 30_000;

/** Junta os pedaços de texto do fluxo e devolve as mensagens completas, na ordem. */
export class InterpretadorSse {
  private resto = '';
  private id: string | null = null;
  private evento = '';
  private dados: string[] = [];
  /** Último `id:` visto, mesmo em mensagens sem `data:`. */
  ultimoId: string | null = null;

  /** Recebe um pedaço do fluxo (já decodificado) e chama `aoMensagem` para cada mensagem completa. */
  alimentar(pedaco: string, aoMensagem: (mensagem: EventoSse) => void) {
    this.resto += pedaco;
    // Um \r no fim pode ser metade de um \r\n: espera o próximo pedaço para decidir
    const retido = this.resto.endsWith('\r') ? '\r' : '';
    const linhas = this.resto.slice(0, this.resto.length - retido.length).split(/\r\n|\n|\r/);
    // A última "linha" pode estar incompleta
    this.resto = (linhas.pop() ?? '') + retido;
    for (const linha of linhas) this.linha(linha, aoMensagem);
  }

  private linha(linha: string, aoMensagem: (mensagem: EventoSse) => void) {
    if (linha === '') {
      if (this.dados.length > 0) {
        aoMensagem({ id: this.id, evento: this.evento || 'message', dados: this.dados.join('\n') });
      }
      this.id = null;
      this.evento = '';
      this.dados = [];
      return;
    }
    if (linha.startsWith(':')) return; // comentário, como o ": ping"

    const separador = linha.indexOf(':');
    const campo = separador === -1 ? linha : linha.slice(0, separador);
    let valor = separador === -1 ? '' : linha.slice(separador + 1);
    if (valor.startsWith(' ')) valor = valor.slice(1);

    if (campo === 'id') {
      if (!valor.includes('\0')) {
        this.id = valor;
        this.ultimoId = valor;
      }
    } else if (campo === 'event') this.evento = valor;
    else if (campo === 'data') this.dados.push(valor);
    // `retry` é ignorado: a espera é nossa (crescente)
  }
}

/**
 * Abre o fluxo em `caminho` (relativo à API do módulo, ex.: `/painel/eventos`) e chama `aoReceber` a
 * cada mensagem. Devolve a função que fecha tudo (leitura, espera e ouvinte de token).
 */
export function abrirFluxo(
  caminho: string,
  aoReceber: (mensagem: EventoSse) => void,
  { aoMudarEstado, tentativasAteIndisponivel = 3 }: OpcoesDoFluxo = {},
): () => void {
  const interpretador = new InterpretadorSse();
  let fechado = false;
  let pausado = false; // sem token: espera o próximo
  let falhasSeguidas = 0;
  let espera = ESPERA_INICIAL_MS;
  let temporizador: number | undefined;
  let controlador: AbortController | null = null;
  let estado: EstadoDoFluxo = 'conectando';
  // Cada conexão tem um número; resultado de uma conexão antiga (abortada) é ignorado
  let geracao = 0;

  function mudarEstado(novo: EstadoDoFluxo) {
    if (novo === estado) return;
    estado = novo;
    aoMudarEstado?.(novo);
  }

  function agendarNovaTentativa() {
    if (fechado || pausado) return;
    falhasSeguidas += 1;
    mudarEstado(falhasSeguidas >= tentativasAteIndisponivel ? 'indisponivel' : 'reconectando');
    window.clearTimeout(temporizador);
    temporizador = window.setTimeout(() => void conectar(), espera);
    espera = Math.min(espera * 2, ESPERA_MAXIMA_MS);
  }

  async function conectar() {
    if (fechado || pausado) return;
    const minha = ++geracao;
    controlador?.abort();
    const meuControlador = new AbortController();
    controlador = meuControlador;

    try {
      const token = plataforma.obterToken() ?? (await plataforma.aguardarToken());
      if (minha !== geracao) return;
      if (!token) {
        // Sem token não há o que abrir: espera o `aoTrocarToken` entregar um
        pausado = true;
        return;
      }

      const cabecalhos = new Headers({ Accept: 'text/event-stream', Authorization: `Bearer ${token}` });
      if (interpretador.ultimoId) cabecalhos.set('Last-Event-ID', interpretador.ultimoId);

      const resposta = await fetch(`${BASE}${caminho}`, {
        headers: cabecalhos,
        signal: meuControlador.signal,
        cache: 'no-store',
      });
      if (minha !== geracao) return;

      if (resposta.status === 401) {
        // A plataforma renova o token e avisa por `aoTrocarToken`, que reabre o fluxo
        plataforma.informarTokenExpirado();
        pausado = true;
        return;
      }
      const tipo = resposta.headers.get('Content-Type') ?? '';
      if (!resposta.ok || !resposta.body || !tipo.includes('text/event-stream')) {
        // 429, 504 do gateway, página de erro... tudo igual: tenta de novo mais tarde
        void resposta.body?.cancel().catch(() => undefined);
        agendarNovaTentativa();
        return;
      }

      falhasSeguidas = 0;
      espera = ESPERA_INICIAL_MS;
      mudarEstado('conectado');

      const leitor = resposta.body.getReader();
      const decodificador = new TextDecoder();
      for (;;) {
        const { done, value } = await leitor.read();
        if (minha !== geracao) return;
        if (done) break;
        interpretador.alimentar(decodificador.decode(value, { stream: true }), aoReceber);
      }
      // O servidor fechou (reinício, limite de tempo do gateway): reconecta com o Last-Event-ID
      if (minha === geracao) {
        mudarEstado('reconectando');
        window.clearTimeout(temporizador);
        temporizador = window.setTimeout(() => void conectar(), ESPERA_INICIAL_MS);
      }
    } catch {
      if (minha !== geracao || fechado) return; // abortada por nós
      agendarNovaTentativa();
    }
  }

  // Token novo (renovação) → reabre já; token nulo (sessão acabou) → para até chegar outro
  const cancelarOuvinte = plataforma.aoTrocarToken((token) => {
    if (fechado) return;
    window.clearTimeout(temporizador);
    if (!token) {
      pausado = true;
      geracao += 1;
      controlador?.abort();
      return;
    }
    pausado = false;
    falhasSeguidas = 0;
    espera = ESPERA_INICIAL_MS;
    void conectar();
  });

  void conectar();

  return () => {
    fechado = true;
    geracao += 1;
    window.clearTimeout(temporizador);
    controlador?.abort();
    cancelarOuvinte();
  };
}
