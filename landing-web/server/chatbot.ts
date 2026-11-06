/*
 * Chatbot de exemplo: as sessões da superfície pública do landing.yaml (Público — Chatbot e
 * origem), em memória. As perguntas são os campos do formulário, na ordem; o consentimento é
 * pedido antes da primeira pergunta de contato (e-mail ou telefone).
 */
import { randomUUID } from 'node:crypto';
import { campoPublico, contatoDasRespostas, type Campo, type Formulario } from './formulario.ts';
import { validarValor, type Erro, type Origem, type Valor } from './validacao.ts';

export interface Sessao {
  id: string;
  formulario: Formulario;
  paginaId: string | null;
  origem: Origem;
  respostas: Record<string, Valor>;
  aceitoEm: string | null;
  leadCapturado: boolean;
}

const sessoes = new Map<string, Sessao>();

const ehContato = (campo: Campo) => campo.tipo === 'EMAIL' || campo.tipo === 'TELEFONE';

export function iniciarSessao(formulario: Formulario, paginaId: string | null, origem: Origem): Sessao {
  const sessao: Sessao = { id: randomUUID(), formulario, paginaId, origem, respostas: {}, aceitoEm: null, leadCapturado: false };
  sessoes.set(sessao.id, sessao);
  return sessao;
}

/** Sessão aberta deste formulário; de outro formulário ou já concluída, `undefined` (404). */
export function buscarSessao(formulario: Formulario, sessaoId: string): Sessao | undefined {
  const sessao = sessoes.get(sessaoId);
  return sessao?.formulario.id === formulario.id ? sessao : undefined;
}

export function encerrarSessao(sessao: Sessao) {
  sessoes.delete(sessao.id);
}

function perguntaAtual(sessao: Sessao): Campo | undefined {
  return sessao.formulario.campos.find((campo) => !(campo.id in sessao.respostas));
}

/** `PassoChatbot` do contrato. */
export function passo(sessao: Sessao) {
  const pergunta = perguntaAtual(sessao);
  const pedirConsentimento = pergunta !== undefined && ehContato(pergunta) && sessao.aceitoEm === null;
  return {
    sessaoId: sessao.id,
    pedirConsentimento,
    textoConsentimento: pedirConsentimento ? sessao.formulario.textoConsentimento : null,
    proximaPergunta: pergunta && !pedirConsentimento ? campoPublico(pergunta) : null,
    concluida: pergunta === undefined,
  };
}

export function consentir(sessao: Sessao) {
  sessao.aceitoEm ??= new Date().toISOString();
}

/**
 * Grava a resposta da pergunta atual. `capturou` é verdadeiro uma vez por sessão: na primeira
 * resposta que deixa e-mail ou telefone válido com o consentimento já aceito.
 */
export function responder(sessao: Sessao, campoId: unknown, valor: unknown): { erro: Erro } | { capturou: boolean } {
  const pergunta = perguntaAtual(sessao);
  if (!pergunta || pergunta.id !== campoId) {
    return { erro: { campo: 'campoId', codigo: 'PERGUNTA_INVALIDA', detalhe: 'Responda a pergunta atual da conversa.' } };
  }
  if (ehContato(pergunta) && sessao.aceitoEm === null) {
    return { erro: { campo: 'consentimento', codigo: 'CONSENTIMENTO_OBRIGATORIO', detalhe: 'Aceite o termo antes de informar o contato.' } };
  }

  const resultado = validarValor(pergunta, valor);
  if ('erro' in resultado) return resultado;
  sessao.respostas[pergunta.id] = resultado.valor;

  const contato = contatoDasRespostas(sessao.formulario, sessao.respostas);
  const capturou = !sessao.leadCapturado && sessao.aceitoEm !== null && (contato.email !== null || contato.telefone !== null);
  if (capturou) sessao.leadCapturado = true;
  return { capturou };
}
