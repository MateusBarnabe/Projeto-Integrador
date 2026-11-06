/*
 * Envios recebidos pelo placeholder. Ficam em memória e somem quando o servidor reinicia
 * (decisão X10: é ferramenta de teste local; o Landing real guarda no schema dele).
 */
import { randomUUID } from 'node:crypto';
import { contatoDasRespostas, type Formulario } from './formulario.ts';
import type { Origem, Valor } from './validacao.ts';

export type Canal = 'formulario' | 'landing_page' | 'embed' | 'chatbot';

export interface EnvioGravado {
  id: string;
  tenantId: string;
  formularioId: string;
  paginaId: string | null;
  canal: Canal;
  /** Mapa `campoId → valor`, já validado */
  respostas: Record<string, Valor>;
  origem: Origem;
  /** Texto do termo vigente no aceite; sem IP nem User-Agent (o contrato do Landing não tem) */
  consentimento: { aceito: true; texto: string; aceitoEm: string };
  empresaId: string | null;
  contatoId: string | null;
  recebidoEm: string;
  anonimizado: boolean;
}

const envios = new Map<string, EnvioGravado>();

export function gravarEnvio(
  formulario: Formulario,
  dados: Pick<EnvioGravado, 'paginaId' | 'canal' | 'respostas' | 'origem'> & { aceitoEm?: string },
): EnvioGravado {
  const agora = new Date().toISOString();
  const envio: EnvioGravado = {
    id: randomUUID(),
    tenantId: formulario.tenantId,
    formularioId: formulario.id,
    paginaId: dados.paginaId,
    canal: dados.canal,
    respostas: dados.respostas,
    origem: dados.origem,
    consentimento: { aceito: true, texto: formulario.textoConsentimento, aceitoEm: dados.aceitoEm ?? agora },
    // preenchidos pela chamada ao stub do CRM (tarefa L4)
    empresaId: null,
    contatoId: null,
    recebidoEm: agora,
    anonimizado: false,
  };
  envios.set(envio.id, envio);
  // só ids e canal: nada de resposta, contato, IP ou User-Agent em log
  console.log(`Envio ${envio.id} gravado (formulário ${envio.formularioId}, canal ${envio.canal}, tenant ${envio.tenantId})`);
  return envio;
}

export function buscarEnvio(id: string): EnvioGravado | undefined {
  return envios.get(id);
}

/** Schema `Envio` do landing.yaml (respostas na ordem dos campos do formulário). */
export function envioParaContrato(envio: EnvioGravado, formulario: Formulario) {
  const contato = contatoDasRespostas(formulario, envio.respostas);
  return {
    id: envio.id,
    formularioId: envio.formularioId,
    formularioNome: formulario.nome,
    paginaId: envio.paginaId,
    canal: envio.canal,
    nome: envio.anonimizado ? null : contato.nome,
    email: envio.anonimizado ? null : contato.email,
    recebidoEm: envio.recebidoEm,
    anonimizado: envio.anonimizado,
    respostas: formulario.campos.map((campo) => ({
      campoId: campo.id,
      rotulo: campo.rotulo,
      tipo: campo.tipo,
      valor: envio.anonimizado ? null : (envio.respostas[campo.id] ?? null),
    })),
    origem: envio.origem,
    consentimento: envio.consentimento,
    empresaId: envio.empresaId,
    contatoId: envio.contatoId,
  };
}
