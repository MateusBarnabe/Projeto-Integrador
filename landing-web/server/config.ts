/** Configuração do servidor, sempre por variável de ambiente (os padrões servem para rodar local). */
export const config = {
  porta: Number(process.env.PORTA ?? 8088),
  /** RabbitMQ, conectando como mq_landing: o user_id das mensagens precisa bater com o usuário (§9.7) */
  rabbitmqUrl: process.env.RABBITMQ_URL ?? 'amqp://mq_landing:mq_landing@localhost:5672/plataforma',
  /** API do CRM (stub local ou o real), chamada direto, sem gateway (§9.1); no compose, http://crm:8082 */
  crmBaseUrl: (process.env.CRM_BASE_URL ?? 'http://localhost:8082').replace(/\/+$/, ''),
};
