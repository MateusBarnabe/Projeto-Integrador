/*
 * Publicação dos eventos do CRM em crm.eventos (crm.asyncapi.yaml), com o envelope da §9.7.
 * Conecta como mq_crm; a propriedade AMQP user_id vai com o mesmo usuário da conexão, senão o
 * RabbitMQ recusa a mensagem. Usa canal com confirmação para saber que o broker recebeu.
 */
import { randomUUID } from 'node:crypto';
import amqp from 'amqplib';
import { config } from './config.ts';

const EXCHANGE = 'crm.eventos';
const usuario = decodeURIComponent(new URL(config.rabbitmqUrl).username) || 'guest';

export interface EnvelopeEvento<T> {
  id: string;
  tipo: string;
  versao: number;
  tenantId: string;
  moduloOrigem: 'crm';
  ocorridoEm: string;
  usuarioId: string | null;
  correlacaoId: string | null;
  dados: T;
}

let canal: Promise<amqp.ConfirmChannel> | null = null;

function abrirCanal(): Promise<amqp.ConfirmChannel> {
  canal ??= (async () => {
    const conexao = await amqp.connect(config.rabbitmqUrl, { timeout: 3000 });
    const esquecer = () => {
      canal = null;
    };
    conexao.on('error', (e) => console.error(`[rabbitmq] erro na conexão: ${e.message}`));
    conexao.on('close', esquecer);
    const ch = await conexao.createConfirmChannel();
    ch.on('error', (e) => console.error(`[rabbitmq] erro no canal: ${e.message}`));
    ch.on('close', esquecer);
    // A exchange já vem do definitions.json; declarar de novo com os mesmos atributos não muda nada
    await ch.assertExchange(EXCHANGE, 'topic', { durable: true, autoDelete: false });
    return ch;
  })().catch((e: unknown) => {
    canal = null;
    throw e;
  });
  return canal;
}

/** Publica um evento em crm.eventos, com a chave de roteamento igual ao tipo. Devolve o envelope enviado. */
export async function publicar<T>(
  tipo: string,
  tenantId: string,
  dados: T,
  extras: { usuarioId?: string | null; correlacaoId?: string | null } = {},
): Promise<EnvelopeEvento<T>> {
  const envelope: EnvelopeEvento<T> = {
    id: randomUUID(),
    tipo,
    versao: 1,
    tenantId,
    moduloOrigem: 'crm',
    ocorridoEm: new Date().toISOString(),
    usuarioId: extras.usuarioId ?? null,
    correlacaoId: extras.correlacaoId ?? null,
    dados,
  };
  const ch = await abrirCanal();
  ch.publish(EXCHANGE, tipo, Buffer.from(JSON.stringify(envelope)), {
    userId: usuario,
    appId: 'crm',
    messageId: envelope.id,
    type: tipo,
    contentType: 'application/json',
    contentEncoding: 'utf-8',
    persistent: true,
    timestamp: Math.floor(Date.now() / 1000),
  });
  await ch.waitForConfirms();
  return envelope;
}
