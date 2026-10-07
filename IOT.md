# 🚨 BRIEFING DE ENTREGA URGENTE: INGESTÃO DE LEADS & FILAS

Precisamos pausar temporariamente o fluxo de trabalho atual (F2) para focar em uma **entrega urgente**: a implementação do pipeline de recebimento e normalização de leads vindos de campanhas de marketing via mensageria/fila.

---

### 📌 1. Ponto de Controle (Para retomada posterior)
* **Status atual:** A F2 foi concluída na branch `feat/f2-fundacao-dados`; esta entrega foi aberta em uma branch separada para não misturar o trabalho urgente com a `main`.
* **Etapa para onde pulamos:** Implementação parcial de D2/E1: DTO, consumer, buffer de entrada, normalização e demonstração visual de leads enriquecidos.
* **Motivo:** Entrega urgente de integração do pipeline de captação de leads.
* *(Ao finalizar este bloco, retornaremos exatamente ao ponto pausado).*

---

### 📦 2. Estrutura do Payload do Lead (Dados Esperados da Fila)

Os dados chegam pela mensageria (RabbitMQ / Kafka / Redis) com o payload estruturado nas 4 camadas:

1. **Dados Declarados (Formulário):**
   * `nome`, `email`, `whatsapp` / `telefone`.

2. **Dados de Origem e Marketing (UTMs e URL):**
   * `utm_source` (canal/mídia: google, meta, linkedin, etc.)
   * `utm_medium` (formato: cpc, stories, email, etc.)
   * `utm_campaign` (nome da campanha estratégica)
   * `utm_content` (criativo/chamada específica)
   * `referrer` (URL da página de origem do clique)

3. **Dados de Contexto Técnico (Device & Rede):**
   * `device_type` (smartphone, desktop, tablet)
   * `os` e `browser` (inferidos via User-Agent)
   * `location` (cidade, estado, país inferidos por IP no backend)
   * `timezone` e `language` (configurações locais do aparelho)

4. **Dados Comportamentais (On-site via JS):**
   * `dwell_time_seconds` (tempo ativo na página)
   * `scroll_depth_percent` (marcos de rolagem: 25%, 50%, 75%, 100%)
   * `clicked_elements` (lista/array de CTAs e botões clicados antes da conversão)
   * `pages_visited` (histórico de navegação da sessão)

---

### 🛠️ 3. Diretrizes de Implementação

* **Consumo de Mensageria:** Configurar o listener/consumer para receber e desserializar o JSON acima.
* **Isolamento de Dependências Externas (MOCKS OBRIGATÓRIOS):**
  * Toda regra de negócio, serviço ou validação que depender de **outros grupos/módulos fora do escopo de marketing deve ser mockada e documentada**.
  * Crie esses mocks em **arquivos/classes totalmente separados** (ex: `mocks/` ou sufixo `*MockService`), com anotações claras ou profiles do Spring para que possamos deletar ou substituir facilmente por integrações reais depois, sem quebrar o código core.
* **Tratamento dos Dados:**
  * Validar e persistir os dados do lead vinculando seus metadados de tracking.
  * Preparar a estrutura para posterior disparo de réguas de automação de e-mail com base nesses atributos.

---

### 4. O que foi realizado nesta entrega

* DTO `LeadEnriquecido` com dados declarados, UTMs, contexto técnico e comportamento on-site;
* envelope `LeadEnriquecidoEvento` com `id`, `tipo`, `tenantId` e `dados`;
* consumer RabbitMQ da fila `marketing.landing-leads`, com exchange `landing.eventos`, retry de três tentativas e DLQ;
* migration `V3__cria_leads_entrada_buffer_iot.sql` para o buffer previsto na D2;
* normalização de e-mail, localização mockada e armazenamento dos dados técnicos/comportamentais em JSONB;
* deduplicação por `evento_id`;
* mocks separados em `marketing-api/.../mocks/` para geolocalização, CRM e automação de e-mail;
* endpoint autenticado `GET /api/marketing/entradas`;
* tela de Leads para visualizar os registros recebidos no `marketing-web`;
* payload reproduzível em [Docs/mocks/lead-enriquecido.json](Docs/mocks/lead-enriquecido.json).

### 5. Como demonstrar localmente

1. Suba a infraestrutura local e a API com o consumer ativo:

```bash
docker compose up -d postgres rabbitmq mailpit
docker compose up -d --build marketing marketing-front
```

2. Publique o payload pelo RabbitMQ Management API. No PowerShell, a partir da raiz:

```powershell
$payload = [string](Get-Content .\Docs\mocks\lead-enriquecido.json -Raw)
$corpo = [ordered]@{ properties = [ordered]@{ content_type = 'application/json' }; routing_key = 'landing.lead.enriquecido'; payload = $payload; payload_encoding = 'string' } | ConvertTo-Json -Depth 10
$autorizacao = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin:admin'))
Invoke-RestMethod -Method Post -Uri 'http://localhost:15672/api/exchanges/plataforma/landing.eventos/publish' -Headers @{ Authorization = "Basic $autorizacao"; 'Content-Type' = 'application/json' } -Body $corpo
```

3. Configure um JWT de usuário com `marketing.lead.ver` em `marketing-web/.env` como `VITE_TOKEN_DEV`, abra `/modulos/marketing/leads/` e atualize a tela. O registro aparecerá no buffer após o consumer processar a mensagem.

### 6. Ponto de retomada

Esta entrega não substitui a D1/E1 definitivas. Ao retomá-la:

* confirmar o contrato final dos eventos com o Grupo 7;
* reconciliar a migration V3 com a D2 oficial, mantendo `leads_entrada_buffer` como tabela de transição;
* substituir os mocks pelo cliente CRM da C1/F5, geolocalização real e automação E8/E9;
* implementar a drenagem do buffer para `leads` na E2, sem duplicar o consumer;
* manter a branch `feat/iot-ingestao-leads` como histórico da entrega urgente.