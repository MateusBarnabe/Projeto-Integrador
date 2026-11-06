/*
 * Publicação dos eventos do Landing em `landing.eventos` (topic), como o módulo do Grupo 7:
 * envelope da §9.7 com `moduloOrigem: "landing"`, routing key igual ao `tipo` e propriedade
 * AMQP `user_id` igual ao usuário da conexão (`mq_landing`), senão a plataforma recusa.
 * Formatos em `contratos/landing.asyncapi.yaml` do infra-integrador-2026.
 */
import { randomUUID } from 'node:crypto';
import amqp from 'amqplib';
import type { ConfirmChannel } from 'amqplib';
import { config } from './config.ts';

export const EXCHANGE = 'landing.eventos';

export interface Envelope<T> {
  id: string;
  tipo: string;
  versao: number;
  tenantId: string;
  moduloOrigem: 'landing';
  ocorridoEm: string;
  /** Nulo: os dois eventos nascem na superfície pública, sem usuário logado */
  usuarioId: string | null;
  correlacaoId: string | null;
  dados: T;
}

export function envelope<T>(tipo: string, tenantId: string, dados: T, correlacaoId?: string | null): Envelope<T> {
  return {
    id: randomUUID(),
    tipo,
    versao: 1,
    tenantId,
    moduloOrigem: 'landing',
    ocorridoEm: new Date().toISOString(),
    usuarioId: null,
    correlacaoId: correlacaoId ?? null,
    dados,
  };
}

const usuarioRabbit = decodeURIComponent(new URL(config.rabbitmqUrl).username) || 'guest';
let canal: Promise<ConfirmChannel> | null = null;

/** Abre a conexão na primeira publicação e a refaz se cair. */
function obterCanal(): Promise<ConfirmChannel> {
  if (!canal) {
    canal = (async () => {
      const conexao = await amqp.connect(config.rabbitmqUrl, { timeout: 3000 });
      const esquecer = () => {
        canal = null;
      };
      conexao.on('close', esquecer);
      conexao.on('error', esquecer);
      const confirmavel = await conexao.createConfirmChannel();
      confirmavel.on('close', esquecer);
      confirmavel.on('error', esquecer);
      return confirmavel;
    })();
    canal.catch(() => {
      canal = null;
    });
  }
  return canal;
}

/**
 * Publica o evento e espera a confirmação do broker. Chamar só depois de gravar o fato.
 * Se o RabbitMQ estiver fora, o evento se perde (como no módulo real sem outbox) e o log leva só
 * tipo, id e tenant, nunca os `dados` (que têm contato).
 */
export async function publicar<T>(evento: Envelope<T>): Promise<boolean> {
  try {
    const confirmavel = await obterCanal();
    confirmavel.publish(EXCHANGE, evento.tipo, Buffer.from(JSON.stringify(evento)), {
      persistent: true,
      contentType: 'application/json',
      contentEncoding: 'utf-8',
      userId: usuarioRabbit,
      messageId: evento.id,
      type: evento.tipo,
      correlationId: evento.correlacaoId ?? undefined,
      timestamp: Math.floor(Date.parse(evento.ocorridoEm) / 1000),
      appId: 'landing',
    });
    await confirmavel.waitForConfirms();
    console.log(`Evento ${evento.tipo} publicado em ${EXCHANGE} (id ${evento.id}, tenant ${evento.tenantId})`);
    return true;
  } catch (falha) {
    // ECONNREFUSED chega como AggregateError, sem mensagem; o código basta
    const motivo = (falha as { code?: string }).code ?? (falha instanceof Error ? falha.message : String(falha));
    console.error(`Falha ao publicar ${evento.tipo} em ${EXCHANGE} (id ${evento.id}, tenant ${evento.tenantId}): ${motivo}`);
    return false;
  }
}
