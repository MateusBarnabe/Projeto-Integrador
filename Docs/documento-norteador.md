# Documento Norteador — Módulo de Marketing (Grupo 4)

Código `marketing` · Schema `marketing` · Projeto Integrador 2026 · Grupo 4 · Revisão de 24/09/2026

*Documento norteador — incrementado conforme o projeto avança · Alinhado ao Contrato de Integração v0.7*

> **Base desta revisão:** repositório `infra-integrador-2026` do Grupo 2. Foram usados o Contrato de Integração **v0.7** (PR #5, ainda não mergeado; objeções até 24/09), o Mapa de Fronteiras v0.2, o checklist de conformidade, os contratos e permissões já publicados e o módulo de exemplo. Onde este documento diz *§*, a referência é a seção do Contrato de Integração.
>
> O que outros grupos precisam saber de nós está separado em [`contratos/`](contratos/); este documento continua sendo a fonte da verdade do Grupo 4.
>
> Marcadores usados:
> - **[Pendente · Grupo 7]** ou **[Pendente · Grupo 6]** depende de alinhamento com esse grupo.
> - **[Novo]** foi acrescentado para cumprir o contrato.
> - **[Regra da infra]** é obrigatório e vem de fora do grupo.

## Índice

1. [Enquadramento na plataforma](#1-enquadramento-na-plataforma)
2. [Funil de leads](#2-funil-de-leads)
3. [Tabelas](#3-tabelas)
4. [Jobs em background](#4-jobs-em-background)
5. [Fluxo de escrita](#5-fluxo-de-escrita)
6. [Endpoints](#6-endpoints)
7. [Permissões e menu](#7-permissões-e-menu)
8. [Mensageria](#8-mensageria)
9. [Front-end e telas](#9-front-end-e-telas)
   - [9.1 Painel](#91-painel)
   - [9.2 Configurações](#92-configurações)
   - [9.3 Definição do formulário](#93-definição-do-formulário-pendente--grupo-7)
10. [Testes e conformidade](#10-testes-e-conformidade)
11. [Pendências e decisões em aberto](#11-pendências-e-decisões-em-aberto)
12. [Registro de alterações](#12-registro-de-alterações)

---

## 1. Enquadramento na plataforma

O módulo roda dentro da plataforma do Grupo 2 e herda dela nomes, portas, credenciais e regras. Nada disto é decisão do Grupo 4.

### Identificação [Regra da infra]

| Item | Valor | Fonte |
|---|---|---|
| Código do módulo | `marketing`, nunca muda; é prefixo de schema, rota, container e permissão | §1 |
| API | porta `8087`, rotas em `/api/marketing/**` | §13.3 |
| Front | porta `3007`, servido em `/modulos/marketing/` | §12.8 |
| Rotas públicas | `/public/marketing/**`, sem token, com rate limit no gateway | §12.6 |
| Banco | schema `marketing`; `own_marketing` roda o Flyway, `usr_marketing` roda a aplicação | §7.1 |
| RabbitMQ | usuário `mq_marketing`; publica só em `marketing.eventos` e `identity.entrada` | §9.7, §13.3 |
| Token de serviço | `clientId = marketing`, segredo em `SVC_MARKETING_SEGREDO` | §9.2 |
| Stack | Java 21, Spring Boot 3.x, Maven, Flyway, Spring AMQP; React + Vite + TypeScript, Tailwind v4 | §13.1 |
| Imagens | `ghcr.io/{conta}/marketing` e `marketing-front`, publicadas a cada merge com `:latest` e `:sha-{commit}` | §14.3 |

### Escopo atribuído pelo Mapa de Fronteiras

| Seções do Prompt Mestre | Situação no Mapa | Neste documento |
|---|---|---|
| 58–59 · Tracking e UTM | com Grupo 7 | UTM capturada pelo Landing, recebida nos eventos (seção 8) e guardada em `leads` |
| 60 · Construtor visual de automação | definido | Não coberto. Lacuna registrada na seção 11 |
| 61–62 · Gatilhos e ações | compartilhado | Só a cadência de aquecimento (4.2). Lacuna registrada na seção 11 |
| 63 · Campanhas de e-mail | definido | Só e-mail de aquecimento. Lacuna registrada na seção 11 |
| 64 · Opt-out | definido | Descadastro público e anonimização (4.3, 6) |
| 65–67 · WhatsApp, e-mail, telefonia | sem dono | Só e-mail. WhatsApp fica como evolução futura |

### Duas superfícies (§12.4–12.5)

| | Autenticada (casca, iframe) | Pública (sem login) |
|---|---|---|
| O que é do marketing | painel, leads, configurações, modelos de mensagem, ciclo de vida, formulário | webhook do Meta Ads e página de descadastro. O visitante da landing page é atendido pelo Landing, não pelo Marketing |
| Tenant | claim `tenant_id` do token | subdomínio ou slug, nunca parâmetro alterável |
| Devolve dado de negócio | sim, conforme permissão | nunca; só recebe informação |

> **Divisão com o Grupo 7 (Landing Pages), definida em 24/09.**
>
> 1. O **Landing** atende o visitante e avisa cada fato por **evento no RabbitMQ**, na exchange `landing.eventos`: visita por anúncio, clique em "fale conosco", contato preenchido, formulário em andamento e formulário enviado.
> 2. Cada evento traz a **etapa** (0 a 3), **decidida pelo Landing**, inclusive o critério de "grande parte do formulário" da etapa 2. O Marketing grava a etapa recebida e só impede que ela volte para trás.
> 3. O evento traz contato, consentimento e UTM. As respostas do formulário o Marketing consulta na API do Landing, pelo `envioId`.
> 4. Na etapa 3, o Landing cria ou reaproveita **empresa e contato** no CRM, como no PR #16. **A oportunidade é o Marketing que cria.**
>
> O que pedimos ao Landing está em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md), com os eventos em AsyncAPI. A proposta ainda precisa do aceite deles. O dono da definição do formulário continua em aberto (9.3).

---

## 2. Funil de leads

| Etapa | Descrição | Temperatura | Persistência | Fonte |
|---|---|---|---|---|
| 0 | Veio de anúncio e não interagiu: lead do Meta Ads (pré-preenchido, passivo) ou visitante da landing com UTM de campanha. Ou clicou em "fale conosco" e não preencheu nada | Frio | Batch (via buffer) | webhook do Meta; `landing.visita.registrada`; `landing.formulario.aberto` |
| 1 | Clicou em "fale conosco" e preencheu os dados de contato: nome, telefone e e-mail | Frio | Batch (via buffer) | `landing.contato.informado` |
| 2 | Preencheu grande parte do formulário, não enviou | Médio | Tempo real | `landing.formulario.atualizado` |
| 3 | Enviou o formulário completo; falta assinar contrato | Quente | Tempo real + handoff | `landing.formulario.recebido` |

Nos leads da landing, **a etapa vem no evento, decidida pelo Landing** (campo `etapa`). O Marketing não recalcula: grava a etapa recebida e ignora uma etapa menor do que a que o lead já tem. O critério da etapa 2 ("grande parte do formulário") é do Landing, que só publica `landing.formulario.atualizado` quando ele é atingido. Precisamos conhecer esse critério (seção 11). Nos leads do Meta, a etapa é sempre 0.

Fluxo de saída: `LEAD → CRM (oportunidade) → PROPOSTA → CONTRATO`. Na etapa 3, o Landing cria empresa e contato no CRM e o Marketing cria a oportunidade (seção 6). Propostas estão sem dono no Mapa de Fronteiras (seções 18–19), e o PR #13 do CRM declara que não são dele.

---

## 3. Tabelas

> **Regras que valem para todas as tabelas [Regra da infra]**
>
> - As **sete colunas obrigatórias** (§7.2): `id uuid`, gerado pela aplicação; `tenant_id uuid not null`, sempre indexado; `created_at`, `updated_at` e `deleted_at` em `timestamptz` UTC, com `deleted_at` nulo significando ativo (soft delete); `created_by` e `updated_by` em `uuid`, vindos do claim `sub`. Nas tabelas abaixo elas aparecem resumidas como "colunas obrigatórias".
> - Em registros criados por rotina automática ou por rota pública, sem usuário, `created_by` e `updated_by` ficam nulos.
> - Nomes em `snake_case`, minúsculos, sem acento e no plural. Toda mudança de estrutura passa por migration do Flyway, rodando como `own_marketing`.
> - Dinheiro em `numeric(15,2)` e percentual em `numeric(9,4)`, em pontos percentuais (§8.7).
> - `FOREIGN KEY` só dentro do schema `marketing`. Referências a outros módulos guardam apenas o UUID (§7.1, §10.5).

### `leads`

Registro central do lead. Existe quando há um identificador de contato: leads do Meta (etapa 0, já pré-preenchidos) e visitantes da landing da etapa 1 em diante. Visitante de etapa 0 sem contato fica só no buffer.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| etapa | smallint | 0–3 |
| temperatura | varchar, GENERATED ALWAYS AS | espelho da etapa: 0/1→frio, 2→médio, 3→quente. Sem override manual por ora |
| origem | varchar | `meta_ads`, `landing_page`, `manual` |
| nome / email / telefone / whatsapp | varchar, nullable | podem não existir nas etapas 0/1. `whatsapp` é só dado de contato; o canal não é usado nesta fase |
| utm_source / utm_medium / utm_campaign / utm_term / utm_content | varchar, nullable | |
| landing_page_url | varchar, nullable | de `origem.landingPageUrl`. As colunas `utm_*` vêm do mesmo objeto `origem`; a primeira UTM recebida não é sobrescrita por valor vazio |
| first_touch / last_touch | timestamptz, nullable | `first_touch` vem de `origem.primeiraVisitaEm` nos eventos do Landing |
| visitante_id **[Novo]** | uuid, nullable | `visitanteId` do Landing, o mesmo da etapa 0 à 3. **UNIQUE (tenant_id, visitante_id)**. Nulo em lead do Meta |
| envio_id **[Novo]** | uuid, nullable | `envioId` do Landing, o mesmo da etapa 1 à 3. Usado para consultar as respostas |
| formulario_id **[Novo]** | uuid, nullable | `formularioId` do Landing, só o UUID |
| formulario_versao | int, nullable | `formularioVersao` do Landing. Antes `form_definition_version` |
| dados_formulario | jsonb | cópia das respostas consultadas em `GET /api/landing/envios/{envioId}` na etapa 3, como registro histórico do fato (§9.5) |
| consentimento_marketing **[Novo]** | boolean, default false | o lead aceitou receber comunicação de marketing (LGPD). Sem ele, o job 4.2 não envia |
| consentimento_em **[Novo]** | timestamptz, nullable | quando o consentimento foi registrado no Landing |
| meta_lead_id | varchar, nullable | dedupe de reentrega do webhook do Meta. **UNIQUE (tenant_id, meta_lead_id)**: a unicidade é por tenant |
| crm_empresa_id / crm_contato_id **[Novo]** | uuid, nullable | recebidos em `landing.formulario.recebido`. Só o UUID, nunca FK entre schemas |
| crm_oportunidade_id | uuid, nullable | oportunidade criada pelo Marketing na etapa 3. Só o UUID |
| ultima_comunicacao_em | timestamptz, nullable | denormalizado, evita agregar `lead_comunicacoes` a cada execução do job |
| tentativas_aquecimento | int, default 0 | |
| status_aquecimento | varchar | `ativo`, `esgotado`, `opt_out` |
| esgotado_em | timestamptz, nullable | base da retenção após esgotamento |
| opt_out_em **[Novo]** | timestamptz, nullable | base da retenção após opt-out. Faltava para o job 4.3 calcular o prazo |
| token_descadastro **[Novo]** | varchar, UNIQUE | token opaco e aleatório do link de descadastro (seção 6). Não é token da plataforma |
| anonimizado | boolean, default false | |
| colunas obrigatórias | §7.2 | |

```sql
temperatura varchar GENERATED ALWAYS AS (
  CASE
    WHEN etapa IN (0,1) THEN 'frio'
    WHEN etapa = 2 THEN 'medio'
    WHEN etapa = 3 THEN 'quente'
  END
) STORED
```

> **Fronteira com Contato do CRM [Pendente · Grupo 6].** O CRM é o dono de empresas e contatos (§10), e o Mapa proíbe copiar a tabela de outro grupo. Até a etapa 2, o lead não existe no CRM e `leads` guarda os dados de contato de quem ainda *não* é contato. Na etapa 3, o Landing cria o contato e o lead passa a apontar para ele por `crm_contato_id`. Precisamos confirmar com o Grupo 6 que essa leitura atende à regra do dono único (seção 11).

### `lead_eventos` (antes `lead_events`)

Timeline e auditoria da progressão do lead, exigidas pelo Prompt Mestre em todo módulo.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| sequencia **[Novo]** | bigint GENERATED ALWAYS AS IDENTITY, UNIQUE | ordem global, usada como `id` das mensagens SSE (9.1). O `id` é uuid e não serve para isso |
| lead_id | uuid | FK para `leads.id`, no mesmo schema |
| tipo_evento | varchar | `etapa_alterada`, `formulario_atualizado`, `comunicacao_enviada`, `handoff_crm`, `descadastrado`, `anonimizado` |
| etapa_anterior / etapa_nova | smallint, nullable | |
| payload | jsonb | detalhes do evento, sem dado pessoal além do necessário |
| colunas obrigatórias | §7.2 | antes só havia `created_at` e `created_by` |

### `leads_entrada_buffer` (antes `lead_intake_buffer`)

Área de espera das etapas 0 e 1, esvaziada em lote por um job agendado (`@Scheduled`). Evita gravar direto no banco transacional a cada evento passivo.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | isolamento por tenant mantido mesmo no buffer |
| origem | varchar | `meta_ads`, `landing_page` |
| etapa **[Novo]** | smallint | 0 ou 1: a etapa que veio no evento do Landing, ou 0 no webhook do Meta |
| tipo_origem **[Novo]** | varchar | o que gerou a linha: `meta.webhook`, `landing.visita.registrada`, `landing.formulario.aberto`, `landing.contato.informado` |
| payload_bruto | jsonb | corpo do webhook ou `dados` do evento. Contém dado pessoal (ver retenção na seção 11) |
| meta_lead_id | varchar, nullable | |
| visitante_id **[Novo]** | uuid, nullable | `visitanteId` do Landing; liga as linhas de etapa 0 ao lead quando ele chega à etapa 1 |
| processado / processado_em | boolean default false / timestamptz | o momento do recebimento é o `created_at` (antes `recebido_em`) |
| tentativas | int, default 0 | incrementado a cada falha de processamento |
| falha_permanente | boolean, default false | marcado após N tentativas, para investigação manual |
| colunas obrigatórias **[Novo]** | §7.2 | |

> **Regra de promoção:** uma linha do buffer só vira registro em `leads` se tiver `meta_lead_id`, e-mail ou telefone. Cliques e eventos sem nenhum identificador de contato são marcados como `processado = true` sem gerar lead, para não poluir o funil com registros vazios.

### `lead_comunicacoes`

Histórico da comunicação de aquecimento. Nesta fase só por e-mail, sem chatbot.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id / lead_id | uuid | `lead_id` é FK para `leads` |
| canal | varchar | `email`. Coluna mantida para a entrada futura de outros canais |
| modelo_id | uuid | FK para `modelos_mensagem.id` (antes `template_id`) |
| status | varchar | `enviado`, `entregue`, `aberto`, `clicado`, `falhou`. Aberto e clicado exigem rastreio (seção 11) |
| enviado_em | timestamptz | |
| colunas obrigatórias | §7.2 | |

### `formularios` (antes `form_definitions`) [Pendente · Grupo 7]

Modelo configurável do formulário da landing page. Com a divisão de 24/09, o Landing já envia `formularioId` e `formularioVersao`, e as respostas vêm da API dele. Manter esta tabela ou usar só as referências do Landing é o ponto em aberto com o Grupo 7 (9.3).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| versao | int | |
| ativo | boolean | |
| campos | jsonb | array de `{id, label, tipo, obrigatorio, ordem, opcoes}`, sem normalizar enquanto os campos não estão fechados |
| colunas obrigatórias | §7.2 | |

### `canais_anuncio` (antes `marketing_settings`)

Preferências de referência por canal de anúncio. Não substitui as ferramentas de Ads: uma pessoa lê e configura manualmente no Ads Manager.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | UNIQUE (tenant_id, canal) |
| canal | varchar | `google_ads`, `meta_ads` |
| orcamento_mensal **[Novo]** | numeric(15,2), nullable | saiu do jsonb: dinheiro tem tipo fixo (§8.7). Mais de 2 casas responde `400 PRECISAO_EXCEDIDA` |
| configuracao | jsonb | `{publicoAlvo, objetivo}` |
| meta_app_secret / meta_verify_token **[Novo]** | varchar cifrado, nullable | só na linha `meta_ads`. Validam o webhook do tenant (seção 6). A API recebe o valor, mas nunca o devolve. Proposta a confirmar |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

### `modelos_mensagem` (antes `mensagem_templates`)

Modelos de aquecimento usados pelo job de comunicação das etapas frias (0/1). É conteúdo estruturado, diferente das preferências de canal acima.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| nome | varchar | identificador amigável, ex.: "Reengajamento 7 dias" |
| canal | varchar | `email` nesta fase |
| etapa_alvo | smallint, nullable | 0 ou 1; nulo vale para qualquer etapa fria |
| ordem_sequencia | int | posição na cadência (1º toque, 2º toque, ...) |
| dias_apos_anterior | int | dias de espera desde o toque anterior |
| assunto | varchar, not null | obrigatório, já que o único canal é e-mail |
| corpo | text | com variáveis como `{{nome}}` e `{{origem}}`, no mesmo padrão de variáveis dinâmicas do Prompt Mestre (seção 18). O link de descadastro entra em todo envio (4.2) |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

### `ciclo_vida_configuracoes` (antes `lifecycle_settings`)

Configuração do ciclo de vida do aquecimento, uma linha por tenant. Cada administrador define os próprios limites.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | `tenant_id` UNIQUE |
| max_tentativas_aquecimento | int | depois desse número de toques sem resposta, o lead vira `esgotado` |
| meses_retencao_pos_esgotamento | int | meses depois de `esgotado_em` até anonimizar |
| meses_retencao_pos_optout | int | meses depois de `opt_out_em` até anonimizar |
| colunas obrigatórias | §7.2 | |

### `eventos_processados` [Novo] [Regra da infra]

Obrigatória em todo schema que consome eventos (§9.7). Tem formato fixo pelo contrato: é tabela de infraestrutura, não de negócio.

```sql
CREATE TABLE marketing.eventos_processados (
  evento_id     uuid PRIMARY KEY,
  tipo          text NOT NULL,
  processado_em timestamptz NOT NULL DEFAULT now()
);
```

---

## 4. Jobs em background

> **Rotina sem usuário:** os jobs rodam fora de requisição, então não há token. Cada execução percorre os tenants e define o contexto de tenant antes de cada consulta, para o filtro por tenant continuar valendo. Mensagens publicadas por rotina levam `usuarioId: null` (§9.7). Se um job precisar chamar outro módulo, usa token de serviço, direto pelo nome do container e com `X-Tenant-Id`, nunca pelo gateway (§9.2).

### 4.1 Drenagem do buffer de entrada

- **Frequência:** a cada 5 minutos (`@Scheduled`), em lotes de até 500 linhas por execução
- **Concorrência segura:** `SELECT ... FOR UPDATE SKIP LOCKED` ao puxar linhas não processadas, para evitar processamento duplicado entre execuções sobrepostas ou várias instâncias
- **Resolução de identidade**, por linha do buffer e sempre dentro do mesmo tenant:
  1. Tem `meta_lead_id`? Procura exatamente esse valor em `leads`. Se existir, é reentrega (idempotência): atualiza, não duplica
  2. Tem `visitante_id`? Procura em `leads.visitante_id`. Se existir, é o mesmo visitante da landing: atualiza
  3. Sem nenhum dos dois, mas com e-mail ou telefone? Normaliza (e-mail em minúsculas e sem espaços; telefone só com dígitos, em E.164) e procura em `leads`. Se achar, faz merge sem sobrescrever dado já preenchido com dado mais pobre. Se não achar, cria um lead novo
  4. Sem `meta_lead_id`, e-mail ou telefone (visitante da landing na etapa 0)? **Não vira lead**; fica só como evento de analytics no buffer, ligado pelo `visitante_id` caso ele chegue depois à etapa 1
- **Regra de não-retrocesso:** se o lead encontrado já está na etapa 2 ou 3, uma linha do buffer com etapa 0/1 não rebaixa a etapa. Só atualiza dados complementares e registra o evento
- **Falha e retry:** a falha incrementa `tentativas`; depois de N tentativas, marca `falha_permanente` para investigação manual, sem travar o resto do lote

### 4.2 Comunicação de aquecimento

Roda diariamente. Envia a cadência de `modelos_mensagem` por e-mail para leads frios (etapa 0/1) que ainda não esgotaram as tentativas.

1. Seleciona leads com `etapa IN (0,1)`, `status_aquecimento = 'ativo'`, `consentimento_marketing = true`, e-mail preenchido e `now() - ultima_comunicacao_em >= dias_apos_anterior` do próximo modelo da sequência (posição `tentativas_aquecimento + 1`)
2. **Confere `status_aquecimento != 'opt_out'` antes de enviar.** É obrigatório pela seção 64 do Prompt Mestre (opt-out de marketing)
3. Envia o modelo correspondente, sempre com o link de descadastro `/public/marketing/descadastro/{token_descadastro}`. Grava em `lead_comunicacoes` com o `modelo_id`, incrementa `tentativas_aquecimento` e atualiza `ultima_comunicacao_em`
4. Se não há próximo modelo na sequência daquela etapa, ou se `tentativas_aquecimento` chegou a `ciclo_vida_configuracoes.max_tentativas_aquecimento`, marca `status_aquecimento = 'esgotado'` e grava `esgotado_em = now()`

> **Quem envia o e-mail:** o próprio módulo. A mensagem `identity.email.enviar` da plataforma aceita *só e-mail transacional*; campanhas e listas são do Marketing (AsyncAPI do identity e Mapa §3). Em desenvolvimento o destino é o Mailpit (`mailpit:1025`). Hoje o `docker-compose.yml` não entrega variáveis de SMTP ao serviço `marketing`, e isso está registrado como pendência com o Grupo 2.

O limite de tentativas é configurado por tenant em `ciclo_vida_configuracoes`, não fixado no código. WhatsApp fica fora desta fase: está sem dono no Mapa (seções 65–67) e entra como evolução futura.

### 4.3 Anonimização por retenção (LGPD)

Roda mensalmente. Não apaga a linha do lead, porque isso quebraria o histórico do funil e as métricas de conversão do painel. Remove só o dado pessoal identificável e preserva o valor estatístico.

1. Seleciona leads com `anonimizado = false` e (`status_aquecimento = 'esgotado'` com `now() - esgotado_em >= meses_retencao_pos_esgotamento`) ou (`status_aquecimento = 'opt_out'` com `now() - opt_out_em >= meses_retencao_pos_optout`)
2. Zera `nome`, `email`, `telefone`, `whatsapp` e `token_descadastro`. Mantém `etapa`, `origem`, `utm_*`, datas e contadores
3. Marca `anonimizado = true` e registra evento em `lead_eventos`

Os dois prazos de retenção são configurados por tenant em `ciclo_vida_configuracoes`, conforme a seção 83 do Prompt Mestre (LGPD).

---

## 5. Fluxo de escrita

1. **Etapa 0 do Meta:** o webhook grava em `leads_entrada_buffer`, com o tenant resolvido pelo slug do caminho e a assinatura validada (seção 6)
2. **Etapas 0 e 1 do Landing:** o consumidor da fila `marketing.landing-leads` recebe `landing.visita.registrada`, `landing.formulario.aberto` e `landing.contato.informado` e grava em `leads_entrada_buffer`, com o `tenantId` do envelope
3. **Drenagem:** o job 4.1 esvazia o buffer, faz upsert em `leads` (dedupe por `meta_lead_id`, `visitante_id`, e-mail ou telefone) e grava `lead_eventos`
4. **Etapa 2:** `landing.formulario.atualizado` é processado na chegada. O evento já traz `etapa: 2`. O Marketing escreve direto em `leads` + `lead_eventos`, sem passar pelo buffer
5. **Etapa 3:** `landing.formulario.recebido` é processado na chegada, em cinco passos:
   1. grava `crm_empresa_id` e `crm_contato_id`;
   2. consulta as respostas em `GET /api/landing/envios/{envioId}` e guarda em `dados_formulario`;
   3. cria a oportunidade no CRM com token de serviço;
   4. grava `crm_oportunidade_id`;
   5. registra em `lead_eventos`.

   Se `empresaId` vier nulo (CRM fora do ar no momento do envio), o lead fica na etapa 3 sem oportunidade até o Landing publicar o evento de novo com os ids

Todo consumo é idempotente (`eventos_processados`) e a etapa nunca volta para trás, mesmo que os eventos cheguem fora de ordem. Depois do commit de cada passo, o módulo pode publicar evento em `marketing.eventos` e pedir notificação ou registro na timeline (seção 8).

---

## 6. Endpoints

### Superfície pública [Regra da infra]

Rotas sem usuário ficam sob `/public/marketing/**`. O gateway as libera de token e aplica rate limit por IP (hoje 120 por minuto). Nenhuma devolve dado de negócio (§12.6). O Marketing não tem rota de intake: os leads da landing chegam por evento do Landing (seção 8).

| Método | Rota | Autenticação | Quem chama |
|---|---|---|---|
| GET | `/public/marketing/{subdominio}/meta/leads` | handshake do Meta: confere `hub.verify_token` com `meta_verify_token` do tenant e devolve `hub.challenge` | Meta (servidor) |
| POST | `/public/marketing/{subdominio}/meta/leads` | HMAC `X-Hub-Signature-256` calculado com o `meta_app_secret` do tenant | Meta (servidor) |
| GET / POST | `/public/marketing/descadastro/{token}` **[Novo]** | token opaco do lead | Destinatário do e-mail |
> **Como o tenant do webhook é resolvido.** O slug do caminho é o subdomínio do tenant. O módulo pergunta à plataforma a qual tenant ele pertence, em `GET /api/identity/tenants/resolver?subdominio=`, com o próprio token de serviço. A permissão `identity.tenant.ver` já foi concedida ao marketing em `permissoes/identity.yaml`. Subdomínio desconhecido responde `404`.
>
> Trocar o slug não dá acesso a outro tenant: a assinatura HMAC só confere com o App Secret daquele tenant. A chamada ao identity segue o timeout de 3 s (§9.6); a resposta pode ficar em cache curto.

> **Descadastro:** o `GET` mostra a confirmação e o `POST` efetiva o opt-out, gravando `status_aquecimento = 'opt_out'` e `opt_out_em` e registrando em `lead_eventos`. O opt-out não acontece no `GET` porque leitores de e-mail costumam abrir links automaticamente. Token inválido responde a mesma página neutra, para não revelar se o lead existe.

### Superfície autenticada

Token JWT no cabeçalho, validado localmente pelo JWKS do identity (§4.2). Permissão verificada no back-end com `@PreAuthorize` (§5.2). Tenant sempre vindo do claim `tenant_id`; um `tenantId` no corpo ou na query é ignorado (§6).

| Método | Rota | Permissão | Observação |
|---|---|---|---|
| GET | `/api/marketing/leads` | `marketing.lead.ver` | paginada: `pagina`, `tamanho` (máx. 100), `ordenar` |
| GET | `/api/marketing/leads/{id}` | `marketing.lead.ver` | lead de outro tenant responde 404 |
| GET | `/api/marketing/busca?q=` **[Novo]** | `marketing.lead.ver` | busca global (§8.6) |
| GET | `/api/marketing/painel` | `marketing.dashboard.ver` | antes `/dashboard` |
| GET | `/api/marketing/painel/eventos` (SSE) | `marketing.dashboard.ver` | antes `/stream`; ver 9.1 |
| GET / PUT | `/api/marketing/canais-anuncio` | `marketing.configuracao.ver` / `.editar` | antes `/settings` |
| GET / POST | `/api/marketing/modelos-mensagem` | `marketing.template.ver` / `.criar` | antes `/templates`; lista paginada |
| PUT / DELETE | `/api/marketing/modelos-mensagem/{id}` | `marketing.template.editar` / `.excluir` | DELETE é soft delete |
| GET / PUT | `/api/marketing/ciclo-vida` | `marketing.automacao.ver` / `.editar` | antes `/lifecycle-settings` |
| GET / PUT | `/api/marketing/formularios` | `marketing.formulario.ver` / `.editar` | antes `/form-definitions` **[Pendente · Grupo 7]** |
| GET | `/api/marketing/health` | nenhuma | `200` com o serviço de pé e conectado ao banco (§8.5) |

### Busca global [Novo]

Todo módulo com registro que o usuário procura pelo nome expõe `GET /api/{modulo}/busca?q=` (§8.6): `q` com pelo menos 2 caracteres, no máximo 5 itens, sem paginação. Leads anonimizados não aparecem.

```
GET /api/marketing/busca?q=maria

→ 200 { "success": true,
        "data": [
          { "id": "9f1c4e2a-...",
            "titulo": "Maria Souza",
            "subtitulo": "Etapa 2 · landing_page",
            "rota": "/leads/9f1c4e2a-..." }
        ],
        "message": null, "errors": [] }
```

`rota` é relativa ao `urlFrontend` do módulo, sem o código: `/leads/…`, e não `/modulos/marketing/leads/…`.

### Regras HTTP de todas as rotas [Regra da infra]

- Envelope `{success, data, message, errors}` em toda resposta, inclusive de erro, com `errors[]` em `{campo, codigo, detalhe}` (§8.2)
- Listagem: `data = {itens, pagina, tamanho, total}`, com no máximo 100 por página (§8.3)
- Códigos da §8.4: `201` com `Location`; `404` para dado de outro tenant, nunca `403`; `409` para duplicidade; `422` para regra de negócio
- Cabeçalho HTTP de até 32 KB aceito (`server.max-http-request-header-size: 32KB`), por causa do token de ADMINISTRADOR (§4.4)
- `X-Request-Id` em todo log e repassado em `correlacaoId` (§3)
- Chamada a outro módulo: timeout de 3 s, e a tela mostra o que conseguiu em vez de responder `500` (§9.6). Endereço vindo de variável de ambiente, nunca fixo no código (§9.1)

### Handoff para o CRM

| Passo | Quem faz |
|---|---|
| Criar ou reaproveitar a empresa (`POST /api/crm/empresas`, tratando o `409`) | Landing, antes de publicar `landing.formulario.recebido` |
| Criar ou reaproveitar o contato (`POST /api/crm/contatos`, tratando o `409`) | Landing, antes de publicar `landing.formulario.recebido` |
| Criar a oportunidade | **Marketing**, ao consumir o evento, com `empresaId` e `contatoId` dele |

> **Regras do repositório de infra para a criação da oportunidade:**
>
> - Rotina sem usuário: token de serviço e `X-Tenant-Id`, direto em `http://crm:8082`, com o host vindo de `CRM_BASE_URL` e timeout de 3 s (§9.1, §9.2, §9.6).
> - A criação informa `origem` e `origemModuloId` para rastreio (§10.3).
> - Um CRM fora do ar não perde o lead: a criação é tentada de novo depois, e o lead fica na etapa 3 sem `crm_oportunidade_id` até dar certo.

> **O que falta do CRM [Pendente · Grupo 6]**
>
> - O contrato do CRM ainda não tem `/api/crm/oportunidades`; o PR #13 diz que entra "em versões seguintes". O formato abaixo é proposta nossa, e o CRM decide o final.
> - A permissão `crm.oportunidade.criar` precisa de `servicos: [marketing]`, para o token de serviço do Marketing poder criar.
> - A resposta precisa devolver o `id` da oportunidade, que gravamos em `leads.crm_oportunidade_id`.

**Proposta de payload para `POST /api/crm/oportunidades`:**

```json
{
  "empresaId": "5c2d9e1a-...",
  "contatoId": "7b1e4c3a-...",
  "origem": "marketing",
  "origemModuloId": "9f1c4e2a-...",
  "canalOrigem": "landing_page",
  "utm": { "source": "...", "medium": "...", "campaign": "...", "term": "...", "content": "..." },
  "respostasFormulario": [ { "label": "...", "valor": "..." } ],
  "dataCaptura": "2026-09-24T13:04:40Z"
}
```

`origemModuloId` é o `leads.id`. `respostasFormulario` é a cópia em `dados_formulario`, tirada da API do Landing. Dados que não existem no momento da captação (vendedor, valor, produto) o CRM preenche depois, do lado dele.

---

## 7. Permissões e menu

As listas entram por PR no `infra-integrador-2026` e o CI as valida contra os schemas JSON. Uma permissão que não está na lista nunca aparece no token.

### `permissoes/marketing.yaml` [Novo]

Formato `modulo.recurso.acao` (§5.1). Os códigos de recurso foram mantidos como estavam neste documento. As permissões de leitura vão para ADMINISTRADOR, GESTOR e MARKETING; as de edição, para ADMINISTRADOR e MARKETING.

```yaml
# Permissões do módulo Marketing e Automações (Grupo 4). Formato: modulo.recurso.acao (Contrato §5.1).
modulo: marketing
permissoes:
  - codigo: marketing.acessar
    descricao: Ver o módulo Marketing no menu
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]

  - codigo: marketing.dashboard.ver
    descricao: Ver o painel do funil de leads, com atualização em tempo real
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]

  - codigo: marketing.lead.ver
    descricao: Ver a lista de leads, o detalhe de cada um e buscá-los pelo nome
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]

  - codigo: marketing.configuracao.ver
    descricao: Ver as preferências dos canais de anúncio
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.configuracao.editar
    descricao: Editar as preferências dos canais de anúncio e as credenciais do webhook do Meta
    perfisPadrao: [ADMINISTRADOR, MARKETING]

  - codigo: marketing.template.ver
    descricao: Ver os modelos de mensagem de aquecimento
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.template.criar
    descricao: Criar modelo de mensagem de aquecimento
    perfisPadrao: [ADMINISTRADOR, MARKETING]
  - codigo: marketing.template.editar
    descricao: Editar, ativar e desativar modelo de mensagem de aquecimento
    perfisPadrao: [ADMINISTRADOR, MARKETING]
  - codigo: marketing.template.excluir
    descricao: Excluir modelo de mensagem de aquecimento
    perfisPadrao: [ADMINISTRADOR, MARKETING]

  - codigo: marketing.automacao.ver
    descricao: Ver o ciclo de vida do lead (tentativas e prazos de retenção)
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.automacao.editar
    descricao: Editar o ciclo de vida do lead (tentativas e prazos de retenção LGPD)
    perfisPadrao: [ADMINISTRADOR, MARKETING]

  # Pendente de alinhamento com o Grupo 7
  - codigo: marketing.formulario.ver
    descricao: Ver os campos do formulário de captação
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.formulario.editar
    descricao: Editar e publicar os campos do formulário de captação
    perfisPadrao: [ADMINISTRADOR, MARKETING]
```

Nenhuma permissão do marketing declara `servicos` por enquanto: nenhum outro módulo chama o marketing com token de serviço.

### Permissões de outros módulos que usamos

| Permissão | Para quê | Situação |
|---|---|---|
| `identity.tenant.ver` | resolver o tenant do webhook do Meta | já concedida (`servicos: [..., marketing, ...]`) |
| `crm.empresa.ver_resumo` | mostrar a razão social de um lead que já passou pelo handoff | já no perfil MARKETING |
| `crm.contato.ver_resumo` | mostrar o resumo do contato vinculado | no PR #13 do CRM, ainda aberto |
| `crm.oportunidade.criar` | criar a oportunidade na etapa 3, com token de serviço | pedir `servicos: [marketing]` ao CRM **[Pendente · Grupo 6]** |
| `landing.envio.ver` | consultar as respostas do formulário com token de serviço | pedido em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md) **[Pendente · Grupo 7]** |

### `modulos/marketing.json` [Novo]

Registro no menu da casca (§11). A faixa de `ordemMenu` do Marketing é 60–69. Ícone e rotas são proposta.

```json
{
  "codigo": "marketing",
  "nome": "Marketing",
  "grupo": "Grupo 4 — Marketing e Automações",
  "icone": "megaphone",
  "urlFrontend": "/modulos/marketing/",
  "prefixoApi": "/api/marketing",
  "permissaoMenu": "marketing.acessar",
  "ordemMenu": 60,
  "healthcheck": "/api/marketing/health",
  "itensSubmenu": [
    { "rota": "/painel",        "nome": "Painel",        "permissao": "marketing.dashboard.ver" },
    { "rota": "/leads",         "nome": "Leads",         "permissao": "marketing.lead.ver" },
    { "rota": "/configuracoes", "nome": "Configurações", "permissao": "marketing.configuracao.ver" },
    { "rota": "/formulario",    "nome": "Formulário",    "permissao": "marketing.formulario.ver" }
  ]
}
```

Esconder item de menu é usabilidade, não segurança: o back-end continua respondendo `403`.

---

## 8. Mensageria

Para **avisar** um fato, o módulo publica evento no RabbitMQ. Para **perguntar** ou pedir algo com resposta imediata, usa API (§9.7).

### Regras de publicação [Regra da infra]

- Publica só na exchange `marketing.eventos` (topic), com a chave de roteamento igual ao tipo do evento, e em `identity.entrada` para pedidos à plataforma
- O envelope é sempre o mesmo: `id` (uuid, chave de idempotência), `tipo` (`modulo.entidade.acao`, verbo no particípio), `versao`, `tenantId`, `moduloOrigem: "marketing"`, `ocorridoEm` em UTC, `usuarioId` (nulo em rotina), `correlacaoId` e `dados`
- Propriedade AMQP **`user_id = mq_marketing`** em toda mensagem. Sem ela, a plataforma manda o pedido para a `.dlq` (novidade da v0.7)
- Publica só depois do commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`)
- Sem senha, token, segredo ou dado pessoal além do necessário: mensagem fica em fila, em log e na `.dlq`
- Todo evento é descrito em `contratos/marketing.asyncapi.yaml` *antes* de ser publicado. Mudança incompatível sobe `versao`

### Eventos candidatos [A confirmar com consumidores]

O contrato pede que só se publique o que algum módulo consome. Nenhum consumidor foi identificado ainda.

| Tipo | Quando | Possível interessado |
|---|---|---|
| `marketing.lead.qualificado` | lead chega à etapa 3 | a identificar (o CRM já recebe a oportunidade por API) |
| `marketing.lead.descadastrado` | opt-out confirmado | CRM (consentimento do contato), Landing |

### Pedidos à plataforma (`identity.entrada`)

| Mensagem | Uso no marketing | Condição |
|---|---|---|
| `identity.notificacao.criar` | avisar um usuário quando um lead chega à etapa 3, com a categoria `NOVO_LEAD` e `rota: "/leads/{id}"` | quem recebe está em aberto (seção 11) |
| `identity.timeline.registrar` | registrar o handoff na timeline da empresa | exige `empresaId`, que só existe depois do handoff |
| `identity.email.enviar` | não se aplica ao aquecimento | só e-mail transacional (4.2) |

### Consumo: eventos do Landing

Todos pela fila `marketing.landing-leads`, ligada a `landing.eventos`, com `marketing.landing-leads.dlq` para mensagens que falharam três vezes. Formato completo em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md). Os nomes dos quatro primeiros são proposta nossa; o dono é o Landing. Todos trazem o campo `etapa`, com o valor da coluna abaixo.

| Evento | Etapa | O que traz | Processamento |
|---|---|---|---|
| `landing.visita.registrada` | 0 | `visitanteId`, página, `origem` (UTM) | buffer |
| `landing.formulario.aberto` | 0 | `visitanteId`, `formularioId`, `origem` | buffer |
| `landing.contato.informado` | 1 | + `envioId`, `formularioVersao`, contato, consentimento | buffer |
| `landing.formulario.atualizado` | 2 | contato atualizado; publicado só quando o Landing considera o preenchimento "grande parte" | na chegada |
| `landing.formulario.recebido` | 3 | já no PR #16; pedimos acrescentar `visitanteId`, `formularioVersao`, contato, consentimento e `origem` | na chegada, com handoff |

Todo consumo é idempotente: grava o `id` em `eventos_processados` na mesma transação do efeito e ignora um `id` já visto. O evento `crm.oportunidade.criada`, citado no §12.7, não é necessário: quem cria a oportunidade é o próprio Marketing.

---

## 9. Front-end e telas

Escopo desta fase: painel, leads, configurações e definição do formulário, rodando dentro da casca via `<iframe>`.

> **Decisão de escopo:** a "camada intermediária" de configurações é **config pura** nesta fase. O cliente registra parâmetros e preferências por canal, sem integração real via OAuth ou API com Google Ads ou Meta Business. A única integração real é receber leads pelo webhook do Meta. A integração completa fica para a Fase 6 (Integrações Avançadas) do Prompt Mestre.

### Requisitos do iframe e da casca [Regra da infra]

- Funciona aberto direto, com token real de um usuário de teste (`marketing@empresa-a.dev`), e embutido, com o token recebido por `postMessage` (§12)
- Servido sob `/modulos/marketing/`: `base: '/modulos/marketing/'` no Vite e o mesmo prefixo no roteador. A API é chamada pelo caminho relativo `/api/marketing/`
- **Registra o ouvinte de `message` antes de enviar `modulo:pronto`** e guarda a sessão fora dos componentes. É a correção do exemplo de 22/09 (§12.2, novidade da v0.7). Recomendação: copiar `exemplo-modulo/front/src/plataforma/sessao.ts`
- Confere `event.origin` (a própria origem) *e* `event.source === window.parent` em toda mensagem
- Envia `modulo:altura` a cada mudança de conteúdo, `modulo:navegar` com rota relativa (`/leads/9f1c`) e `modulo:token-expirado` ao receber `401`
- Token só em memória, nunca em `localStorage`
- O servidor do front envia `Content-Security-Policy: frame-ancestors 'self'` e nunca `X-Frame-Options: DENY` (§12.8)
- Tema: Design System Centinela sobre Tailwind v4, com `lucide-react` e a fonte Inter (`ui/plataforma.css`). Ele substitui o shadcn/ui. Usar os tokens (`bg-superficie`, `text-texto`...) em vez de hex e aplicar `data-tema` recebido da casca

### 9.1 Painel

**Fonte de dados inicial:** `GET /api/marketing/painel?periodo=&origem=&campanha=`, um único endpoint com tudo o que a primeira renderização precisa.

```json
{
  "success": true,
  "data": {
    "funil": { "etapa0": 120, "etapa1": 40, "etapa2": 15, "etapa3": 6 },
    "serieTemporal": [
      { "data": "2026-09-01", "novosLeads": 8, "origem": "meta_ads" },
      { "data": "2026-09-01", "novosLeads": 3, "origem": "landing_page" }
    ],
    "taxaConversao": {
      "etapa0ParaEtapa1": 33.3333,
      "etapa1ParaEtapa2": 37.5,
      "etapa2ParaEtapa3": 40.0
    },
    "leadsRecentes": [
      { "id": "...", "nome": "...", "etapa": 2, "origem": "landing_page", "criadoEm": "..." }
    ]
  },
  "message": null,
  "errors": []
}
```

> **Percentual em pontos [Regra da infra]:** `33.3333` significa 33,33%, com até 4 casas. A versão anterior usava fração (`0.33`), que o §8.7 proíbe.

Filtros: `periodo` (7/30/90 dias ou intervalo customizado), `origem` (`meta_ads`, `landing_page` ou todas) e `campanha` (opcional, via `utm_campaign`).

**Atualização em tempo real via SSE** (`GET /api/marketing/painel/eventos`). O servidor envia eventos pontuais, não o resumo inteiro recalculado:

```
id: 10482
event: lead_criado
data: {"leadId": "...", "etapa": 0, "origem": "meta_ads"}

id: 10483
event: lead_etapa_alterada
data: {"leadId": "...", "etapaAnterior": 1, "etapaNova": 2}
```

O front busca o resumo completo uma vez e depois só reage aos eventos, incrementando contadores e atualizando a lista de recentes localmente.

> **Reconexão e autenticação do SSE.**
>
> - O `id` de cada mensagem é `lead_eventos.sequencia`. Ao reconectar, o cliente envia `Last-Event-ID` e o back-end reenvia só o que foi perdido, filtrado pelo tenant do token.
> - `EventSource` não aceita o cabeçalho `Authorization`, e o token não pode ir na URL (§8.2). Por isso a conexão usa `fetch` + `ReadableStream`.
> - O token dura 15 min: ao receber `plataforma:token`, o front reabre o stream com o token novo e o `Last-Event-ID`.
> - Ainda precisamos confirmar com o Grupo 2 se o gateway, que devolve `504` quando o módulo não responde em 3 s, mantém uma conexão longa aberta (seção 11).

**Cache das agregações:** com o volume esperado (uso interno, escala acadêmica), cache local em memória com TTL curto (ex.: Caffeine) basta, sempre com chave por tenant. Não é preciso Redis nesta fase.

**Elementos de tela:**

- Funil por etapa (contadores 0/1/2/3 ou frio/médio/quente)
- Série temporal de novos leads, filtrável por origem
- Taxa de conversão entre etapas consecutivas
- Lista de leads recentes, atualizada via SSE

### 9.2 Configurações

Uma tela com três abas de naturezas diferentes: preferência de referência, conteúdo usado pelo sistema e regras de LGPD.

**Aba "Canais de anúncio"**: `GET/PUT /api/marketing/canais-anuncio`

- Um card por canal (`google_ads`, `meta_ads`)
- Orçamento mensal pretendido, em reais, com 2 casas; público-alvo e objetivo da campanha em campos livres
- Sem chamada externa: é registro de intenção, lido manualmente por quem configura o Ads Manager de fato
- No card do Meta: App Secret e verify token do webhook, só de escrita (a tela mostra "configurado" ou "não configurado", nunca o valor), e a URL do webhook deste tenant, pronta para copiar

**Aba "Modelos de mensagem"**: `GET/POST/PUT/DELETE /api/marketing/modelos-mensagem`

- Lista de modelos de e-mail, indicando a etapa fria (0/1) de cada um e sua posição na cadência
- Editor com assunto e corpo, aceitando variáveis como `{{nome}}` e `{{origem}}`
- Pré-visualização do modelo com dados de exemplo antes de salvar, incluindo o rodapé com o link de descadastro
- Chave de ativo/inativo: modelos inativos não entram no job de comunicação

Cada envio grava `lead_comunicacoes.modelo_id`, o que permite medir abertura e clique por modelo quando houver rastreio (seção 11).

**Aba "Ciclo de vida do lead"**: `GET/PUT /api/marketing/ciclo-vida`

- Máximo de tentativas de aquecimento antes de marcar o lead como esgotado
- Meses de retenção após esgotamento, antes da anonimização automática
- Meses de retenção após opt-out, antes da anonimização automática
- Cada tenant define os próprios valores, sem padrão fixo no código, conforme a seção 83 do Prompt Mestre (LGPD)

Criar e editar em modal com título, confirmação em modal pequeno e vermelho só para ação irreversível (regras de uso do tema).

### 9.3 Definição do formulário [Pendente · Grupo 7]

> **[Pendente · Grupo 7]** Esta seção foi mantida como estava e aguarda o alinhamento com o Grupo 7. O editor de formulários (seção 56) é do Landing pelo Mapa de Fronteiras, e com a divisão de 24/09 o Landing já envia `formularioId`, `formularioVersao` e as respostas pela API dele. Pela regra do dono único, falta decidir se esta tela e a tabela `formularios` continuam no Marketing ou se usamos só as referências do Landing.

> **Divisão de responsabilidade combinada anteriormente:** o editor visual rico (arrastar e soltar, layout, estilo) é do Grupo 7. O Grupo 4 não reconstrói esse editor. Nossa tela é uma seleção simples de campos, e `formularios` é o contrato de dados entre os dois módulos.

**Lista de campos:** `GET /api/marketing/formularios` retorna a versão ativa

- Tabela simples: rótulo, tipo, obrigatório, ordem
- Ações: adicionar campo, editar, remover e reordenar (lista simples, não layout)
- Tipos de campo iguais aos do Prompt Mestre (seção 56): texto, e-mail, telefone, WhatsApp, CNPJ, CPF, lista, checkbox, radio, textarea, data

**Botão "Publicar alterações":** `PUT /api/marketing/formularios`

- Ao salvar, incrementa `versao`, marca a nova como `ativo = true` e desativa a anterior
- Combina com `leads.formulario_versao`: dá para saber a qual versão do formulário cada lead respondeu

**Fora do nosso escopo** (é do Grupo 7): layout visual da landing page (posição, estilo, cores) e o editor de arrastar e soltar. Nosso lado só consome e produz o JSON de `campos`.

**Schema de cada item de `campos`, a formalizar com o Grupo 7:**

```json
{
  "id": "string (uuid)",
  "label": "string",
  "tipo": "texto | email | telefone | whatsapp | cnpj | cpf | lista | checkbox | radio | textarea | data",
  "obrigatorio": "boolean",
  "ordem": "integer",
  "opcoes": "string[] (obrigatório só quando tipo = lista | radio | checkbox)"
}
```

Vale formalizar isso como um JSON Schema curto compartilhado com o Grupo 7, e não só como descrição em prosa, porque é literalmente a interface entre os dois módulos.

---

## 10. Testes e conformidade

O módulo está integrado quando passa nos 19 itens do checklist (§15), verificados com o Grupo 2 numa sessão de 20 minutos. A lista vai na descrição do PR que registra o módulo em `modulos/`.

| Itens | Como o marketing cumpre |
|---|---|
| 1, 2, 14, 16 | sobe no compose na porta 8087; `/api/marketing/health` sem token; imagem publicada pelo workflow reutilizável `publicar-imagem.yml`; Flyway com `own_marketing`, app com `usr_marketing` |
| 3, 4, 19 | teste de 401 sem token, 403 com token sem permissão (ex.: `vendedor@empresa-a.dev`) e cabeçalho de 32 KB com token de ADMINISTRADOR |
| 5, 6 | lead criado na Empresa A e buscado com token da Empresa B responde 404; `tenantId` no corpo é ignorado. Vale também para o webhook: slug de um tenant com assinatura de outro é recusado |
| 7, 8 | sete colunas em todas as tabelas de negócio (seção 3); nenhuma FK nem query em outro schema |
| 9, 10, 13 | envelope em tudo, inclusive nas rotas públicas; listagens paginadas e ordenáveis; `contratos/marketing.yaml` refletindo a API |
| 11, 12 | front sozinho e no iframe sob `/modulos/marketing/`, com altura ajustando; `modulos/marketing.json` entregue |
| 15 | suíte com Testcontainers (PostgreSQL e RabbitMQ) e WireMock para CRM, Landing e identity; os eventos do Landing são publicados pelo próprio teste no RabbitMQ do Testcontainers; nenhum outro módulo no ar |
| 17, 18 | eventos com envelope e `user_id = mq_marketing`, descritos no AsyncAPI; consumidor testado com a mesma mensagem entregue duas vezes |

O `exemplo-modulo` já cobre os itens 2 a 6, 9, 10, 15 e 18 com testes automatizados. Copiá-los e adaptar as rotas é o caminho mais curto. Para desenvolver contra o CRM antes de ele existir, use um stub do Prism a partir de `contratos/crm.yaml` (sem tirar o `-m false`). O identity nunca é stub: use a imagem real e os usuários de `docs/usuarios-de-teste.md`.

### Entrega no `infra-integrador-2026`

- `contratos/marketing.yaml` (OpenAPI 3.1): endpoints que a casca e outros módulos usam, antes de implementar (§14.1)
- `contratos/marketing.asyncapi.yaml` (AsyncAPI 3.0): quando houver evento com consumidor
- `permissoes/marketing.yaml` e `modulos/marketing.json` (seção 7)
- `IMAGEM_MARKETING` e `IMAGEM_MARKETING_FRONT` com a conta do grupo, em `.env.example`
- `contratos/marketing.views.md`: só se o Grupo 3 pedir *views* `vw_pub_*` para relatórios

---

## 11. Pendências e decisões em aberto

| Pendência | Com quem | Situação |
|---|---|---|
| Aceite da proposta ao Landing ([`contratos/landing-requisitos.md`](contratos/landing-requisitos.md)): cinco eventos, `visitanteId` e `envioId`, UTM, consentimento, `GET /api/landing/envios/{envioId}` e `servicos: [marketing]` em `landing.envio.ver` | Grupo 7 | **[Rascunho a enviar]** |
| Dono da definição do formulário: manter a tabela `formularios` e a tela 9.3 ou usar só as referências do Landing | Grupo 7 | A definir |
| Critério da etapa 2 ("grande parte do formulário"), definido e aplicado pelo Landing; precisamos conhecê-lo para documentar no painel | Grupo 7 | A combinar |
| Consentimento de marketing dos leads do Meta Ads (etapa 0) antes do aquecimento | Grupo 4 | A definir |
| Fronteira entre `leads` (dados de quem ainda não é contato) e Contato do CRM (§10, regra do dono único) | Grupo 6 | A confirmar |
| `POST /api/crm/oportunidades` no contrato do CRM, com o formato da seção 6, e `servicos: [marketing]` em `crm.oportunidade.criar` | Grupo 6 | A pedir |
| Construtor visual de automação (60), gatilhos e ações (61–62) e campanhas de e-mail (63), atribuídos ao Grupo 4 pelo Mapa e não cobertos aqui | Grupo 4, professores | Lacuna de escopo a definir |
| WhatsApp e telefonia (65–67), sem dono no Mapa | — | Fora desta fase; evolução futura |
| SMTP para o e-mail de aquecimento: o compose não entrega variáveis de SMTP ao `marketing`, e o provedor fora do desenvolvimento depende dos professores (P6) | Grupo 2, professores | A pedir |
| SSE pelo gateway: a conexão longa sobrevive à regra de `504` em 3 s? | Grupo 2 | A confirmar |
| Rate limit de 120 req/min por IP em `/public/**` pode barrar rajadas do webhook do Meta | Grupo 2 | A confirmar |
| Handshake do Meta exige devolver `hub.challenge` em texto puro, fora do envelope; o §8.2 só prevê exceção para download de arquivo | Grupo 2 | Pedir exceção registrada |
| Onde guardar App Secret e verify token do Meta por tenant (proposta: colunas cifradas em `canais_anuncio`) | Grupo 4 | Proposta a confirmar |
| Quem recebe a notificação `NOVO_LEAD` na etapa 3 | Grupo 4, Grupo 6 | A definir |
| Eventos candidatos (`marketing.lead.qualificado`, `marketing.lead.descadastrado`) | Consumidores | A confirmar antes do AsyncAPI |
| Rastreio de aberto e clicado em `lead_comunicacoes` (pixel e redirecionamento públicos, §12.5) | Grupo 4 | A definir |
| Retenção do `payload_bruto` do buffer já processado, que contém dado pessoal | Grupo 4 | A definir |
| Auditoria em `audit_logs` no próprio schema (proposta do Mapa §5, ainda não decidida) | Grupo 2 | Acompanhar |
| Ratificação das v0.6 e v0.7 do Contrato: o Marketing ainda não se manifestou; objeções no PR #5 até 24/09 | Gestores | Prazo em 24/09/2026 |
| Decisão D4 (CRM dono de Empresas e Contatos): falta a manifestação do Marketing | Gestores | A manifestar |

---

## 12. Registro de alterações

### 24/09/2026 · divisão com o Landing (Grupo 7)

- Etapas redefinidas: etapa 0 inclui o clique em "fale conosco" sem preenchimento; etapa 1 passa a ser o contato preenchido (nome, telefone e e-mail)
- Leads da landing chegam por cinco eventos do Landing na fila `marketing.landing-leads`; a rota pública de intake foi removida
- O Landing decide a etapa e a envia no campo `etapa` de cada evento, inclusive o critério da etapa 2; o Marketing só impede que a etapa volte para trás
- Etapa 3: o Landing cria empresa e contato no CRM, e o Marketing cria a oportunidade
- Novas colunas em `leads`: `visitante_id`, `envio_id`, `formulario_id`, `consentimento_marketing`, `consentimento_em`, `crm_empresa_id` e `crm_contato_id`. Em `leads_entrada_buffer`: `etapa`, `tipo_origem` e `visitante_id`
- O job de aquecimento só envia para quem tem consentimento de marketing
- Pendências atualizadas; o que pedimos ao Landing foi para [`contratos/`](contratos/)

### 24/09/2026 · alinhamento ao `infra-integrador-2026` e ao Contrato v0.7

- Nova seção de enquadramento: código, portas, credenciais, escopo do Mapa de Fronteiras e as duas superfícies
- Tabelas renomeadas para português e todas com as sete colunas obrigatórias. Entraram `eventos_processados`, `lead_eventos.sequencia`, `leads.opt_out_em`, `leads.token_descadastro` e `canais_anuncio.orcamento_mensal` em `numeric(15,2)`. `meta_lead_id` passou a ser único por tenant
- Rotas renomeadas para português; rotas públicas movidas para `/public/marketing/**`; webhook do Meta com o tenant resolvido pelo subdomínio no caminho e validado por HMAC por tenant
- Novas rotas: busca global (`/busca`) e descadastro público
- Taxa de conversão do painel passou a ser em pontos percentuais
- Aquecimento só por e-mail nesta fase; WhatsApp fica como evolução futura
- Novas seções: permissões e menu, mensageria, testes e conformidade, pendências
- Intake, handoff e formulário marcados como pendentes do alinhamento com o Grupo 7, sem mudança de desenho
