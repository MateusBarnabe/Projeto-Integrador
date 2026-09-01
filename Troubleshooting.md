## 9. Monitoramento, Logs e Troubleshooting

### 9.1. Monitoramento e Logs de Execução
* **Logs de Entrada:** Registro de cada payload recebido na camada de automação contendo: `status_code`, `lead_id`, `utm_campaign`, `timestamp` e `payload_raw`.
* **Tratamento de Exceções e Erros:**
  * **Erro 4xx (Validação/Payload Inválido):** Log do erro e descarte ou encaminhamento para fila de quarentena.
  * **Erro 5xx (Instabilidade no CRM/API Externa):** Fila de retentativa automática (*Dead Letter Queue*) com 3 tentativas espaçadas (1 min, 5 min, 15 min).

### 9.2. Alertas em Tempo Real
* **Alerta de Quebra de Integração:** Disparo de notificação imediata (Slack/Teams/E-mail técnico) se a taxa de falha dos webhooks ultrapassar **3%** em uma janela de 10 minutos.
* **Alerta de Inatividade de Conversão:** Notificação automática caso uma Landing Page com tráfego ativo passe mais de 4 horas sem registrar nenhuma submissão de formulário.

### 9.3. Procedimento Operacional Padrão: Nova Campanha / UTM
1. Criar a nova campanha na plataforma de anúncios garantindo a nomenclatura padronizada de UTMs.
2. Validar se a Landing Page de destino está com os *scripts* de captura de campos ocultos ativos.
3. Realizar um teste de submissão (Lead Teste) antes da liberação de orçamento.
4. Conferir se o Lead Teste entrou no CRM com a respectiva tag de campanha, pontuação correta de *score* e roteamento para o vendedor designado.