/*
 * Envios recebidos pelo placeholder. Ficam em memória: somem quando o servidor reinicia
 * (onde guardar de vez é a pendência X10, da tarefa L3).
 */
import { randomUUID } from 'node:crypto';
import type { Formulario } from './formulario.ts';
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
  return envio;
}

export function buscarEnvio(id: string): EnvioGravado | undefined {
  return envios.get(id);
}
