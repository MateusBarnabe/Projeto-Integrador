/** Configuração do servidor, sempre por variável de ambiente (os padrões servem para rodar local). */
export const config = {
  porta: Number(process.env.PORTA ?? 8088),
  /** RabbitMQ, conectando como mq_landing: o user_id das mensagens precisa bater com o usuário (§9.7) */
  rabbitmqUrl: process.env.RABBITMQ_URL ?? 'amqp://mq_landing:mq_landing@localhost:5672/plataforma',
};
