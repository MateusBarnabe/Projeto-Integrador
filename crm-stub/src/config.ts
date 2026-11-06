/** Configuração do stub, sempre por variável de ambiente (os padrões servem para rodar local). */
export const config = {
  /** Mesma porta do CRM real (crm.yaml: http://crm:8082) */
  porta: Number(process.env.PORTA ?? 8082),
  /** RabbitMQ, conectando como mq_crm: o user_id das mensagens precisa bater com o usuário (§9.7) */
  rabbitmqUrl: process.env.RABBITMQ_URL ?? 'amqp://mq_crm:mq_crm@localhost:5672/plataforma',
};
