/*
 * Os dois eventos do landing.asyncapi.yaml. Nenhum traz a etapa do funil: quem decide é o Marketing.
 */
import type { EnvioGravado } from './envios.ts';
import { envelope, publicar } from './mensageria.ts';
import type { ToqueOrigem } from './validacao.ts';

/** `landing.formulario.recebido`: só ids e canal; contato, respostas e origem o Marketing lê na API. */
export function publicarFormularioRecebido(envio: EnvioGravado, correlacaoId: string | null) {
  return publicar(envelope('landing.formulario.recebido', envio.tenantId, {
    envioId: envio.id,
    formularioId: envio.formularioId,
    empresaId: envio.empresaId,
    contatoId: envio.contatoId,
    canal: envio.canal,
  }, correlacaoId));
}

export interface LeadCapturado {
  tenantId: string;
  sessaoId: string;
  formularioId: string;
  paginaId: string | null;
  contato: { nome: string | null; email: string | null; telefone: string | null };
  /** Última origem do visitante */
  ultimaOrigem: ToqueOrigem;
  aceitoEm: string;
}

/** `landing.chatbot.lead_capturado`: uma vez por sessão, com o mínimo de contato (§9.7). */
export function publicarLeadCapturado(lead: LeadCapturado, correlacaoId: string | null) {
  return publicar(envelope('landing.chatbot.lead_capturado', lead.tenantId, {
    sessaoId: lead.sessaoId,
    formularioId: lead.formularioId,
    paginaId: lead.paginaId,
    contato: lead.contato,
    origem: {
      utmSource: lead.ultimaOrigem.utmSource,
      utmMedium: lead.ultimaOrigem.utmMedium,
      utmCampaign: lead.ultimaOrigem.utmCampaign,
    },
    consentimento: { aceito: true as const, aceitoEm: lead.aceitoEm },
  }, correlacaoId));
}
