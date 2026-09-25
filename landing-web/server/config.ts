/** Configuração do servidor, sempre por variável de ambiente (os padrões servem para rodar local). */
export const config = {
  porta: Number(process.env.PORTA ?? 8088),
  /** API do Marketing, de onde vem a definição do formulário (GET /api/marketing/formularios/{id}) */
  marketingApiUrl: process.env.MARKETING_API_URL ?? 'http://localhost:8087',
  /** RabbitMQ, conectando como mq_landing: o user_id das mensagens precisa bater com o usuário (§9.7) */
  rabbitmqUrl: process.env.RABBITMQ_URL ?? 'amqp://mq_landing:mq_landing@localhost:5672/plataforma',
};
