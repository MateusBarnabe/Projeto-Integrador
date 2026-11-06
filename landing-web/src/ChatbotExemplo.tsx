import { useState, type FormEvent } from 'react';
import { Bot, MessageCircle, Send, X } from 'lucide-react';
import { CHAVE_FORMULARIO, SLUG_PAGINA, chamar, type CampoPublico } from './api';
import type { Origem } from './origem';

/** `PassoChatbot` do landing.yaml. */
interface Passo {
  sessaoId: string;
  pedirConsentimento: boolean;
  textoConsentimento: string | null;
  proximaPergunta: CampoPublico | null;
  concluida: boolean;
}

interface Mensagem {
  de: 'bot' | 'visitante';
  texto: string;
}

const BASE = `/public/landing/formularios/${CHAVE_FORMULARIO}/sessoes`;

/**
 * Chatbot de exemplo, pelas rotas de sessão do contrato. Quando a pessoa aceita o termo e deixa
 * e-mail ou telefone, o servidor publica `landing.chatbot.lead_capturado`; ao concluir, o envio
 * vira `landing.formulario.recebido` com canal `chatbot`.
 */
export function ChatbotExemplo({ origem }: { origem: Origem }) {
  const [aberto, setAberto] = useState(false);
  const [passo, setPasso] = useState<Passo | null>(null);
  const [mensagens, setMensagens] = useState<Mensagem[]>([]);
  const [texto, setTexto] = useState('');
  const [marcados, setMarcados] = useState<string[]>([]);
  const [aguardando, setAguardando] = useState(false);
  const [encerrada, setEncerrada] = useState(false);

  const dizer = (...novas: Mensagem[]) => setMensagens((atual) => [...atual, ...novas]);

  function mostrar(novo: Passo) {
    setPasso(novo);
    if (novo.pedirConsentimento && novo.textoConsentimento) {
      dizer({ de: 'bot', texto: `Antes de pedir seu contato: ${novo.textoConsentimento}` });
    } else if (novo.proximaPergunta) {
      dizer({ de: 'bot', texto: novo.proximaPergunta.textoPergunta ?? novo.proximaPergunta.rotulo });
    } else if (novo.concluida) {
      dizer({ de: 'bot', texto: 'Pronto! Posso enviar suas respostas para a equipe?' });
    }
  }

  async function chamarPasso(caminho: string, corpo: unknown, eco?: string) {
    setAguardando(true);
    if (eco !== undefined) dizer({ de: 'visitante', texto: eco });
    const resposta = await chamar<Passo>('POST', caminho, corpo);
    setAguardando(false);
    if (resposta.success && resposta.data) {
      setTexto('');
      setMarcados([]);
      mostrar(resposta.data);
    } else {
      dizer({ de: 'bot', texto: resposta.errors[0]?.detalhe ?? resposta.message ?? 'Não entendi, pode repetir?' });
    }
  }

  function abrir() {
    setAberto(true);
    if (!passo && !aguardando) {
      dizer({ de: 'bot', texto: 'Oi! Sou o assistente da Empresa A.' });
      void chamarPasso(BASE, { paginaSlug: SLUG_PAGINA, origem });
    }
  }

  function reiniciar() {
    setPasso(null);
    setMensagens([]);
    setEncerrada(false);
    dizer({ de: 'bot', texto: 'Oi! Sou o assistente da Empresa A.' });
    void chamarPasso(BASE, { paginaSlug: SLUG_PAGINA, origem });
  }

  async function concluir() {
    if (!passo) return;
    setAguardando(true);
    const resposta = await chamar<{ mensagemSucesso: string }>('POST', `${BASE}/${passo.sessaoId}/concluir`);
    setAguardando(false);
    if (resposta.success && resposta.data) {
      dizer({ de: 'bot', texto: resposta.data.mensagemSucesso });
      setEncerrada(true);
    } else {
      dizer({ de: 'bot', texto: resposta.errors[0]?.detalhe ?? resposta.message ?? 'Não consegui enviar.' });
    }
  }

  function responder(evento: FormEvent, valor: string | string[], eco: string) {
    evento.preventDefault();
    const pergunta = passo?.proximaPergunta;
    if (!passo || !pergunta) return;
    void chamarPasso(`${BASE}/${passo.sessaoId}/respostas`, { campoId: pergunta.id, valor }, eco);
  }

  if (!aberto) {
    return (
      <button
        onClick={abrir}
        className="fixed right-6 bottom-6 inline-flex items-center gap-2 rounded-full bg-brand-700 px-4 py-3 text-sm font-semibold text-white shadow-lg hover:bg-brand-800"
      >
        <MessageCircle className="h-5 w-5" />
        Converse com a gente
      </button>
    );
  }

  const pergunta = passo?.proximaPergunta ?? null;

  return (
    <div className="fixed right-6 bottom-6 flex h-[30rem] w-80 flex-col overflow-hidden rounded-xl border border-gray-200 bg-white shadow-xl">
      <div className="flex items-center justify-between bg-brand-950 px-4 py-3 text-sm font-semibold text-white">
        <span className="flex items-center gap-2"><Bot className="h-4 w-4" /> Chatbot de exemplo</span>
        <button onClick={() => setAberto(false)} aria-label="Fechar"><X className="h-4 w-4" /></button>
      </div>

      <div className="flex flex-1 flex-col gap-2 overflow-y-auto p-3 text-sm">
        {mensagens.map((m, i) => (
          <p
            key={i}
            className={m.de === 'bot'
              ? 'max-w-[85%] self-start rounded-lg bg-gray-100 px-3 py-2'
              : 'max-w-[85%] self-end rounded-lg bg-brand-100 px-3 py-2'}
          >
            {m.texto}
          </p>
        ))}
      </div>

      <div className="border-t border-gray-100 p-3 text-sm">
        {encerrada ? (
          <button onClick={reiniciar} className="w-full rounded-lg border border-gray-300 py-2">Nova conversa</button>
        ) : passo?.pedirConsentimento ? (
          <button
            disabled={aguardando}
            onClick={() => void chamarPasso(`${BASE}/${passo.sessaoId}/consentimento`, { aceito: true }, 'Aceito')}
            className="w-full rounded-lg bg-brand-700 py-2 font-semibold text-white disabled:opacity-60"
          >
            Aceito o termo
          </button>
        ) : passo?.concluida ? (
          <button disabled={aguardando} onClick={concluir} className="w-full rounded-lg bg-brand-700 py-2 font-semibold text-white disabled:opacity-60">
            Enviar respostas
          </button>
        ) : pergunta?.tipo === 'LISTA_OPCOES' ? (
          <div className="flex flex-wrap gap-2">
            {pergunta.opcoes?.map((opcao) => (
              <button key={opcao} disabled={aguardando} onClick={(e) => responder(e, opcao, opcao)} className="rounded-full border border-brand-700 px-3 py-1 text-brand-800">
                {opcao}
              </button>
            ))}
          </div>
        ) : pergunta?.tipo === 'MULTIPLA_ESCOLHA' ? (
          <form onSubmit={(e) => responder(e, marcados, marcados.join(', ') || '(nenhuma)')} className="flex flex-col gap-2">
            <div className="flex flex-wrap gap-3">
              {pergunta.opcoes?.map((opcao) => (
                <label key={opcao} className="flex items-center gap-1">
                  <input
                    type="checkbox"
                    checked={marcados.includes(opcao)}
                    onChange={(e) => setMarcados(e.target.checked ? [...marcados, opcao] : marcados.filter((m) => m !== opcao))}
                  />
                  {opcao}
                </label>
              ))}
            </div>
            <button disabled={aguardando} className="rounded-lg bg-brand-700 py-1.5 font-semibold text-white">Responder</button>
          </form>
        ) : pergunta ? (
          <form onSubmit={(e) => responder(e, texto, texto || '(pular)')} className="flex gap-2">
            <input
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder={pergunta.obrigatorio ? pergunta.placeholder ?? '' : 'Opcional: envie vazio para pular'}
              className="flex-1 rounded-lg border border-gray-300 px-2 py-1.5 focus:border-brand-700 focus:outline-none"
            />
            <button disabled={aguardando} aria-label="Enviar" className="rounded-lg bg-brand-700 px-3 text-white disabled:opacity-60">
              <Send className="h-4 w-4" />
            </button>
          </form>
        ) : (
          <p className="text-xs text-gray-500">Aguarde...</p>
        )}
      </div>
    </div>
  );
}
