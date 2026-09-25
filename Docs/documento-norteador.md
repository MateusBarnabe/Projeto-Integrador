# Documento Norteador — Módulo de Marketing (Grupo 4)

Código `marketing` · Schema `marketing` · Projeto Integrador 2026 · Grupo 4 · Revisão de 25/09/2026

*Documento norteador — incrementado conforme o projeto avança · Alinhado ao Contrato de Integração v0.7*

> **Base desta revisão:** repositório `infra-integrador-2026` do Grupo 2. Foram usados o Contrato de Integração **v0.7** (PR #5, ainda não mergeado; objeções até 24/09), o Mapa de Fronteiras v0.2, o checklist de conformidade, os contratos e permissões já publicados e o módulo de exemplo. Onde este documento diz *§*, a referência é a seção do Contrato de Integração.
>
> O que outros grupos precisam saber de nós está separado em [`contratos/`](contratos/); este documento continua sendo a fonte da verdade do Grupo 4.
>
> Marcadores usados:
> - **[Etapa 2]** faz parte do módulo de marketing integrado na plataforma (seção 0).
> - **[Pendente · Grupo 7]** ou **[Pendente · Grupo 6]** depende de alinhamento com esse grupo.
> - **[Novo]** foi acrescentado para cumprir o contrato.
> - **[Regra da infra]** é obrigatório e vem de fora do grupo.

## Índice

0. [As duas etapas do projeto](#0-as-duas-etapas-do-projeto)
1. [Enquadramento na plataforma](#1-enquadramento-na-plataforma)
2. [Funil de leads](#2-funil-de-leads)
3. [Tabelas](#3-tabelas)
4. [Jobs em background](#4-jobs-em-background)
5. [Fluxo de escrita](#5-fluxo-de-escrita)
6. [Endpoints](#6-endpoints)
7. [Permissões e menu](#7-permissões-e-menu)
8. [Mensageria](#8-mensageria)
9. [Etapa 2 — Módulo integrado de marketing](#9-etapa-2--módulo-integrado-de-marketing)
   - [9.1 Painel](#91-painel)
   - [9.2 Leads](#92-leads)
   - [9.3 Qualificação e distribuição](#93-qualificação-e-distribuição)
   - [9.4 Configurações de marketing](#94-configurações-de-marketing)
   - [9.5 Campos do formulário](#95-campos-do-formulário)
   - [9.6 Disponibilização para outros módulos](#96-disponibilização-para-outros-módulos)
10. [Testes e conformidade](#10-testes-e-conformidade)
11. [Pendências e decisões em aberto](#11-pendências-e-decisões-em-aberto)
12. [Registro de alterações](#12-registro-de-alterações)

---

## 0. As duas etapas do projeto

O Marketing é entregue em duas etapas. A **Etapa 1** faz o lead chegar e ser registrado. A **Etapa 2** é o **módulo de marketing integrado na plataforma**: a área em que a pessoa gestora de marketing acompanha os resultados, configura o marketing e decide o que o formulário da landing page pergunta. É também de onde as informações de lead saem para os módulos que precisam delas.

> **Etapa do projeto não é etapa do funil.** "Etapa 1" e "Etapa 2", com maiúscula, são as fases do projeto. As etapas 0 a 3 do lead são as etapas do funil (seção 2).

| | Etapa 1 · Captação | Etapa 2 · Módulo integrado de marketing |
|---|---|---|
| O que entrega | o lead chega pelo webhook do Meta e pelos eventos do Landing, é deduplicado, tem o consentimento registrado, avança no funil, recebe a confirmação de boas-vindas e o aquecimento por e-mail e, na etapa 3 do funil, vira oportunidade no CRM | painel, lista e busca de leads, qualificação e distribuição para vendas, configurações de marketing (Meta Ads e outros canais), campos do formulário e disponibilização dos dados |
| Quem usa | ninguém vê diretamente: roda no back-end | a pessoa gestora de marketing, dentro da casca; e os módulos interessados, pela API e pelos eventos |
| Onde está | seções 2 a 5, 8 e a superfície pública da seção 6 | seção 9, e as tabelas, rotas e permissões marcadas **[Etapa 2]** nas seções 3, 6 e 7 |

A landing page e o formulário que o visitante preenche fazem parte da jornada da Etapa 1, mas são do Landing (Grupo 7): ficam na página pública, aberta a qualquer pessoa, e não na plataforma compartilhada. A Etapa 2 é a área autenticada, dentro da casca, onde a pessoa gestora define os campos que esse formulário pergunta.

**O que a Etapa 2 oferece:**

1. **Painel** (9.1): funil, conversão, qualificação, campanhas, custo por lead e distribuição entre vendedores, com atualização em tempo real.
2. **Leads** (9.2): lista, detalhe e busca global. A consulta de leads é do Marketing. O CRM só passa a ter o lead quando ele vira empresa, contato e oportunidade.
3. **Qualificação e distribuição** (9.3): pontuação (lead scoring), faixa MQL e rodízio entre vendedores, conforme as [Regras de Negócio](Regras%20de%20Negocio.md).
4. **Configurações de marketing** (9.4): canais de anúncio, com o Meta Ads, modelos de mensagem, ciclo de vida e regras de qualificação.
5. **Campos do formulário** (9.5): a pessoa gestora escolhe os campos que o formulário da landing page pergunta. O Landing só monta a página.
6. **Disponibilização** (9.6): API e eventos para os interessados consumirem, e o fluxo expresso que manda o lead quente direto ao CRM.

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
| 58–59 · Tracking e UTM | com Grupo 7 | UTM capturada pelo Landing, recebida nos eventos (seção 8), guardada em `leads` e mostrada por campanha no painel (9.1) |
| 60 · Construtor visual de automação | definido | Não coberto. Lacuna registrada na seção 11 |
| 61–62 · Gatilhos e ações | compartilhado | Cadência de aquecimento (4.2), qualificação e rodízio (9.3). Lacuna registrada na seção 11 |
| 63 · Campanhas de e-mail | definido | Só e-mail de aquecimento. Lacuna registrada na seção 11 |
| 64 · Opt-out | definido | Descadastro público e anonimização (4.3, 6) |
| 65–67 · WhatsApp, e-mail, telefonia | sem dono | Só e-mail. WhatsApp fica como evolução futura |

### Duas superfícies (§12.4–12.5)

| | Autenticada (casca, iframe) | Pública (sem login) |
|---|---|---|
| O que é do marketing | o módulo da Etapa 2: painel, leads, qualificação e distribuição, configurações, modelos de mensagem, ciclo de vida e campos do formulário | webhook do Meta Ads e página de descadastro. O visitante da landing page é atendido pelo Landing, não pelo Marketing |
| Tenant | claim `tenant_id` do token | subdomínio ou slug, nunca parâmetro alterável |
| Devolve dado de negócio | sim, conforme permissão | nunca; só recebe informação |

> **Divisão com o Grupo 7 (Landing Pages), definida em 24/09.**
>
> 1. O **Landing** atende o visitante e avisa cada fato por **evento no RabbitMQ**, na exchange `landing.eventos`: visita por anúncio, clique em "fale conosco", contato preenchido, formulário em andamento e formulário enviado.
> 2. Cada evento traz a **etapa** (0 a 3), **decidida pelo Landing**, inclusive o critério de "grande parte do formulário" da etapa 2. O Marketing grava a etapa recebida e só impede que ela volte para trás.
> 3. O evento traz contato, consentimento e UTM. As respostas do formulário o Marketing consulta na API do Landing, pelo `envioId`.
> 4. Na etapa 3, o Landing cria ou reaproveita **empresa e contato** no CRM, como no PR #16. **A oportunidade é o Marketing que cria.**
> 5. **Proposta do Grupo 4, a enviar:** os **campos do formulário** são definidos no Marketing (9.5). O Landing lê a definição pela nossa API, monta a página e guarda as respostas.
>
> O que pedimos ao Landing está em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md), com os eventos em AsyncAPI. A proposta ainda precisa do aceite deles.

---

## 2. Funil de leads

| Etapa | Descrição | Temperatura | Persistência | Fonte |
|---|---|---|---|---|
| 0 | Veio de anúncio e não interagiu: lead do Meta Ads (pré-preenchido, passivo) ou visitante da landing com UTM de campanha. Ou clicou em "fale conosco" e não preencheu nada | Frio | Batch (via buffer) | webhook do Meta; `landing.visita.registrada`; `landing.formulario.aberto` |
| 1 | Clicou em "fale conosco" e preencheu os dados de contato: nome, telefone e e-mail | Frio | Batch (via buffer) | `landing.contato.informado` |
| 2 | Preencheu grande parte do formulário, não enviou | Médio | Tempo real | `landing.formulario.atualizado` |
| 3 | Enviou o formulário completo; falta assinar contrato | Quente | Tempo real + fluxo expresso para o CRM | `landing.formulario.recebido` |

Nos leads da landing, **a etapa vem no evento, decidida pelo Landing** (campo `etapa`). O Marketing não recalcula: grava a etapa recebida e ignora uma etapa menor do que a que o lead já tem. O critério da etapa 2 ("grande parte do formulário") é do Landing, que só publica `landing.formulario.atualizado` quando ele é atingido. Precisamos conhecer esse critério (seção 11). Nos leads do Meta, a etapa é sempre 0.

**Etapa do funil e qualificação medem coisas diferentes.** A etapa, e a temperatura que sai dela, mede quanto o lead avançou no formulário. A qualificação (9.3) mede o perfil, pela pontuação das respostas e da origem. Um lead pode estar na etapa 3 e cair na faixa de nutrição, ou estar na etapa 2 e já ser MQL quente. O painel mostra as duas.

Fluxo de saída: `LEAD → CRM (oportunidade) → PROPOSTA → CONTRATO`. Na etapa 3, o Landing cria empresa e contato no CRM e o Marketing cria a oportunidade pelo fluxo expresso (9.6). Propostas estão sem dono no Mapa de Fronteiras (seções 18–19), e o PR #13 do CRM declara que não são dele.

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
| formulario_id **[Novo]** | uuid, nullable | `formularioId` do evento, que é o `id` de `formularios` (9.5). Sem FK, para um formulário desconhecido não travar a gravação do lead |
| formulario_versao | int, nullable | `formularioVersao` do evento, que é a `versao` de `formulario_versoes`. Antes `form_definition_version` |
| dados_formulario | jsonb | cópia das respostas consultadas em `GET /api/landing/envios/{envioId}` nas etapas 2 e 3, como registro histórico do fato (§9.5) |
| consentimento_marketing **[Novo]** | boolean, default false | o lead aceitou receber comunicação de marketing (LGPD). Sem ele, o job 4.2 não envia. É o estado atual; a evidência de cada aceite e revogação fica em `lead_consentimentos` |
| consentimento_em **[Novo]** | timestamptz, nullable | quando o consentimento atual foi registrado no Landing |
| boas_vindas_enviada_em **[Novo]** | timestamptz, nullable | quando o e-mail de boas-vindas saiu (4.5). Nulo enquanto não saiu; garante um envio só por lead |
| meta_lead_id | varchar, nullable | `leadgen_id` do Meta, para dedupe de reentrega do webhook. **UNIQUE (tenant_id, meta_lead_id)**: a unicidade é por tenant |
| crm_empresa_id / crm_contato_id **[Novo]** | uuid, nullable | recebidos em `landing.formulario.recebido`. Só o UUID, nunca FK entre schemas |
| crm_oportunidade_id | uuid, nullable | oportunidade criada pelo Marketing na etapa 3. Só o UUID |
| pontuacao **[Etapa 2]** | int, nullable | soma das regras de pontuação (9.3). Nula enquanto nada pontuou |
| faixa_qualificacao **[Etapa 2]** | varchar, nullable | `mql_quente`, `mql_morno`, `nutricao` |
| pontuacao_detalhe **[Etapa 2]** | jsonb | regras que somaram pontos, para o detalhe do lead explicar a nota |
| qualificado_em **[Etapa 2]** | timestamptz, nullable | último cálculo da pontuação |
| vendedor_id **[Etapa 2]** | uuid, nullable | usuário do identity atribuído pelo rodízio (9.3). Só o UUID |
| vendedor_atribuido_em **[Etapa 2]** | timestamptz, nullable | base do transbordo |
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
| tipo_evento | varchar | `etapa_alterada`, `formulario_atualizado`, `comunicacao_enviada`, `consentimento_registrado`, `qualificacao_alterada`, `vendedor_atribuido`, `handoff_crm`, `descadastrado`, `anonimizado` |
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
| visitante_id **[Novo]** | uuid, nullable | `visitanteId` do Landing; liga as linhas de etapa 0 ao lead quando ele chega à etapa 1. Também é a base da contagem de visitantes do painel (9.1) |
| processado / processado_em | boolean default false / timestamptz | o momento do recebimento é o `created_at` (antes `recebido_em`) |
| tentativas | int, default 0 | incrementado a cada falha de processamento |
| falha_permanente | boolean, default false | marcado após N tentativas, para investigação manual |
| colunas obrigatórias **[Novo]** | §7.2 | |

> **Regra de promoção:** uma linha do buffer só vira registro em `leads` se tiver `meta_lead_id`, e-mail ou telefone. Cliques e eventos sem nenhum identificador de contato são marcados como `processado = true` sem gerar lead, para não poluir o funil com registros vazios.

### `lead_consentimentos` [Novo]

Registro de evidência do consentimento, exigido pela [LGPD](LGPD.md) (8.1) e pelo requisito RC03 dos [Requisitos](Requisitos.md): data e hora, IP, *User-Agent*, versão do termo aceito e formulário de origem. É só de inclusão: uma linha por aceite ou revogação, nunca alterada. `leads.consentimento_marketing` guarda só o estado atual.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| lead_id | uuid | FK para `leads.id` |
| finalidade | varchar | `marketing` nesta fase. Coluna mantida para outras finalidades, como termos de uso |
| aceito | boolean | `true` no aceite, `false` na revogação (descadastro ou desmarcação no formulário) |
| registrado_em | timestamptz | `consentimento.registradoEm` do evento, em UTC, ou o momento do descadastro |
| ip | varchar(45), nullable | IPv4 ou IPv6 de quem aceitou ou revogou. Zerado na anonimização (4.3) |
| user_agent | varchar(500), nullable | *User-Agent* do navegador. Zerado na anonimização (4.3) |
| versao_termo | varchar, nullable | versão do texto de consentimento que a pessoa viu, informada pelo Landing. Nula na revogação |
| origem | varchar | `landing.contato.informado`, `landing.formulario.atualizado`, `landing.formulario.recebido` ou `descadastro` |
| formulario_id / formulario_versao | uuid / int, nullable | formulário de origem do aceite. Sem FK, como em `leads` |
| envio_id | uuid, nullable | `envioId` do Landing |
| evento_id | uuid, nullable | `id` do evento que trouxe o aceite, para auditoria. Nulo no descadastro |
| colunas obrigatórias | §7.2 | |

> **Quando grava:** o `consentimento` chega nos eventos das etapas 1, 2 e 3 do funil (seção 8). O consumidor só grava uma linha nova quando `aceito`, `registrado_em` ou `versao_termo` diferem da última linha do lead, para os eventos repetidos da etapa 2 não multiplicarem o registro. Na mesma transação, atualiza `leads.consentimento_marketing` e `leads.consentimento_em`. O descadastro (seção 6) grava a revogação com o IP e o *User-Agent* da requisição.

### `lead_comunicacoes`

Histórico dos e-mails enviados ao lead: boas-vindas (4.5) e aquecimento (4.2). Nesta fase só por e-mail, sem chatbot.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id / lead_id | uuid | `lead_id` é FK para `leads` |
| canal | varchar | `email`. Coluna mantida para a entrada futura de outros canais |
| modelo_id | uuid | FK para `modelos_mensagem.id` (antes `template_id`) |
| status | varchar | `enviado`, `entregue`, `aberto`, `clicado`, `falhou`. Aberto e clicado exigem rastreio (seção 11) |
| enviado_em | timestamptz | |
| colunas obrigatórias | §7.2 | |

### `formularios` e `formulario_versoes` (antes `form_definitions`) [Etapa 2]

Definição dos campos do formulário da landing page, feita pela pessoa gestora no Marketing (9.5). O Landing lê a versão ativa e monta a página. Um tenant pode ter mais de um formulário, por exemplo um por landing page ou campanha.

**`formularios`**

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | é o `formularioId` que o Landing manda nos eventos |
| nome | varchar | identificador amigável, ex.: "Consultoria — Black Friday" |
| versao_ativa | int, nullable | versão que o Landing mostra. Nula enquanto o formulário nunca foi publicado |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

**`formulario_versoes`**

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| formulario_id | uuid | FK para `formularios.id`. **UNIQUE (formulario_id, versao)** |
| versao | int | começa em 1 e sobe a cada publicação |
| campos | jsonb | array no formato da 9.5, sem normalizar enquanto os campos não estão fechados |
| publicado_em | timestamptz | versões publicadas não mudam: cada lead guarda a versão que respondeu |
| colunas obrigatórias | §7.2 | |

### `canais_anuncio` (antes `marketing_settings`)

Preferências de referência por canal de anúncio. Não substitui as ferramentas de Ads: uma pessoa lê e configura manualmente no Ads Manager.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | UNIQUE (tenant_id, canal) |
| canal | varchar | `google_ads`, `meta_ads` |
| orcamento_mensal **[Novo]** | numeric(15,2), nullable | saiu do jsonb: dinheiro tem tipo fixo (§8.7). Mais de 2 casas responde `400 PRECISAO_EXCEDIDA`. Base do custo estimado por lead no painel (9.1) |
| configuracao | jsonb | `{publicoAlvo, objetivo, mapeamentoCampos}`. `mapeamentoCampos` **[Etapa 2]** liga cada pergunta do formulário instantâneo do Meta à `chave` de um campo nosso, para pontuar o lead do Meta (9.3) |
| meta_app_secret / meta_verify_token **[Novo]** | varchar cifrado, nullable | só na linha `meta_ads`. Validam o webhook do tenant (seção 6). A API recebe o valor, mas nunca o devolve. Proposta a confirmar |
| meta_page_id / meta_page_access_token **[Novo]** | varchar / varchar cifrado, nullable | só na linha `meta_ads`. O webhook do Meta traz só o `leadgen_id`; os dados do lead vêm da Graph API com o token da página (4.1). O token nunca é devolvido pela API |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

### `modelos_mensagem` (antes `mensagem_templates`)

Modelos de e-mail: a confirmação de boas-vindas (4.5) e a cadência de aquecimento das etapas frias (0/1, job 4.2). É conteúdo estruturado, diferente das preferências de canal acima.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| tipo **[Novo]** | varchar | `boas_vindas` ou `aquecimento`. Só um modelo `boas_vindas` ativo por tenant |
| nome | varchar | identificador amigável, ex.: "Reengajamento 7 dias" |
| canal | varchar | `email` nesta fase |
| etapa_alvo | smallint, nullable | só em `aquecimento`: 0 ou 1; nulo vale para qualquer etapa fria |
| ordem_sequencia | int, nullable | só em `aquecimento`: posição na cadência (1º toque, 2º toque, ...) |
| dias_apos_anterior | int, nullable | só em `aquecimento`: dias de espera desde o toque anterior |
| assunto | varchar, not null | obrigatório, já que o único canal é e-mail |
| corpo | text | com variáveis como `{{nome}}` e `{{origem}}`, no mesmo padrão de variáveis dinâmicas do Prompt Mestre (seção 18). O link de descadastro entra em todo envio (4.2) |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

Cada tenant começa com um modelo `boas_vindas` padrão, que a pessoa gestora edita (9.4).

### `ciclo_vida_configuracoes` (antes `lifecycle_settings`)

Configuração do ciclo de vida do aquecimento, uma linha por tenant. Cada administrador define os próprios limites.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | `tenant_id` UNIQUE |
| max_tentativas_aquecimento | int | depois desse número de toques sem resposta, o lead vira `esgotado` |
| meses_retencao_pos_esgotamento | int | meses depois de `esgotado_em` até anonimizar |
| meses_retencao_pos_optout | int | meses depois de `opt_out_em` até anonimizar |
| colunas obrigatórias | §7.2 | |

### `regras_pontuacao` [Etapa 2]

Critérios da pontuação do lead (9.3), por tenant. Começam com a matriz das [Regras de Negócio](Regras%20de%20Negocio.md) (7.1), e a pessoa gestora ajusta.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| criterio | varchar | nome amigável: "Cargo", "Orçamento", "Prazo de compra", "Origem do tráfego". Cada critério conta uma vez por lead: se duas regras dele baterem, vale a de mais pontos |
| fonte | varchar | `campo_formulario` ou `utm` |
| campo_chave | varchar, nullable | `chave` do campo (9.5), quando `fonte = campo_formulario`. A mesma chave vale em todos os formulários do tenant |
| valores | jsonb | respostas que disparam a regra, ex.: `["Decisor", "Sócio", "Gerente"]`. Em `utm`: `[{"source": "google", "medium": "cpc"}, {"direto": true}]` |
| pontos | int | pode ser 0 |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

### `qualificacao_configuracoes` [Etapa 2]

Faixas de qualificação e regras do rodízio, uma linha por tenant. Os valores iniciais vêm das Regras de Negócio e cada tenant ajusta.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | `tenant_id` UNIQUE |
| limite_mql_quente | int | valor inicial 70 |
| limite_mql_morno | int | valor inicial 40. Abaixo disso, a faixa é `nutricao` |
| rodizio_ativo | boolean | desligado, os MQL ficam em "aguardando vendedor" até a pessoa gestora atribuir |
| horario_comercial_inicio / horario_comercial_fim | time | valores iniciais 08:00 e 18:00, no fuso do tenant |
| dias_atendimento | smallint[] | dias da semana com atendimento, de 1 (segunda) a 7 (domingo) |
| minutos_transbordo | int, nullable | valor inicial 15. Só vale quando soubermos do primeiro contato (seção 11) |
| colunas obrigatórias | §7.2 | |

### `vendedores_rodizio` [Etapa 2]

Quem participa da escala do rodízio (9.3).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | UNIQUE (tenant_id, vendedor_id) |
| vendedor_id | uuid | usuário do identity com perfil VENDEDOR. Só o UUID |
| ativo | boolean | participa da escala |
| ultima_atribuicao_em | timestamptz, nullable | o rodízio escolhe o ativo com a atribuição mais antiga |
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
- **Leads do Meta [Novo]:** o webhook traz só o `leadgen_id`. Antes de resolver a identidade, o job busca os campos do lead em `GET https://graph.facebook.com/{versão}/{leadgen_id}` com o `meta_page_access_token` do tenant, com timeout de 3 s. Uma falha conta como tentativa
- **Resolução de identidade**, por linha do buffer e sempre dentro do mesmo tenant:
  1. Tem `meta_lead_id`? Procura exatamente esse valor em `leads`. Se existir, é reentrega (idempotência): atualiza, não duplica
  2. Tem `visitante_id`? Procura em `leads.visitante_id`. Se existir, é o mesmo visitante da landing: atualiza
  3. Sem nenhum dos dois, mas com e-mail ou telefone? Normaliza (e-mail em minúsculas e sem espaços; telefone só com dígitos, em E.164) e procura em `leads`. Se achar, faz merge sem sobrescrever dado já preenchido com dado mais pobre. Se não achar, cria um lead novo
  4. Sem `meta_lead_id`, e-mail ou telefone (visitante da landing na etapa 0)? **Não vira lead**; fica só como evento de analytics no buffer, ligado pelo `visitante_id` caso ele chegue depois à etapa 1
- **Pontuação do Meta [Etapa 2]:** depois de gravar um lead do Meta, calcula a pontuação com as respostas do formulário instantâneo, traduzidas por `mapeamentoCampos` (9.3)
- **Regra de não-retrocesso:** se o lead encontrado já está na etapa 2 ou 3, uma linha do buffer com etapa 0/1 não rebaixa a etapa. Só atualiza dados complementares e registra o evento
- **Falha e retry:** a falha incrementa `tentativas`; depois de N tentativas, marca `falha_permanente` para investigação manual, sem travar o resto do lote

### 4.2 Comunicação de aquecimento

Roda diariamente. Envia a cadência de `modelos_mensagem` com `tipo = 'aquecimento'` por e-mail para leads frios (etapa 0/1) que ainda não esgotaram as tentativas.

1. Seleciona leads com `etapa IN (0,1)`, `status_aquecimento = 'ativo'`, `consentimento_marketing = true`, e-mail preenchido e `now() - ultima_comunicacao_em >= dias_apos_anterior` do próximo modelo da sequência (posição `tentativas_aquecimento + 1`)
2. **Confere `status_aquecimento != 'opt_out'` antes de enviar.** É obrigatório pela seção 64 do Prompt Mestre (opt-out de marketing)
3. Envia o modelo correspondente, sempre com o link de descadastro `/public/marketing/descadastro/{token_descadastro}`. Grava em `lead_comunicacoes` com o `modelo_id`, incrementa `tentativas_aquecimento` e atualiza `ultima_comunicacao_em`
4. Se não há próximo modelo na sequência daquela etapa, ou se `tentativas_aquecimento` chegou a `ciclo_vida_configuracoes.max_tentativas_aquecimento`, marca `status_aquecimento = 'esgotado'` e grava `esgotado_em = now()`

> **Quem envia o e-mail:** o próprio módulo, tanto o aquecimento quanto a confirmação de boas-vindas (4.5). A mensagem `identity.email.enviar` da plataforma aceita *só e-mail transacional*; campanhas e listas são do Marketing (AsyncAPI do identity e Mapa §3). Em desenvolvimento o destino é o Mailpit (`mailpit:1025`). Hoje o `docker-compose.yml` não entrega variáveis de SMTP ao serviço `marketing`, e isso está registrado como pendência com o Grupo 2.

O limite de tentativas é configurado por tenant em `ciclo_vida_configuracoes`, não fixado no código. WhatsApp fica fora desta fase: está sem dono no Mapa (seções 65–67) e entra como evolução futura. Se a régua de nutrição também vale para o lead da etapa 2 com faixa `nutricao` ainda está em aberto (seção 11).

### 4.3 Anonimização por retenção (LGPD)

Roda mensalmente. Não apaga a linha do lead, porque isso quebraria o histórico do funil e as métricas de conversão do painel. Remove só o dado pessoal identificável e preserva o valor estatístico.

1. Seleciona leads com `anonimizado = false` e (`status_aquecimento = 'esgotado'` com `now() - esgotado_em >= meses_retencao_pos_esgotamento`) ou (`status_aquecimento = 'opt_out'` com `now() - opt_out_em >= meses_retencao_pos_optout`)
2. Zera `nome`, `email`, `telefone`, `whatsapp`, `token_descadastro` e `dados_formulario`, e também `ip` e `user_agent` das linhas do lead em `lead_consentimentos`. Mantém `etapa`, `origem`, `utm_*`, `pontuacao`, `faixa_qualificacao`, datas e contadores, e no registro de consentimento a finalidade, o aceite, a data e a versão do termo
3. Marca `anonimizado = true` e registra evento em `lead_eventos`

Os dois prazos de retenção são configurados por tenant em `ciclo_vida_configuracoes`, conforme a seção 83 do Prompt Mestre (LGPD).

### 4.4 Atribuição pendente e transbordo [Etapa 2]

Roda a cada minuto, só para tenants com `rodizio_ativo = true`.

1. **Atribuição pendente:** leads MQL sem vendedor, que chegaram fora do horário comercial, recebem vendedor pelo rodízio (9.3) no início do próximo horário de atendimento
2. **Transbordo [Pendente · Grupo 6]:** se o vendedor não fez o primeiro contato em `minutos_transbordo`, o lead passa ao próximo vendedor do rodízio. Quem registra o primeiro contato é o CRM; sem essa informação, este passo fica desligado (seção 11)

### 4.5 Confirmação de boas-vindas [Novo]

Atende o RF06 e o RN01 dos [Requisitos](Requisitos.md): o lead recebe uma mensagem automática de confirmação nos primeiros minutos, e o MQL é abordado por ela em até 5 minutos. Roda a cada minuto, então o e-mail sai em até 1 ou 2 minutos depois do fato.

1. **Quem recebe:** lead com e-mail, `boas_vindas_enviada_em` nulo, `anonimizado = false`, `status_aquecimento != 'opt_out'` e que atende a um dos gatilhos:
   - **enviou o formulário (etapa 3):** recebe mesmo sem `consentimento_marketing`, porque o e-mail só confirma o pedido que a própria pessoa fez;
   - **virou MQL, quente ou morno, antes de enviar (etapa 2):** recebe só com `consentimento_marketing = true`, porque ainda não houve pedido a confirmar.
2. **O que envia:** o modelo `boas_vindas` ativo do tenant (`modelos_mensagem`), com as variáveis do lead e o link de descadastro `/public/marketing/descadastro/{token_descadastro}`. Sai uma vez por lead: o que chegar primeiro entre os dois gatilhos.
3. **O que grava:** `lead_comunicacoes` com o `modelo_id`, `boas_vindas_enviada_em = now()` e `comunicacao_enviada` em `lead_eventos`. Não conta em `tentativas_aquecimento`.
4. **Falha:** grava `lead_comunicacoes` com `status = 'falhou'` e tenta de novo na execução seguinte. Depois de 3 falhas do lead, desiste e o lead aparece com a confirmação pendente no detalhe (9.2).

Lead do Meta Ads não recebe: ele não enviou formulário da landing, e o consentimento dele está em aberto (seção 11). O horário comercial não se aplica: a janela de não perturbe do RN03 vale só para WhatsApp.

---

## 5. Fluxo de escrita

1. **Etapa 0 do Meta:** o webhook grava em `leads_entrada_buffer`, com o tenant resolvido pelo slug do caminho e a assinatura validada (seção 6)
2. **Etapas 0 e 1 do Landing:** o consumidor da fila `marketing.landing-leads` recebe `landing.visita.registrada`, `landing.formulario.aberto` e `landing.contato.informado` e grava em `leads_entrada_buffer`, com o `tenantId` do envelope
3. **Drenagem:** o job 4.1 esvazia o buffer, busca os dados dos leads do Meta na Graph API, faz upsert em `leads` (dedupe por `meta_lead_id`, `visitante_id`, e-mail ou telefone) e grava `lead_eventos`
4. **Etapa 2:** `landing.formulario.atualizado` é processado na chegada. O evento já traz `etapa: 2`. O Marketing escreve direto em `leads` + `lead_eventos`, sem passar pelo buffer. Depois consulta as respostas parciais em `GET /api/landing/envios/{envioId}`, recalcula a pontuação e, se o lead virou MQL, atribui vendedor (9.3). Um MQL com consentimento passa a esperar a confirmação de boas-vindas (4.5)
5. **Etapa 3, fluxo expresso (9.6):** `landing.formulario.recebido` é processado na chegada, em seis passos:
   1. grava `crm_empresa_id` e `crm_contato_id`;
   2. consulta as respostas em `GET /api/landing/envios/{envioId}` e guarda em `dados_formulario`;
   3. calcula a pontuação e a faixa e, se for MQL sem vendedor, atribui pelo rodízio (9.3). Se a consulta das respostas falhar, segue sem pontuação: a oportunidade não espera;
   4. cria a oportunidade no CRM com token de serviço, levando pontuação, faixa e vendedor;
   5. grava `crm_oportunidade_id`;
   6. registra em `lead_eventos` e notifica o vendedor (seção 8).

   A confirmação de boas-vindas sai pelo job 4.5, em até 1 ou 2 minutos, sem esperar o CRM. Se `empresaId` vier nulo (CRM fora do ar no momento do envio), o lead fica na etapa 3 sem oportunidade até o Landing publicar o evento de novo com os ids

**Consentimento:** nas etapas 1, 2 e 3, o `consentimento` do evento é registrado em `lead_consentimentos` quando muda (seção 3). Nas etapas 2 e 3 isso acontece na chegada; na etapa 1, na drenagem do buffer, junto com a criação do lead.

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
>
> O corpo do webhook traz só `leadgen_id`, `page_id`, `form_id` e `ad_id`. O webhook grava isso no buffer e responde na hora; os campos do lead são buscados depois, na drenagem (4.1).

> **Descadastro:** o `GET` mostra a confirmação e o `POST` efetiva o opt-out, gravando `status_aquecimento = 'opt_out'`, `opt_out_em` e `consentimento_marketing = false`, e registrando em `lead_eventos` e a revogação em `lead_consentimentos`, com o IP e o *User-Agent* da requisição. O opt-out não acontece no `GET` porque leitores de e-mail costumam abrir links automaticamente. Token inválido responde a mesma página neutra, para não revelar se o lead existe.

### Superfície autenticada

Token JWT no cabeçalho, validado localmente pelo JWKS do identity (§4.2). Permissão verificada no back-end com `@PreAuthorize` (§5.2). Tenant sempre vindo do claim `tenant_id`; um `tenantId` no corpo ou na query é ignorado (§6).

| Método | Rota | Permissão | Observação |
|---|---|---|---|
| GET | `/api/marketing/leads` | `marketing.lead.ver` | paginada: `pagina`, `tamanho` (máx. 100), `ordenar`. Filtros **[Etapa 2]**: `etapa`, `faixa`, `origem`, `campanha`, `vendedorId`, `periodo`, `q` |
| GET | `/api/marketing/leads/{id}` | `marketing.lead.ver` | lead de outro tenant responde 404. Traz respostas, pontuação com o detalhe, comunicações e timeline (9.2) |
| GET | `/api/marketing/leads/{id}/resumo` **[Etapa 2]** | `marketing.lead.ver_resumo` | para outros módulos (9.6) |
| PUT | `/api/marketing/leads/{id}/vendedor` **[Etapa 2]** | `marketing.qualificacao.editar` | reatribuição manual do vendedor |
| GET | `/api/marketing/busca?q=` **[Novo]** | `marketing.lead.ver` | busca global (§8.6) |
| GET | `/api/marketing/painel` | `marketing.dashboard.ver` | antes `/dashboard` |
| GET | `/api/marketing/painel/eventos` (SSE) | `marketing.dashboard.ver` | antes `/stream`; ver 9.1 |
| GET / PUT | `/api/marketing/canais-anuncio` | `marketing.configuracao.ver` / `.editar` | antes `/settings` |
| GET / POST | `/api/marketing/modelos-mensagem` | `marketing.template.ver` / `.criar` | antes `/templates`; lista paginada |
| PUT / DELETE | `/api/marketing/modelos-mensagem/{id}` | `marketing.template.editar` / `.excluir` | DELETE é soft delete |
| GET / PUT | `/api/marketing/ciclo-vida` | `marketing.automacao.ver` / `.editar` | antes `/lifecycle-settings` |
| GET / PUT | `/api/marketing/qualificacao` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | faixas, horário comercial, rodízio ligado e transbordo |
| GET / POST | `/api/marketing/regras-pontuacao` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | |
| PUT / DELETE | `/api/marketing/regras-pontuacao/{id}` **[Etapa 2]** | `marketing.qualificacao.editar` | DELETE é soft delete |
| GET / PUT | `/api/marketing/rodizio/vendedores` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | quem está na escala |
| GET / POST | `/api/marketing/formularios` **[Etapa 2]** | `marketing.formulario.ver` / `.editar` | lista e criação. Antes `/form-definitions` |
| GET / PUT | `/api/marketing/formularios/{id}` **[Etapa 2]** | `marketing.formulario.ver` / `.editar` | `GET` devolve a versão ativa, também para o Landing com token de serviço; `PUT` publica uma nova versão (9.5) |
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
| Criar a oportunidade | **Marketing**, ao consumir o evento, com `empresaId` e `contatoId` dele, pelo fluxo expresso (9.6) |

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
> - A oportunidade precisa aceitar `responsavelId`, `pontuacao` e `faixaQualificacao`, que vêm da qualificação e do rodízio (9.3).

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
  "pontuacao": 85,
  "faixaQualificacao": "mql_quente",
  "responsavelId": "2d4f6a8c-...",
  "dataCaptura": "2026-09-24T13:04:40Z"
}
```

`origemModuloId` é o `leads.id`. `respostasFormulario` é a cópia em `dados_formulario`, tirada da API do Landing. `responsavelId` é o vendedor escolhido pelo rodízio; nulo quando o lead não é MQL ou está aguardando vendedor. Dados que não existem no momento da captação (valor, produto) o CRM preenche depois, do lado dele.

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
    descricao: Ver o painel do marketing (funil, qualificação, campanhas, custo e distribuição), com atualização em tempo real
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]

  - codigo: marketing.lead.ver
    descricao: Ver a lista de leads, o detalhe de cada um e buscá-los pelo nome
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.lead.ver_resumo
    descricao: Ver o resumo de um lead (origem, etapa, qualificação e vendedor), para outros módulos
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
    # servicos: preencher quando um módulo pedir o resumo com token de serviço (9.6)

  - codigo: marketing.qualificacao.ver
    descricao: Ver as regras de pontuação, as faixas de qualificação e o rodízio de vendedores
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.qualificacao.editar
    descricao: Editar as regras de pontuação, as faixas, o rodízio e reatribuir o vendedor de um lead
    perfisPadrao: [ADMINISTRADOR, MARKETING]

  - codigo: marketing.configuracao.ver
    descricao: Ver as preferências dos canais de anúncio
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
  - codigo: marketing.configuracao.editar
    descricao: Editar as preferências dos canais de anúncio e as credenciais do Meta (webhook e página)
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

  - codigo: marketing.formulario.ver
    descricao: Ver os campos dos formulários de captação
    perfisPadrao: [ADMINISTRADOR, GESTOR, MARKETING]
    servicos: [landing]
  - codigo: marketing.formulario.editar
    descricao: Criar, editar e publicar os campos dos formulários de captação
    perfisPadrao: [ADMINISTRADOR, MARKETING]
```

`marketing.formulario.ver` declara `servicos: [landing]`: o Landing lê a definição do formulário com token de serviço (9.5). As demais permissões não declaram `servicos` por enquanto.

### Permissões de outros módulos que usamos

| Permissão | Para quê | Situação |
|---|---|---|
| `identity.tenant.ver` | resolver o tenant do webhook do Meta | já concedida (`servicos: [..., marketing, ...]`) |
| permissão de listar usuários do identity (código a confirmar) | listar quem tem perfil VENDEDOR, para a escala do rodízio e para mostrar o nome no painel (9.3) | a pedir **[Pendente · Grupo 2]** |
| `crm.empresa.ver_resumo` | mostrar a razão social de um lead que já passou pelo handoff | já no perfil MARKETING |
| `crm.contato.ver_resumo` | mostrar o resumo do contato vinculado | no PR #13 do CRM, ainda aberto |
| `crm.oportunidade.criar` | criar a oportunidade na etapa 3, com token de serviço | pedir `servicos: [marketing]` ao CRM **[Pendente · Grupo 6]** |
| `landing.envio.ver` | consultar as respostas do formulário com token de serviço, nas etapas 2 e 3 | pedido em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md) **[Pendente · Grupo 7]** |

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
    { "rota": "/formularios",   "nome": "Formulários",   "permissao": "marketing.formulario.ver" },
    { "rota": "/configuracoes", "nome": "Configurações", "permissao": "marketing.configuracao.ver" }
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

### Eventos que publicamos [Etapa 2]

A Etapa 2 disponibiliza as informações do painel para quem quiser consumir (9.6). O contrato pede que só se publique o que algum módulo consome: `marketing.formulario.publicado` já tem consumidor; os outros entram no AsyncAPI quando um interessado confirmar.

| Tipo | Quando | Consumidor |
|---|---|---|
| `marketing.formulario.publicado` | nova versão de um formulário publicada | Landing, para atualizar o formulário da página (9.5) |
| `marketing.lead.qualificado` | lead entra numa faixa MQL ou muda de faixa | a confirmar (CRM, relatórios) |
| `marketing.lead.atribuido` | vendedor atribuído ou trocado | a confirmar (CRM) |
| `marketing.lead.descadastrado` | opt-out confirmado | a confirmar (CRM, pelo consentimento do contato; Landing) |

### Pedidos à plataforma (`identity.entrada`)

| Mensagem | Uso no marketing | Condição |
|---|---|---|
| `identity.notificacao.criar` | avisar o vendedor atribuído quando recebe um lead MQL, com a categoria `NOVO_LEAD` e `rota: "/leads/{id}"`. Lead na etapa 3 vai como prioritário | acesso do vendedor à tela do lead e destinatário quando não há vendedor estão em aberto (seção 11) |
| `identity.timeline.registrar` | registrar o handoff na timeline da empresa | exige `empresaId`, que só existe depois do handoff |
| `identity.email.enviar` | não se aplica ao aquecimento | só e-mail transacional (4.2) |

### Consumo: eventos do Landing

Todos pela fila `marketing.landing-leads`, ligada a `landing.eventos`, com `marketing.landing-leads.dlq` para mensagens que falharam três vezes. Formato completo em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md). Os nomes dos quatro primeiros são proposta nossa; o dono é o Landing. Todos trazem o campo `etapa`, com o valor da coluna abaixo.

| Evento | Etapa | O que traz | Processamento |
|---|---|---|---|
| `landing.visita.registrada` | 0 | `visitanteId`, página, `origem` (UTM) | buffer |
| `landing.formulario.aberto` | 0 | `visitanteId`, `formularioId`, `origem` | buffer |
| `landing.contato.informado` | 1 | + `envioId`, `formularioVersao`, contato, consentimento (aceite, data, IP, *User-Agent* e versão do termo) | buffer |
| `landing.formulario.atualizado` | 2 | contato atualizado; publicado só quando o Landing considera o preenchimento "grande parte" | na chegada, com pontuação |
| `landing.formulario.recebido` | 3 | já no PR #16; pedimos acrescentar `visitanteId`, `formularioVersao`, contato, consentimento e `origem` | na chegada, fluxo expresso |

IP e *User-Agent* são dado pessoal e só viajam no evento porque são a evidência de consentimento que a [LGPD](LGPD.md) (8.1) exige. Não aparecem em log.

Todo consumo é idempotente: grava o `id` em `eventos_processados` na mesma transação do efeito e ignora um `id` já visto. O evento `crm.oportunidade.criada`, citado no §12.7, não é necessário: quem cria a oportunidade é o próprio Marketing.

---

## 9. Etapa 2 — Módulo integrado de marketing

É a Etapa 2 do projeto (seção 0): o módulo que a pessoa gestora de marketing usa dentro da casca, via `<iframe>`, e de onde as informações de lead saem para os outros módulos. Tem seis partes: painel (9.1), leads (9.2), qualificação e distribuição (9.3), configurações (9.4), campos do formulário (9.5) e disponibilização (9.6).

> **Decisão de escopo:** a "camada intermediária" de configurações é **config pura** nesta fase. O cliente registra parâmetros e preferências por canal, sem integração real via OAuth ou API com Google Ads ou Meta Business. A única integração real é receber leads do Meta: o webhook e a busca de cada lead na Graph API, que o webhook não traz (4.1). A integração completa fica para a Fase 6 (Integrações Avançadas) do Prompt Mestre.

### Requisitos do iframe e da casca [Regra da infra]

- Funciona aberto direto, com token real de um usuário de teste (`marketing@empresa-a.dev`), e embutido, com o token recebido por `postMessage` (§12)
- Servido sob `/modulos/marketing/`: `base: '/modulos/marketing/'` no Vite e o mesmo prefixo no roteador. A API é chamada pelo caminho relativo `/api/marketing/`
- **Registra o ouvinte de `message` antes de enviar `modulo:pronto`** e guarda a sessão fora dos componentes. É a correção do exemplo de 22/09 (§12.2, novidade da v0.7). Recomendação: copiar `exemplo-modulo/front/src/plataforma/sessao.ts`
- Confere `event.origin` (a própria origem) *e* `event.source === window.parent` em toda mensagem
- Envia `modulo:altura` a cada mudança de conteúdo, `modulo:navegar` com rota relativa (`/leads/9f1c`) e `modulo:token-expirado` ao receber `401`
- Token só em memória, nunca em `localStorage`
- O servidor do front envia `Content-Security-Policy: frame-ancestors 'self'` e nunca `X-Frame-Options: DENY` (§12.8)
- Tema: [Design System Centinela](DesignSystem/design-systemfinal.md) sobre Tailwind v4, com `lucide-react` e a fonte Inter. Ele substitui o shadcn/ui. Usar os tokens do Design System (`bg-brand-950`, `bg-brand-800`, `text-brand-300`...) em vez de hex e aplicar `data-tema` recebido da casca

> **Front independente da casca [Novo].** A integração com a casca está a cargo de outro grupo, e a decisão pode mudar (iframe, micro-frontend ou front único). Por isso o front do Marketing concentra tudo o que depende da casca numa camada só, `plataforma/`: sessão e token, navegação, altura, tema e aviso de token expirado. As telas não falam com `window.parent` nem com `postMessage`; usam essa camada. Caminho base e endereço da API vêm de configuração (`import.meta.env`), não fixos no código. Cada área (painel, leads, formulários, configurações) fica em `src/features/<area>/`, com as próprias rotas, e pode ser montada em outro app sem reescrita. Sozinho, o módulo abre com um layout de desenvolvimento e login de teste; embutido, sem sidebar nem header, que são da casca.

### 9.1 Painel

O painel reúne as informações de lead do tenant: funil, qualificação, campanhas, custo e distribuição. São as mesmas informações que a 9.6 disponibiliza para outros módulos.

**Fonte de dados inicial:** `GET /api/marketing/painel?periodo=&origem=&campanha=`, um único endpoint com tudo o que a primeira renderização precisa.

```json
{
  "success": true,
  "data": {
    "funil": { "etapa0": 120, "etapa1": 40, "etapa2": 15, "etapa3": 6 },
    "qualificacao": { "mqlQuente": 5, "mqlMorno": 9, "nutricao": 22, "semPontuacao": 145 },
    "serieTemporal": [
      { "data": "2026-09-01", "novosLeads": 8, "origem": "meta_ads" },
      { "data": "2026-09-01", "novosLeads": 3, "origem": "landing_page" }
    ],
    "taxaConversao": {
      "etapa0ParaEtapa1": 33.3333,
      "etapa1ParaEtapa2": 37.5,
      "etapa2ParaEtapa3": 40.0,
      "visitantesParaLeads": 12.5,
      "leadsParaMql": 22.2222
    },
    "campanhas": [
      { "utmCampaign": "black-friday-2026", "canal": "meta_ads", "visitantes": 320, "leads": 40, "mql": 9, "etapa3": 3 }
    ],
    "canais": [
      { "canal": "meta_ads", "orcamentoMensal": 1500.00, "leads": 40, "mql": 9, "custoPorLead": 37.50, "custoPorMql": 166.67 }
    ],
    "distribuicao": {
      "porVendedor": [ { "vendedorId": "2d4f6a8c-...", "nome": "Carlos Lima", "atribuidos": 6 } ],
      "aguardandoVendedor": 2
    },
    "leadsRecentes": [
      { "id": "...", "nome": "...", "etapa": 2, "faixa": "mql_morno", "origem": "landing_page", "criadoEm": "..." }
    ]
  },
  "message": null,
  "errors": []
}
```

> **Percentual em pontos [Regra da infra]:** `33.3333` significa 33,33%, com até 4 casas. A versão anterior usava fração (`0.33`), que o §8.7 proíbe.

> **Custo por lead é estimativa.** `custoPorLead` e `custoPorMql` dividem o orçamento mensal informado em `canais_anuncio` pelos leads e MQL do canal no mês. Sem integração com o Ads Manager (decisão de escopo acima), o gasto real não chega ao módulo. ROI e ROAS precisam do valor do contrato, que hoje não tem dono (seção 11).

Filtros: `periodo` (7/30/90 dias ou intervalo customizado), `origem` (`meta_ads`, `landing_page` ou todas) e `campanha` (opcional, via `utm_campaign`). `visitantes` conta os `visitante_id` distintos de `landing.visita.registrada` no buffer.

**Atualização em tempo real via SSE** (`GET /api/marketing/painel/eventos`). O servidor envia eventos pontuais, não o resumo inteiro recalculado:

```
id: 10482
event: lead_criado
data: {"leadId": "...", "etapa": 0, "origem": "meta_ads"}

id: 10483
event: lead_etapa_alterada
data: {"leadId": "...", "etapaAnterior": 1, "etapaNova": 2}

id: 10484
event: lead_qualificado
data: {"leadId": "...", "faixa": "mql_quente", "pontuacao": 85}

id: 10485
event: lead_atribuido
data: {"leadId": "...", "vendedorId": "..."}
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
- Qualificação: MQL quente, MQL morno e nutrição, com a taxa de leads que viram MQL
- Campanhas: visitantes, leads, MQL e etapa 3 por `utm_campaign`, com a conversão da landing (visitantes → leads)
- Canais: orçamento informado e custo estimado por lead e por MQL
- Distribuição: leads por vendedor e quantos aguardam vendedor
- Série temporal de novos leads, filtrável por origem
- Taxa de conversão entre etapas consecutivas
- Lista de leads recentes, atualizada via SSE, com a marca "no CRM" e o link da oportunidade para quem já passou pelo fluxo expresso (9.6)
- Taxa de abertura e clique dos e-mails, quando houver rastreio (seção 11)

### 9.2 Leads

A consulta de leads é do Marketing. O CRM só passa a ter o lead quando ele vira empresa, contato e oportunidade, na etapa 3. Até lá, e mesmo depois, quem quer entender de onde o lead veio e como ele se comportou procura aqui.

**Lista:** `GET /api/marketing/leads`

- Colunas: nome, etapa, faixa de qualificação, pontuação, origem, campanha, vendedor e data de entrada
- Filtros: etapa, faixa, origem, campanha, vendedor, período e busca por nome ou e-mail
- Leads anonimizados aparecem sem dados pessoais, para as contagens baterem com o painel

**Detalhe:** `GET /api/marketing/leads/{id}`

- Contato, consentimento e origem (UTM, landing page, primeira visita). O consentimento mostra o histórico de `lead_consentimentos`: aceite ou revogação, data, versão do termo e formulário. IP e *User-Agent* aparecem só aqui, nunca no resumo (9.6)
- Confirmação de boas-vindas: enviada, pendente ou com falha (4.5)
- Respostas do formulário, com a versão respondida
- Pontuação com o detalhe: quais regras somaram quantos pontos (9.3)
- Vendedor atribuído, com a ação de reatribuir (`marketing.qualificacao.editar`)
- Timeline (`lead_eventos`) e e-mails de aquecimento enviados
- Depois da etapa 3: resumo da empresa e do contato no CRM (`crm.empresa.ver_resumo`, `crm.contato.ver_resumo`) e o link da oportunidade

**Busca global:** `GET /api/marketing/busca?q=` faz o lead aparecer na barra de busca da casca (seção 6).

### 9.3 Qualificação e distribuição

As regras de negócio completas estão em [Regras de Negócio](Regras%20de%20Negocio.md) (7.1 e 7.2). Aqui fica como elas viram dado e processamento no Marketing.

**Pontuação (lead scoring)**

- **Quando calcula:** na etapa 2, com as respostas parciais; na etapa 3, com as respostas completas; e na drenagem, para lead do Meta, com as respostas do formulário instantâneo traduzidas por `mapeamentoCampos`. A origem pontua desde o primeiro cálculo
- **Como calcula:** soma os `pontos` de cada regra ativa de `regras_pontuacao` que bate. Em `campo_formulario`, compara a resposta do campo de mesma `chave` com `valores`; em `utm`, compara a UTM do lead. Cada critério conta uma vez, pela regra de mais pontos
- **Faixa:** `pontuacao >= limite_mql_quente` é `mql_quente`; `>= limite_mql_morno` é `mql_morno`; abaixo disso, `nutricao`
- **O que grava:** `pontuacao`, `faixa_qualificacao`, `pontuacao_detalhe` e `qualificado_em`. Quando a faixa muda, registra `qualificacao_alterada` em `lead_eventos`, envia `lead_qualificado` pelo SSE e publica `marketing.lead.qualificado`

**Regras iniciais de cada tenant** (matriz das Regras de Negócio, 7.1):

| Critério | Fonte | Respostas | Pontos |
|---|---|---|---|
| Cargo | campo `cargo` | Decisor, Sócio, Gerente | 30 |
| Cargo | campo `cargo` | Analista, Operacional, Outros | 10 |
| Orçamento | campo `orcamento` | as opções acima do ticket mínimo, marcadas pela pessoa gestora | 30 |
| Prazo de compra | campo `prazo_compra` | Imediato, Até 30 dias | 25 |
| Prazo de compra | campo `prazo_compra` | Mais de 3 meses, Apenas pesquisando | 5 |
| Origem do tráfego | `utm` | fundo de funil: `google` + `cpc` (Search) ou sem UTM (direto) | 15 |
| Origem do tráfego | `utm` | topo de funil: display e redes sociais | 5 |

Faixas iniciais: 70 ou mais é MQL quente; de 40 a 69, MQL morno; abaixo de 40, nutrição. Os campos `cargo`, `orcamento` e `prazo_compra` precisam existir no formulário (9.5) para as regras pontuarem.

**Distribuição (rodízio)**

- **Quem entra:** lead que vira MQL, quente ou morno, e tem contato (etapa 1 em diante). Nutrição não entra no rodízio
- **Como escolhe:** entre os vendedores ativos em `vendedores_rodizio`, o que recebeu lead há mais tempo (`ultima_atribuicao_em` mais antiga). Grava `vendedor_id` e `vendedor_atribuido_em`, registra `vendedor_atribuido` em `lead_eventos`, publica `marketing.lead.atribuido` e notifica o vendedor (seção 8). MQL quente e morno entram no mesmo rodízio; a notificação do quente vai como prioritária
- **Fora do horário comercial:** o lead fica em "aguardando vendedor" e é atribuído no início do próximo horário de atendimento (job 4.4)
- **Transbordo [Pendente · Grupo 6]:** sem primeiro contato em `minutos_transbordo`, o lead passa ao próximo vendedor. O primeiro contato é registrado no CRM, e hoje nenhum contrato expõe essa informação
- **Disponibilidade [Pendente · Grupo 6]:** as Regras de Negócio falam em vendedor "Online/Disponível" no CRM, dado que também não existe em contrato. Nesta fase, a escala é a lista de ativos em `vendedores_rodizio`, mantida pela pessoa gestora
- **Reatribuição manual:** a pessoa gestora troca o vendedor no detalhe do lead (`PUT /api/marketing/leads/{id}/vendedor`)

### 9.4 Configurações de marketing

Uma tela com quatro abas: canais de anúncio, modelos de mensagem, ciclo de vida e qualificação.

**Aba "Canais de anúncio"**: `GET/PUT /api/marketing/canais-anuncio`

- Um card por canal (`google_ads`, `meta_ads`)
- Orçamento mensal pretendido, em reais, com 2 casas; público-alvo e objetivo da campanha em campos livres. O orçamento alimenta o custo estimado por lead no painel (9.1)
- Sem chamada externa para configurar campanha: é registro de intenção, lido manualmente por quem configura o Ads Manager de fato
- **Card do Meta Ads:**
  - App Secret e verify token do webhook, só de escrita: a tela mostra "configurado" ou "não configurado", nunca o valor
  - ID da página e token de acesso da página, também só de escrita. Sem o token, os leads do Meta chegam sem nome, e-mail e telefone (4.1)
  - URL do webhook deste tenant, pronta para copiar
  - Mapeamento de campos: para cada pergunta do formulário instantâneo do Meta, o campo do nosso formulário que ela corresponde (por `chave`). É o que permite pontuar o lead do Meta (9.3)

**Aba "Modelos de mensagem"**: `GET/POST/PUT/DELETE /api/marketing/modelos-mensagem`

- O modelo de boas-vindas no topo, separado da cadência: um por tenant, sempre ativo, editável e sem exclusão (4.5)
- Lista de modelos de aquecimento, indicando a etapa fria (0/1) de cada um e sua posição na cadência
- Editor com assunto e corpo, aceitando variáveis como `{{nome}}` e `{{origem}}`
- Pré-visualização do modelo com dados de exemplo antes de salvar, incluindo o rodapé com o link de descadastro
- Chave de ativo/inativo nos modelos de aquecimento: os inativos não entram no job 4.2

Cada envio grava `lead_comunicacoes.modelo_id`, o que permite medir abertura e clique por modelo quando houver rastreio (seção 11).

**Aba "Ciclo de vida do lead"**: `GET/PUT /api/marketing/ciclo-vida`

- Máximo de tentativas de aquecimento antes de marcar o lead como esgotado
- Meses de retenção após esgotamento, antes da anonimização automática
- Meses de retenção após opt-out, antes da anonimização automática
- Cada tenant define os próprios valores, sem padrão fixo no código, conforme a seção 83 do Prompt Mestre (LGPD)

**Aba "Qualificação e distribuição"** **[Etapa 2]**: `/api/marketing/qualificacao`, `/regras-pontuacao` e `/rodizio/vendedores`

- Regras de pontuação: critério, campo ou UTM, respostas que pontuam e pontos. Adicionar, editar, desativar e remover
- Faixas: limites de MQL quente e MQL morno, com a prévia de quantos leads atuais cairiam em cada faixa
- Rodízio: ligar e desligar, vendedores na escala, horário comercial, dias de atendimento e minutos de transbordo

Criar e editar em modal com título, confirmação em modal pequeno e vermelho só para ação irreversível (regras de uso do tema).

### 9.5 Campos do formulário

> **Decisão do Grupo 4 (24/09): quem define os campos do formulário é o Marketing**, a partir da Etapa 2. O Landing continua dono da página: layout, estilo, editor visual de arrastar e soltar (seção 56 do Prompt Mestre), exibição do formulário e gravação das respostas (envios). A proposta vai ao Grupo 7 em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md), seção 10, e muda o que o Mapa de Fronteiras previa para a seção 56 (seção 11).

**O que é fixo e o que a pessoa gestora escolhe:**

- **Campos fixos**, sempre presentes e travados na tela: nome, e-mail, telefone e o aceite de comunicação de marketing. São os dados da etapa 1 do funil, e o Landing precisa deles para publicar `landing.contato.informado`
- **Campos configuráveis:** as perguntas depois do contato, que levam o lead da etapa 2 do funil em diante. Por exemplo, cargo, orçamento, prazo de compra, número de funcionários, CNPJ e necessidade. São eles que alimentam a pontuação (9.3)

**Tela "Formulários":** `GET/POST /api/marketing/formularios` e `GET/PUT /api/marketing/formularios/{id}`

- Lista de formulários do tenant (um por landing page ou campanha), com a versão publicada de cada um
- Editor de campos: rótulo, tipo, chave, obrigatório, ordem e opções. Adicionar, editar, remover e reordenar (lista simples, não layout)
- Tipos de campo iguais aos do Prompt Mestre (seção 56): texto, e-mail, telefone, WhatsApp, CNPJ, CPF, lista, checkbox, radio, textarea, data
- Marca os campos usados em regras de pontuação e avisa antes de remover um deles
- Pré-visualização simples, como lista de perguntas, sem layout

**Botão "Publicar":** `PUT /api/marketing/formularios/{id}`

- Grava uma nova linha em `formulario_versoes`, atualiza `formularios.versao_ativa` e publica `marketing.formulario.publicado`
- Versões publicadas não mudam. Combina com `leads.formulario_versao`: dá para saber a qual versão cada lead respondeu

**Como o Landing usa a definição:**

- Lê a versão ativa em `GET /api/marketing/formularios/{id}`, com token de serviço (`servicos: [landing]` em `marketing.formulario.ver`)
- Guarda em cache pela versão e renova ao receber `marketing.formulario.publicado`
- Os `formularioId` e `formularioVersao` dos eventos do Landing passam a ser os nossos, e o `campoId` das respostas é o `id` do campo aqui

**Formato de cada item de `campos`,** a publicar no `contratos/marketing.yaml` como schema `Campo`:

```json
{
  "id": "string (uuid)",
  "chave": "string (ex.: cargo; estável entre versões e formulários, usada na pontuação)",
  "label": "string",
  "tipo": "texto | email | telefone | whatsapp | cnpj | cpf | lista | checkbox | radio | textarea | data",
  "obrigatorio": "boolean",
  "fixo": "boolean (true em nome, email, telefone e consentimento)",
  "ordem": "integer",
  "opcoes": "string[] (obrigatório só quando tipo = lista | radio | checkbox)"
}
```

### 9.6 Disponibilização para outros módulos

As informações do painel não ficam presas na tela. O Marketing as oferece para quem precisar, por três caminhos:

| Caminho | O que entrega | Para quem |
|---|---|---|
| API `GET /api/marketing/leads/{id}/resumo` | origem, UTM, etapa, faixa, pontuação e vendedor de um lead | módulos com token de serviço (`servicos` em `marketing.lead.ver_resumo`) e usuários com essa permissão |
| Eventos em `marketing.eventos` | lead qualificado, lead atribuído, lead descadastrado, formulário publicado | quem ligar uma fila a eles (seção 8) |
| Views `vw_pub_*` | indicadores do painel (funil, qualificação, campanhas, custo) para relatórios | Grupo 3, se pedir (`contratos/marketing.views.md`) |

```
GET /api/marketing/leads/9f1c4e2a-.../resumo

→ 200 { "success": true,
        "data": {
          "id": "9f1c4e2a-...",
          "etapa": 3,
          "temperatura": "quente",
          "faixaQualificacao": "mql_quente",
          "pontuacao": 85,
          "origem": "landing_page",
          "utmCampaign": "black-friday-2026",
          "vendedorId": "2d4f6a8c-...",
          "crmOportunidadeId": "4a6c8e0b-..." },
        "message": null, "errors": [] }
```

O resumo não traz e-mail, telefone nem respostas: dado pessoal fica no detalhe, com `marketing.lead.ver`.

**Fluxo expresso do lead quente (etapa 3) · proposta**

Quem enviou o formulário completo só precisa assinar o contrato. Esse lead não espera lote nem fila: é processado na chegada de `landing.formulario.recebido` e vai direto ao CRM como oportunidade, com pontuação, faixa e vendedor quando já houver (seção 5, passo 5).

- **Continua no Marketing:** o lead aparece no painel e na lista com a marca "no CRM" e o link da oportunidade, e entra em todas as métricas
- **Não trava:** se a consulta das respostas falhar, a oportunidade vai sem pontuação, e o Marketing recalcula depois só para o painel. Se o CRM estiver fora do ar, a criação é tentada de novo (seção 6)
- **Avisa na hora:** notificação prioritária ao vendedor atribuído (seção 8)
- **A confirmar com o Grupo 6:** se o CRM prefere continuar recebendo por API, como hoje, ou por evento (`marketing.lead.qualificado`)

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
| 15 | suíte com Testcontainers (PostgreSQL e RabbitMQ) e WireMock para CRM, Landing, identity e Graph API do Meta; os eventos do Landing são publicados pelo próprio teste no RabbitMQ do Testcontainers; nenhum outro módulo no ar |
| 17, 18 | eventos com envelope e `user_id = mq_marketing`, descritos no AsyncAPI; consumidor testado com a mesma mensagem entregue duas vezes |

O `exemplo-modulo` já cobre os itens 2 a 6, 9, 10, 15 e 18 com testes automatizados. Copiá-los e adaptar as rotas é o caminho mais curto. Para desenvolver contra o CRM antes de ele existir, use um stub do Prism a partir de `contratos/crm.yaml` (sem tirar o `-m false`). O identity nunca é stub: use a imagem real e os usuários de `docs/usuarios-de-teste.md`.

Além do checklist, a Etapa 2 precisa de testes próprios: a pontuação com a matriz inicial da 9.3 (um lead de cada faixa), o rodízio com três leads MQL e dois vendedores ativos, a atribuição fora do horário comercial e a publicação de formulário gerando nova versão sem alterar a anterior.

A Etapa 1 também ganha testes próprios: a confirmação de boas-vindas sai uma vez só, mesmo com o lead passando pelos dois gatilhos, e não sai para MQL sem consentimento nem para quem se descadastrou (4.5); o mesmo consentimento entregue em três eventos gera uma linha só em `lead_consentimentos`, e o descadastro grava a revogação com IP e *User-Agent*.

### Entrega no `infra-integrador-2026`

- `contratos/marketing.yaml` (OpenAPI 3.1): endpoints que a casca e outros módulos usam, antes de implementar (§14.1)
- `contratos/marketing.asyncapi.yaml` (AsyncAPI 3.0): começa com `marketing.formulario.publicado`, que já tem consumidor
- `permissoes/marketing.yaml` e `modulos/marketing.json` (seção 7)
- `IMAGEM_MARKETING` e `IMAGEM_MARKETING_FRONT` com a conta do grupo, em `.env.example`
- `contratos/marketing.views.md`: só se o Grupo 3 pedir *views* `vw_pub_*` para relatórios

---

## 11. Pendências e decisões em aberto

| Pendência | Com quem | Situação |
|---|---|---|
| Aceite da proposta ao Landing ([`contratos/landing-requisitos.md`](contratos/landing-requisitos.md)): cinco eventos, `visitanteId` e `envioId`, UTM, consentimento com IP, *User-Agent* e versão do termo, `GET /api/landing/envios/{envioId}` e `servicos: [marketing]` em `landing.envio.ver` | Grupo 7 | **[Rascunho a enviar]** |
| Confirmação de boas-vindas sem consentimento de marketing para quem enviou o formulário (4.5): a leitura é que ela confirma o pedido da própria pessoa e não é comunicação de marketing | Grupo 4, professores | Proposta a confirmar |
| Campos do formulário definidos pelo Marketing (9.5): o Landing lê `GET /api/marketing/formularios/{id}`, ouve `marketing.formulario.publicado` e usa nossos ids. Muda o que o Mapa previa para a seção 56, então precisa do aceite do Grupo 7 e do registro no Mapa | Grupo 7, gestores | **[Rascunho a enviar]** |
| Critério da etapa 2 ("grande parte do formulário"), definido e aplicado pelo Landing; precisamos conhecê-lo para documentar no painel | Grupo 7 | A combinar |
| Consentimento de marketing dos leads do Meta Ads (etapa 0) antes do aquecimento | Grupo 4 | A definir |
| Token de acesso da página do Meta: o webhook só traz o `leadgen_id`, e buscar os dados na Graph API é uma chamada externa, além do que a "config pura" previa. Confirmar também que o container pode sair para a internet | Grupo 4, Grupo 2 | A decidir |
| Fronteira entre `leads` (dados de quem ainda não é contato) e Contato do CRM (§10, regra do dono único) | Grupo 6 | A confirmar |
| `POST /api/crm/oportunidades` no contrato do CRM, com o formato da seção 6 (incluindo `responsavelId`, `pontuacao` e `faixaQualificacao`), e `servicos: [marketing]` em `crm.oportunidade.criar` | Grupo 6 | A pedir |
| Primeiro contato do vendedor, para o transbordo do rodízio (evento ou consulta no CRM) | Grupo 6 | A pedir |
| Disponibilidade do vendedor ("Online/Disponível"): se existir no CRM, como o Marketing consulta | Grupo 6 | A confirmar |
| Listar usuários com perfil VENDEDOR e seus nomes no identity (escala do rodízio e painel) | Grupo 2 | A pedir |
| Acesso do vendedor ao lead notificado: dar `marketing.lead.ver` ao perfil VENDEDOR ou notificar com a rota da oportunidade no CRM | Grupo 4 | A definir |
| Quem recebe a notificação `NOVO_LEAD` quando não há vendedor (rodízio desligado ou fora do horário) | Grupo 4 | A definir |
| Régua de nutrição para o lead da etapa 2 com faixa `nutricao`: estender o job 4.2 ou não | Grupo 4 | A definir |
| Custo real e ROI/ROAS: hoje o custo por lead é estimado pelo orçamento informado; ROI precisa do valor do contrato, e Propostas e Contratos estão sem dono no Mapa | Grupo 4, professores | Evolução futura |
| Construtor visual de automação (60), gatilhos e ações (61–62) e campanhas de e-mail (63), atribuídos ao Grupo 4 pelo Mapa e não cobertos aqui | Grupo 4, professores | Lacuna de escopo a definir |
| WhatsApp e telefonia (65–67), sem dono no Mapa | — | Fora desta fase; evolução futura |
| SMTP para os e-mails de boas-vindas e de aquecimento: o compose não entrega variáveis de SMTP ao `marketing`, e o provedor fora do desenvolvimento depende dos professores (P6) | Grupo 2, professores | A pedir |
| SSE pelo gateway: a conexão longa sobrevive à regra de `504` em 3 s? | Grupo 2 | A confirmar |
| Rate limit de 120 req/min por IP em `/public/**` pode barrar rajadas do webhook do Meta | Grupo 2 | A confirmar |
| Handshake do Meta exige devolver `hub.challenge` em texto puro, fora do envelope; o §8.2 só prevê exceção para download de arquivo | Grupo 2 | Pedir exceção registrada |
| Onde guardar App Secret, verify token e token da página do Meta por tenant (proposta: colunas cifradas em `canais_anuncio`) | Grupo 4 | Proposta a confirmar |
| Consumidores de `marketing.lead.qualificado`, `marketing.lead.atribuido`, `marketing.lead.descadastrado` e do resumo do lead (9.6) | Interessados | A confirmar antes do AsyncAPI |
| Fluxo expresso do lead quente: o CRM recebe por API, como hoje, ou por evento? | Grupo 6 | Proposta a confirmar |
| Rastreio de aberto e clicado em `lead_comunicacoes` (pixel e redirecionamento públicos, §12.5) | Grupo 4 | A definir |
| Retenção do `payload_bruto` do buffer já processado, que contém dado pessoal. A contagem de visitantes do painel usa o buffer: guardar só `visitante_id` e UTM depois de processar resolve os dois | Grupo 4 | A definir |
| Auditoria em `audit_logs` no próprio schema (proposta do Mapa §5, ainda não decidida) | Grupo 2 | Acompanhar |
| Ratificação das v0.6 e v0.7 do Contrato: o Marketing ainda não se manifestou; objeções no PR #5 até 24/09 | Gestores | Prazo em 24/09/2026 |
| Decisão D4 (CRM dono de Empresas e Contatos): falta a manifestação do Marketing | Gestores | A manifestar |

---

## 12. Registro de alterações

### 25/09/2026 · boas-vindas, log de consentimento e front

- Novo job 4.5: confirmação de boas-vindas por e-mail em até 1 ou 2 minutos para quem envia o formulário ou vira MQL, atendendo o RF06 e o RN01. Novo `modelos_mensagem.tipo` (`boas_vindas` ou `aquecimento`) e `leads.boas_vindas_enviada_em`
- Nova tabela `lead_consentimentos`, com data, IP, *User-Agent*, versão do termo e formulário de cada aceite e revogação, atendendo a LGPD 8.1 e o RC03. O Landing passa a mandar IP, *User-Agent* e versão do termo no `consentimento` dos eventos; o descadastro grava a revogação; a anonimização zera IP e *User-Agent*
- Front independente da casca: tudo o que depende dela fica na camada `plataforma/`, para aguentar mudança de decisão sobre iframe ou front único
- Tema com os tokens do Design System Centinela (`bg-brand-*`) no lugar de `ui/plataforma.css`
- Seção 0 deixa claro que a landing page e o formulário público são do Landing, fora da plataforma compartilhada

### 24/09/2026 · Etapa 2: módulo integrado de marketing

- Nova seção 0: as duas etapas do projeto. A Etapa 2 é o módulo de marketing integrado na plataforma, e a seção 9 passou a descrevê-lo por inteiro
- Consulta de leads, pontuação, faixa MQL, rodízio de vendedores e métricas ficam no Marketing, aparecem no painel e são disponibilizados para outros módulos (9.6)
- Novas subseções: 9.2 Leads, 9.3 Qualificação e distribuição, 9.6 Disponibilização. O painel ganhou qualificação, campanhas, custo estimado e distribuição
- Campos do formulário definidos pelo Marketing (9.5), com parte fixa (contato e consentimento) e parte configurável. O Landing lê a definição pela nossa API. Saiu o "Pendente · Grupo 7" da seção; a proposta vai ao Landing
- Etapa 3 vira o fluxo expresso do lead quente: vai direto ao CRM e continua aparecendo no Marketing
- Novas tabelas: `formulario_versoes`, `regras_pontuacao`, `qualificacao_configuracoes` e `vendedores_rodizio`. Novas colunas em `leads` (pontuação, faixa, vendedor) e em `canais_anuncio` (página do Meta, mapeamento de campos)
- Leads do Meta: o webhook traz só o `leadgen_id`, e a drenagem busca os dados na Graph API com o token da página
- Novas rotas, permissões (`marketing.lead.ver_resumo`, `marketing.qualificacao.*`), eventos publicados, job 4.4 e pendências

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
