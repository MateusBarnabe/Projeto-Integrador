# Documento Norteador — Módulo de Marketing (Grupo 4)

Código `marketing` · Schema `marketing` · Projeto Integrador 2026 · Grupo 4 · Revisão de 05/11/2026

*Documento norteador — incrementado conforme o projeto avança · Alinhado ao Contrato de Integração v0.7 e aos contratos publicados do Landing e do CRM*

> **Base desta revisão:** repositório `infra-integrador-2026` do Grupo 2, em 05/11/2026. Foram usados o Contrato de Integração **v0.7** (em vigor desde 25/09), o Mapa de Fronteiras v0.2, o checklist de conformidade, o módulo de exemplo e os contratos publicados: `landing.yaml` e `landing.asyncapi.yaml` (Grupo 7, PR #26), `crm.yaml` e `crm.asyncapi.yaml` (Grupo 6) e o nosso `marketing.yaml` 0.2.0 (PR #29). Onde este documento diz *§*, a referência é a seção do Contrato de Integração.
>
> O que outros grupos precisam saber de nós está separado em [`contratos/`](contratos/); este documento continua sendo a fonte da verdade do Grupo 4.
>
> Marcadores usados:
> - **[Etapa 2]** faz parte do módulo de marketing integrado na plataforma (seção 0).
> - **[Pendente · Grupo 7]**, **[Pendente · Grupo 6]** ou **[Pendente · Grupo 2]** depende de alinhamento com esse grupo.
> - **[Novo]** foi acrescentado para cumprir o contrato ou acompanhar outro grupo.
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
   - [9.5 Formulários e respostas](#95-formulários-e-respostas)
   - [9.6 Disponibilização para outros módulos](#96-disponibilização-para-outros-módulos)
10. [Testes e conformidade](#10-testes-e-conformidade)
11. [Pendências e decisões em aberto](#11-pendências-e-decisões-em-aberto)
12. [Registro de alterações](#12-registro-de-alterações)

---

## 0. As duas etapas do projeto

O Marketing é entregue em duas etapas. A **Etapa 1** faz o lead chegar e ser registrado. A **Etapa 2** é o **módulo de marketing integrado na plataforma**: a área em que a pessoa gestora de marketing acompanha os resultados, configura o marketing e decide como os leads são qualificados e distribuídos. É também de onde as informações de lead saem para os módulos que precisam delas.

> **Etapa do projeto não é etapa do funil.** "Etapa 1" e "Etapa 2", com maiúscula, são as fases do projeto. As etapas 0 a 3 do lead são as etapas do funil (seção 2).

| | Etapa 1 · Captação | Etapa 2 · Módulo integrado de marketing |
|---|---|---|
| O que entrega | o lead chega pelo webhook do Meta e pelos eventos do Landing, é deduplicado, tem o consentimento registrado, avança no funil, recebe a confirmação de boas-vindas e o aquecimento por e-mail e, na etapa 3 do funil, é ligado à oportunidade que o Landing cria no CRM | painel, lista e busca de leads, qualificação e distribuição para vendas, configurações de marketing (Meta Ads e outros canais), leitura das respostas dos formulários do Landing e disponibilização dos dados |
| Quem usa | ninguém vê diretamente: roda no back-end | a pessoa gestora de marketing, dentro da casca; e os módulos interessados, pela API e pelos eventos |
| Onde está | seções 2 a 5, 8 e a superfície pública da seção 6 | seção 9, e as tabelas, rotas e permissões marcadas **[Etapa 2]** nas seções 3, 6 e 7 |

**Formulários, landing pages e envios são do Landing (Grupo 7).** Isso vale para a página pública que o visitante preenche e também para o editor de formulários e campos, que fica no back-office do Landing, dentro da casca. O Marketing não monta formulário nem guarda a definição dele: recebe o aviso de cada envio por evento e lê a definição e as respostas pela API do Landing (9.5).

**O que a Etapa 2 oferece:**

1. **Painel** (9.1): funil, conversão, qualificação, campanhas, custo por lead e distribuição entre vendedores, com atualização em tempo real.
2. **Leads** (9.2): lista, detalhe e busca global. A consulta de leads é do Marketing. O CRM só passa a ter o lead quando ele vira empresa, contato e oportunidade.
3. **Qualificação e distribuição** (9.3): pontuação (lead scoring), faixa MQL e rodízio entre vendedores, conforme as [Regras de Negócio](Regras%20de%20Negocio.md).
4. **Configurações de marketing** (9.4): canais de anúncio, com o Meta Ads, modelos de mensagem, ciclo de vida e regras de qualificação.
5. **Formulários e respostas** (9.5): o Marketing lê do Landing o que cada lead respondeu, para mostrar no detalhe e pontuar. Criar e editar formulários é no Landing.
6. **Disponibilização** (9.6): API e eventos para os interessados consumirem, e o tratamento na hora do lead quente que chega ao CRM.

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
| RabbitMQ | usuário `mq_marketing`; publica só em `marketing.eventos` e `identity.entrada`; lê qualquer `*.eventos` para ligar as próprias filas | §9.7, §13.3 |
| Token de serviço | `clientId = marketing`, segredo em `SVC_MARKETING_SEGREDO` | §9.2 |
| Variáveis de ambiente | as do `docker-compose.yml` da plataforma: `SERVER_PORT`, `DB_URL`, `DB_APP_USER`/`DB_APP_PASSWORD`, `DB_OWNER_USER`/`DB_OWNER_PASSWORD`, `RABBITMQ_HOST`/`PORT`/`VHOST`/`USER`/`PASSWORD`, `JWKS_URI`, `IDENTITY_BASE_URL`, `SVC_CLIENT_ID`/`SVC_CLIENT_SECRET` | compose do infra |
| Stack | Java 21, Spring Boot 3.x, Maven, Flyway, Spring AMQP; React + Vite + TypeScript, Tailwind v4 | §13.1 |
| Imagens | `ghcr.io/{conta}/marketing` e `marketing-front`, publicadas a cada merge com `:latest` e `:sha-{commit}` | §14.3 |

### Escopo atribuído pelo Mapa de Fronteiras

| Seções do Prompt Mestre | Situação no Mapa | Neste documento |
|---|---|---|
| 56–57 · Formulários e landing pages | Grupo 7 | Do Landing, inclusive o editor de campos. O Marketing só lê as respostas (9.5) |
| 58–59 · Tracking e UTM | com Grupo 7 | UTM capturada pelo Landing, lida na origem do envio (`origem.primeira` e `origem.ultima`) ou no evento do chatbot, guardada em `leads` e mostrada por campanha no painel (9.1) |
| 60 · Construtor visual de automação | definido | Não coberto. Lacuna registrada na seção 11 |
| 61–62 · Gatilhos e ações | compartilhado | Cadência de aquecimento (4.2), qualificação e rodízio (9.3). Lacuna registrada na seção 11 |
| 63 · Campanhas de e-mail | definido | Só e-mail de aquecimento. Lacuna registrada na seção 11 |
| 64 · Opt-out | definido | Descadastro público e anonimização (4.3, 6) |
| 65–67 · WhatsApp, e-mail, telefonia | sem dono | Só e-mail. WhatsApp fica como evolução futura |

### Duas superfícies (§12.4–12.5)

| | Autenticada (casca, iframe) | Pública (sem login) |
|---|---|---|
| O que é do marketing | o módulo da Etapa 2: painel, leads, qualificação e distribuição, configurações, modelos de mensagem e ciclo de vida | webhook do Meta Ads e página de descadastro. O visitante da landing page e do formulário é atendido pelo Landing, não pelo Marketing |
| Tenant | claim `tenant_id` do token | subdomínio ou slug, nunca parâmetro alterável |
| Devolve dado de negócio | sim, conforme permissão | nunca; só recebe informação |

> **Divisão com o Landing (Grupo 7) e o CRM (Grupo 6), conforme os contratos publicados.**
>
> 1. O **Landing** é dono de formulários, campos, envios, landing pages, chatbot e das métricas de visita e conversão da página. O Marketing não guarda cópia do formulário (`landing.yaml`).
> 2. O Landing publica dois eventos em `landing.eventos`: **`landing.formulario.recebido`**, quando um envio é concluído, e **`landing.chatbot.lead_capturado`**, quando o chatbot obtém e-mail ou telefone com consentimento antes do fim da conversa (este ainda está como proposta no contrato deles). Nenhum dos dois traz a etapa do funil: **o Marketing deriva a etapa** do tipo de fato recebido (seção 2).
> 3. As respostas, a origem e o consentimento de um envio o Marketing lê em `GET /api/landing/envios/{id}` (`landing.envio.ver`); a definição do formulário, em `GET /api/landing/formularios/{id}/resumo` (`landing.formulario.ver_resumo`). As duas aceitam o token de serviço do `marketing`, direto em `http://landing:8088`, com `X-Tenant-Id`, e as duas permissões já declaram `servicos: [marketing]`.
> 4. Antes de publicar o envio, o Landing cria ou reaproveita **empresa e contato** no CRM e, pelo Contrato §12.7 e pela permissão `crm.oportunidade.criar` (`servicos: [landing]`), também a **oportunidade**. **O Marketing não cria oportunidade:** consome `crm.oportunidade.criada` para ligar o lead a ela (9.6).
>
> O que ainda pedimos ao Landing está em [`contratos/landing-requisitos.md`](contratos/landing-requisitos.md).

---

## 2. Funil de leads

| Etapa | Descrição | Temperatura | Fonte | Processamento |
|---|---|---|---|---|
| 0 | Lead do Meta Ads: preencheu o formulário instantâneo do anúncio e não interagiu com a landing | Frio | webhook do Meta | lote, pelo buffer (4.1), porque os dados vêm da Graph API |
| 1 | Deixou e-mail ou telefone, com consentimento, na conversa do chatbot, e ainda não concluiu | Frio | `landing.chatbot.lead_capturado` | na chegada |
| 2 | Preencheu grande parte do formulário e não enviou | Médio | **sem fonte hoje [Pendente · Grupo 7]** | — |
| 3 | Enviou o formulário, por qualquer canal (`formulario`, `landing_page`, `embed` ou `chatbot` concluído); falta assinar contrato | Quente | `landing.formulario.recebido` | na chegada, com leitura do envio e pontuação |

**A etapa é derivada pelo Marketing.** O Landing não manda etapa: o consumidor grava a etapa que corresponde ao evento e nunca rebaixa a que o lead já tem. Um `landing.chatbot.lead_capturado` que chega depois do `landing.formulario.recebido` da mesma conversa só completa dados.

**A etapa 2 fica reservada.** A coluna continua aceitando 0 a 3, mas nenhum fato publicado hoje leva o lead a ela. O Landing guarda as respostas parciais das sessões passo a passo e do chatbot, mas não avisa quando o preenchimento passa de "grande parte". O pedido está na seção 11.

**Visitante sem contato não é lead.** Visita, clique em "fale conosco" e início de preenchimento não chegam ao Marketing: são as métricas do Landing (`landing.metrica.ver`, tela Métricas do Landing). O funil do Marketing começa quando há um identificador de contato.

**Etapa do funil e qualificação medem coisas diferentes.** A etapa, e a temperatura que sai dela, mede quanto o lead avançou. A qualificação (9.3) mede o perfil, pela pontuação das respostas e da origem. Um lead pode estar na etapa 3 e cair na faixa de nutrição. O painel mostra as duas.

Fluxo de saída: `LEAD → CRM (oportunidade) → PROPOSTA → CONTRATO`. Na etapa 3, o Landing cria empresa, contato e oportunidade no CRM, e o Marketing liga o lead à oportunidade quando recebe `crm.oportunidade.criada` (9.6). Proposta e contrato seguem a partir de `crm.oportunidade.ganha`, consumido por Contratos e Financeiro (`crm.asyncapi.yaml`).

---

## 3. Tabelas

> **Regras que valem para todas as tabelas [Regra da infra]**
>
> - As **sete colunas obrigatórias** (§7.2): `id uuid`, gerado pela aplicação; `tenant_id uuid not null`, sempre indexado; `created_at`, `updated_at` e `deleted_at` em `timestamptz` UTC, com `deleted_at` nulo significando ativo (soft delete); `created_by` e `updated_by` em `uuid`, vindos do claim `sub`. Nas tabelas abaixo elas aparecem resumidas como "colunas obrigatórias".
> - Em registros criados por rotina automática, consumidor de evento ou rota pública, sem usuário, `created_by` e `updated_by` ficam nulos.
> - Nomes em `snake_case`, minúsculos, sem acento e no plural. Toda mudança de estrutura passa por migration do Flyway, rodando como `own_marketing`.
> - Dinheiro em `numeric(15,2)` e percentual em `numeric(9,4)`, em pontos percentuais (§8.7).
> - `FOREIGN KEY` só dentro do schema `marketing`. Referências a outros módulos guardam apenas o UUID (§7.1, §10.5).

### `leads`

Registro central do lead. Existe quando há um identificador de contato: leads do Meta (etapa 0), capturas do chatbot (etapa 1) e envios do Landing (etapa 3).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| etapa | smallint | 0–3 (seção 2) |
| temperatura | varchar, GENERATED ALWAYS AS | espelho da etapa: 0/1→frio, 2→médio, 3→quente. Sem override manual por ora |
| origem | varchar | `meta_ads`, `landing_page`, `manual`. Todo lead que vem do Landing é `landing_page`, qualquer que seja o canal |
| canal **[Novo]** | varchar, nullable | `canal` do envio no Landing: `formulario`, `landing_page`, `embed` ou `chatbot`. Nulo em lead do Meta |
| nome / email / telefone / whatsapp | varchar, nullable | `whatsapp` é só dado de contato; o canal não é usado nesta fase |
| utm_source / utm_medium / utm_campaign / utm_term / utm_content | varchar, nullable | da última origem do visitante (`origem.ultima` do envio ou `origem` do evento do chatbot). Valor vazio nunca sobrescreve um preenchido |
| landing_page_url | varchar, nullable | `origem.ultima.paginaEntrada` do envio |
| first_touch / last_touch | timestamptz, nullable | `origem.primeira.ocorridoEm` e `origem.ultima.ocorridoEm` do envio |
| sessao_id **[Novo]** | uuid, nullable | `sessaoId` do chatbot. Liga a captura da etapa 1 ao envio da mesma conversa. **UNIQUE (tenant_id, sessao_id)**. Substitui `visitante_id`, que não existe mais no Landing |
| envio_id | uuid, nullable | `envioId` de `landing.formulario.recebido`. **UNIQUE (tenant_id, envio_id)**. Chave para ler as respostas e para ligar a oportunidade (9.6) |
| formulario_id | uuid, nullable | `formularioId` do evento; é o id do formulário no Landing. Sem FK |
| dados_formulario | jsonb | cópia das respostas lidas em `GET /api/landing/envios/{envioId}` (`campoId`, `rotulo`, `tipo`, `valor`), como registro histórico do que o lead respondeu |
| envio_lido_em **[Novo]** | timestamptz, nullable | quando as respostas foram lidas no Landing. Nulo com `envio_id` preenchido quer dizer leitura pendente (job 4.6) |
| consentimento_marketing | boolean, default false | o lead aceitou ser contatado (LGPD). Sem ele, o job 4.2 não envia. É o estado atual; a evidência de cada aceite e revogação fica em `lead_consentimentos` |
| consentimento_em | timestamptz, nullable | quando o consentimento atual foi aceito no Landing (`aceitoEm`) |
| boas_vindas_enviada_em | timestamptz, nullable | quando o e-mail de boas-vindas saiu (4.5). Garante um envio só por lead |
| meta_lead_id | varchar, nullable | `leadgen_id` do Meta, para dedupe de reentrega do webhook. **UNIQUE (tenant_id, meta_lead_id)** |
| crm_empresa_id / crm_contato_id | uuid, nullable | `empresaId` e `contatoId` de `landing.formulario.recebido`. Só o UUID, nunca FK entre schemas |
| crm_oportunidade_id | uuid, nullable | `oportunidadeId` de `crm.oportunidade.criada` (9.6). Só o UUID |
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
| opt_out_em | timestamptz, nullable | base da retenção após opt-out |
| token_descadastro | varchar, UNIQUE | token opaco e aleatório do link de descadastro (seção 6). Não é token da plataforma |
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

> **Ajuste da `V2` na `V8` [Novo].** As colunas acima são o resultado da `V2` com a `V8` (tarefa D8 do [PROXIMOS-PASSOS](../PROXIMOS-PASSOS.md)), que tirou `visitante_id` e `formulario_versao`, criou `sessao_id`, `canal` e `envio_lido_em` e trocou os valores antigos de origem do consentimento e de tipo de evento, sem editar a `V2`.

> **Fronteira com Contato do CRM [Pendente · Grupo 6].** O CRM é o dono de empresas e contatos (§10), e o Mapa proíbe copiar a tabela de outro grupo. Até o envio, o lead não existe no CRM, e `leads` guarda os dados de contato de quem ainda *não* é contato (lead do Meta e captura do chatbot). No envio, o Landing cria o contato e o lead passa a apontar para ele por `crm_contato_id`. Precisamos confirmar com o Grupo 6 que essa leitura atende à regra do dono único (seção 11).

### `lead_eventos` (antes `lead_events`)

Timeline e auditoria da progressão do lead, exigidas pelo Prompt Mestre em todo módulo.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| sequencia | bigint GENERATED ALWAYS AS IDENTITY, UNIQUE | ordem global, usada como `id` das mensagens SSE (9.1) |
| lead_id | uuid | FK para `leads.id`, no mesmo schema |
| tipo_evento | varchar | `etapa_alterada`, `respostas_lidas`, `comunicacao_enviada`, `consentimento_registrado`, `qualificacao_alterada`, `vendedor_atribuido`, `handoff_crm`, `descadastrado`, `anonimizado`. `respostas_lidas` substitui `formulario_atualizado` (D8); `handoff_crm` marca a ligação com a oportunidade |
| etapa_anterior / etapa_nova | smallint, nullable | |
| payload | jsonb | detalhes do evento, sem dado pessoal além do necessário |
| colunas obrigatórias | §7.2 | |

### `leads_entrada_buffer` (antes `lead_intake_buffer`)

Área de espera dos leads do Meta Ads. O webhook responde na hora e grava só os identificadores; o job 4.1 busca os dados na Graph API depois. **O Landing não passa mais por aqui:** os eventos dele são processados na chegada (seção 5).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | isolamento por tenant mantido mesmo no buffer |
| meta_lead_id | varchar, not null | `leadgen_id` do webhook. **UNIQUE (tenant_id, meta_lead_id)**: reentrega do webhook não duplica a linha |
| payload_bruto | jsonb | corpo do webhook: `leadgen_id`, `page_id`, `form_id` e `ad_id`. **Não tem dado pessoal**: nome, e-mail e respostas vão direto para `leads` |
| processado / processado_em | boolean default false / timestamptz | o momento do recebimento é o `created_at` |
| tentativas | int, default 0 | incrementado a cada falha de processamento |
| falha_permanente | boolean, default false | marcado após N tentativas, para investigação manual |
| colunas obrigatórias | §7.2 | |

Saíram `origem`, `etapa`, `tipo_origem` e `visitante_id`: com só o Meta passando pelo buffer, eles são sempre os mesmos ou não existem.

### `lead_consentimentos`

Registro de evidência do consentimento, exigido pela [LGPD](LGPD.md) (8.1) e pelo requisito RC03 dos [Requisitos](Requisitos.md). É só de inclusão: uma linha por aceite ou revogação, nunca alterada. `leads.consentimento_marketing` guarda só o estado atual.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | PK / not null, indexado |
| lead_id | uuid | FK para `leads.id` |
| finalidade | varchar | `marketing` nesta fase. Coluna mantida para outras finalidades |
| aceito | boolean | `true` no aceite, `false` na revogação (descadastro) |
| registrado_em | timestamptz | `aceitoEm` do Landing, em UTC, ou o momento do descadastro |
| ip | varchar(45), nullable | só no descadastro, que é nosso. O Landing não informa o IP do aceite [Pendente · Grupo 7]. Zerado na anonimização (4.3) |
| user_agent | varchar(500), nullable | só no descadastro, como o IP. Zerado na anonimização (4.3) |
| texto_termo **[Novo]** | text, nullable | texto do consentimento exibido no aceite (`consentimento.texto` do envio). Substitui `versao_termo`: o Landing não versiona o termo, guarda o texto (D8) |
| origem | varchar | `landing.formulario.recebido`, `landing.chatbot.lead_capturado` ou `descadastro` |
| formulario_id | uuid, nullable | formulário de origem do aceite. Sem FK. Saiu `formulario_versao`, que o Landing não tem |
| envio_id / sessao_id | uuid, nullable | `envioId` ou `sessaoId` do Landing |
| evento_id | uuid, nullable | `id` do evento que trouxe o aceite, para auditoria. Nulo no descadastro |
| colunas obrigatórias | §7.2 | |

> **Quando grava:** no `landing.chatbot.lead_capturado` (aceite e data vêm no evento) e na leitura do envio de `landing.formulario.recebido` (aceite, data e texto). Uma linha nova só entra quando `aceito`, `registrado_em` ou `texto_termo` diferem da última linha do lead, para a captura do chatbot e o envio da mesma conversa não duplicarem o registro. Na mesma transação, atualiza `leads.consentimento_marketing` e `leads.consentimento_em`. O descadastro (seção 6) grava a revogação com o IP e o *User-Agent* da requisição.
>
> **Onde fica a evidência completa:** o envio no Landing guarda o texto exibido e a data do aceite (`ConsentimentoRegistrado` no `landing.yaml`), por até 24 meses. Para a evidência completa do RC03 faltam o IP e o *User-Agent* do aceite, que pedimos ao Landing (seção 11).

### `lead_comunicacoes`

Histórico dos e-mails enviados ao lead: boas-vindas (4.5) e aquecimento (4.2). Nesta fase só por e-mail.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id / lead_id | uuid | `lead_id` é FK para `leads` |
| canal | varchar | `email`. Coluna mantida para a entrada futura de outros canais |
| modelo_id | uuid | FK para `modelos_mensagem.id` (antes `template_id`) |
| status | varchar | `enviado`, `entregue`, `aberto`, `clicado`, `falhou`. Aberto e clicado exigem rastreio (seção 11) |
| enviado_em | timestamptz | |
| colunas obrigatórias | §7.2 | |

> **Saíram `formularios` e `formulario_versoes`.** O formulário é do Landing, que não versiona: a definição atual se lê no resumo do formulário, e o que cada lead respondeu fica copiado em `leads.dados_formulario`, com o rótulo de cada campo na época do envio (9.5).

### `canais_anuncio` (antes `marketing_settings`)

Preferências de referência por canal de anúncio. Não substitui as ferramentas de Ads: uma pessoa lê e configura manualmente no Ads Manager.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | UNIQUE (tenant_id, canal) |
| canal | varchar | `google_ads`, `meta_ads` |
| orcamento_mensal | numeric(15,2), nullable | dinheiro tem tipo fixo (§8.7). Mais de 2 casas responde `400 PRECISAO_EXCEDIDA`. Base do custo estimado por lead no painel (9.1) |
| configuracao | jsonb | `{publicoAlvo, objetivo}`. O `mapeamentoCampos` saiu: as perguntas do formulário instantâneo do Meta pontuam por regras próprias (`fonte = campo_meta`, 9.3) |
| meta_app_secret / meta_verify_token | varchar cifrado, nullable | só na linha `meta_ads`. Validam o webhook do tenant (seção 6). A API recebe o valor, mas nunca o devolve. Proposta a confirmar |
| meta_page_id / meta_page_access_token | varchar / varchar cifrado, nullable | só na linha `meta_ads`. O webhook do Meta traz só o `leadgen_id`; os dados do lead vêm da Graph API com o token da página (4.1). O token nunca é devolvido pela API |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

### `modelos_mensagem` (antes `mensagem_templates`)

Modelos de e-mail: a confirmação de boas-vindas (4.5) e a cadência de aquecimento das etapas frias (0/1, job 4.2).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| tipo | varchar | `boas_vindas` ou `aquecimento`. Só um modelo `boas_vindas` ativo por tenant |
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

Configuração do ciclo de vida do aquecimento, uma linha por tenant.

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | `tenant_id` UNIQUE |
| max_tentativas_aquecimento | int | depois desse número de toques sem resposta, o lead vira `esgotado` |
| meses_retencao_pos_esgotamento | int | meses depois de `esgotado_em` até anonimizar |
| meses_retencao_pos_optout | int | meses depois de `opt_out_em` até anonimizar |
| colunas obrigatórias | §7.2 | |

### `regras_pontuacao` [Etapa 2]

Critérios da pontuação do lead (9.3), por tenant. A pessoa gestora monta as regras a partir da matriz das [Regras de Negócio](Regras%20de%20Negocio.md) (7.1).

| Coluna | Tipo | Observação |
|---|---|---|
| id / tenant_id | uuid | |
| criterio | varchar | nome amigável: "Cargo", "Orçamento", "Prazo de compra", "Origem do tráfego". Cada critério conta uma vez por lead: se duas regras dele baterem, vale a de mais pontos |
| fonte | varchar | `campo_formulario`, `campo_meta` ou `utm` |
| formulario_id / campo_id | uuid, nullable | só em `campo_formulario`: o formulário e o campo do Landing, pelos ids do resumo do formulário. Sem FK. **Index (tenant_id, formulario_id)** |
| campo_meta | varchar, nullable | só em `campo_meta`: o nome da pergunta do formulário instantâneo do Meta |
| valores | jsonb | respostas que disparam a regra, ex.: `["Decisor", "Sócio", "Gerente"]`. Em `utm`: `[{"source": "google", "medium": "cpc"}, {"direto": true}]` |
| pontos | int | pode ser 0 |
| ativo | boolean | |
| colunas obrigatórias | §7.2 | |

Um `CHECK` garante que cada fonte preencha só o próprio alvo. Os campos não têm chave estável entre formulários do Landing, então a regra de campo vale para um formulário: quem tem dois formulários com a pergunta "Cargo" cria uma regra para cada.

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

### `eventos_processados` [Regra da infra]

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

### 4.1 Leads do Meta Ads

- **Frequência:** a cada 5 minutos (`@Scheduled`), em lotes de até 500 linhas por execução
- **Concorrência segura:** `SELECT ... FOR UPDATE SKIP LOCKED` ao puxar linhas não processadas, para evitar processamento duplicado entre execuções sobrepostas ou várias instâncias
- **Dados do lead:** o webhook traz só o `leadgen_id`. O job busca os campos em `GET https://graph.facebook.com/{versão}/{leadgen_id}` com o `meta_page_access_token` do tenant, com timeout de 3 s. Uma falha conta como tentativa
- **Resolução de identidade**, sempre dentro do mesmo tenant:
  1. `meta_lead_id` já existe em `leads`? É reentrega: atualiza, não duplica
  2. Senão, normaliza e-mail (minúsculas, sem espaços) e telefone (só dígitos, em E.164) e procura em `leads`. Se achar, faz merge sem sobrescrever dado já preenchido com dado mais pobre. Se não achar, cria um lead novo na etapa 0
- **Pontuação [Etapa 2]:** depois de gravar, calcula a pontuação com as regras `campo_meta` e `utm` (9.3)
- **Regra de não-retrocesso:** um lead que já está numa etapa maior continua nela; o Meta só completa dados e registra o evento
- **Falha e retry:** a falha incrementa `tentativas`; depois de N tentativas, marca `falha_permanente` para investigação manual, sem travar o resto do lote

### 4.2 Comunicação de aquecimento

Roda diariamente. Envia a cadência de `modelos_mensagem` com `tipo = 'aquecimento'` por e-mail para leads frios (etapa 0/1) que ainda não esgotaram as tentativas.

1. Seleciona leads com `etapa IN (0,1)`, `status_aquecimento = 'ativo'`, `consentimento_marketing = true`, e-mail preenchido e `now() - ultima_comunicacao_em >= dias_apos_anterior` do próximo modelo da sequência (posição `tentativas_aquecimento + 1`)
2. **Confere `status_aquecimento != 'opt_out'` antes de enviar.** É obrigatório pela seção 64 do Prompt Mestre (opt-out de marketing)
3. Envia o modelo correspondente, sempre com o link de descadastro `/public/marketing/descadastro/{token_descadastro}`. Grava em `lead_comunicacoes` com o `modelo_id`, incrementa `tentativas_aquecimento` e atualiza `ultima_comunicacao_em`
4. Se não há próximo modelo na sequência daquela etapa, ou se `tentativas_aquecimento` chegou a `ciclo_vida_configuracoes.max_tentativas_aquecimento`, marca `status_aquecimento = 'esgotado'` e grava `esgotado_em = now()`

> **Quem envia o e-mail:** o próprio módulo, tanto o aquecimento quanto a confirmação de boas-vindas (4.5). A mensagem `identity.email.enviar` da plataforma aceita *só e-mail transacional*; campanhas e listas são do Marketing (AsyncAPI do identity e Mapa §3). Em desenvolvimento o destino é o Mailpit (`mailpit:1025`). Hoje o `docker-compose.yml` da plataforma não entrega variáveis de SMTP ao serviço `marketing`, e isso está registrado como pendência com o Grupo 2.

O limite de tentativas é configurado por tenant em `ciclo_vida_configuracoes`, não fixado no código. WhatsApp fica fora desta fase. Na etapa 1, o aquecimento serve para trazer de volta quem deixou o contato no chatbot e não concluiu.

### 4.3 Anonimização por retenção (LGPD)

Roda mensalmente. Não apaga a linha do lead, porque isso quebraria o histórico do funil e as métricas de conversão do painel. Remove só o dado pessoal identificável e preserva o valor estatístico.

1. Seleciona leads com `anonimizado = false` e (`status_aquecimento = 'esgotado'` com `now() - esgotado_em >= meses_retencao_pos_esgotamento`) ou (`status_aquecimento = 'opt_out'` com `now() - opt_out_em >= meses_retencao_pos_optout`)
2. Zera `nome`, `email`, `telefone`, `whatsapp`, `token_descadastro` e `dados_formulario`, e também `ip` e `user_agent` das linhas do lead em `lead_consentimentos`. Mantém `etapa`, `origem`, `canal`, `utm_*`, `pontuacao`, `faixa_qualificacao`, datas e contadores, e no registro de consentimento a finalidade, o aceite, a data e o texto do termo
3. Marca `anonimizado = true` e registra evento em `lead_eventos`

Os dois prazos de retenção são configurados por tenant em `ciclo_vida_configuracoes`, conforme a seção 83 do Prompt Mestre (LGPD). A anonimização do envio no Landing (até 24 meses) é do Landing e não depende desta.

### 4.4 Atribuição pendente e transbordo [Etapa 2]

Roda a cada minuto, só para tenants com `rodizio_ativo = true`.

1. **Atribuição pendente:** leads MQL sem vendedor, que chegaram fora do horário comercial, recebem vendedor pelo rodízio (9.3) no início do próximo horário de atendimento. Se o lead já tem oportunidade, o vendedor vai para o CRM como responsável (9.6)
2. **Transbordo [Pendente · Grupo 6]:** se o vendedor não fez o primeiro contato em `minutos_transbordo`, o lead passa ao próximo vendedor do rodízio. Quem registra o primeiro contato é o CRM; sem essa informação, este passo fica desligado (seção 11)

### 4.5 Confirmação de boas-vindas

Atende o RF06 e o RN01 dos [Requisitos](Requisitos.md): o lead recebe uma mensagem automática de confirmação nos primeiros minutos. Roda a cada minuto, então o e-mail sai em até 1 ou 2 minutos depois do fato.

1. **Quem recebe:** lead com e-mail, `boas_vindas_enviada_em` nulo, `anonimizado = false`, `status_aquecimento != 'opt_out'` e que **enviou o formulário (etapa 3)**. Recebe mesmo sem `consentimento_marketing`, porque o e-mail só confirma o pedido que a própria pessoa fez
2. **O que envia:** o modelo `boas_vindas` ativo do tenant (`modelos_mensagem`), com as variáveis do lead e o link de descadastro `/public/marketing/descadastro/{token_descadastro}`. Sai uma vez por lead
3. **O que grava:** `lead_comunicacoes` com o `modelo_id`, `boas_vindas_enviada_em = now()` e `comunicacao_enviada` em `lead_eventos`. Não conta em `tentativas_aquecimento`
4. **Falha:** grava `lead_comunicacoes` com `status = 'falhou'` e tenta de novo na execução seguinte. Depois de 3 falhas do lead, desiste e o lead aparece com a confirmação pendente no detalhe (9.2)

Lead do Meta Ads e captura do chatbot não recebem: não houve envio a confirmar. O segundo gatilho da revisão anterior (MQL antes de enviar, na etapa 2) volta quando a etapa 2 tiver fonte. O horário comercial não se aplica: a janela de não perturbe do RN03 vale só para WhatsApp.

### 4.6 Leitura pendente do envio [Novo]

Roda a cada minuto. Pega leads com `envio_id` preenchido e `envio_lido_em` nulo, ou seja, que chegaram enquanto o Landing não respondeu (seção 5, passo 3.3).

1. Lê `GET /api/landing/envios/{envioId}` com token de serviço, timeout de 3 s
2. Grava contato, respostas, origem e consentimento, como no consumo do evento, e marca `envio_lido_em`
3. Calcula a pontuação e, se o lead virou MQL sem vendedor, atribui pelo rodízio
4. Depois de 10 falhas seguidas do mesmo lead, para de tentar e o detalhe mostra "respostas indisponíveis no Landing"

---

## 5. Fluxo de escrita

1. **Etapa 0, Meta Ads:** o webhook grava em `leads_entrada_buffer`, com o tenant resolvido pelo slug do caminho e a assinatura validada (seção 6). O job 4.1 busca os dados na Graph API, faz upsert em `leads` e grava `lead_eventos`
2. **Etapa 1, chatbot:** `landing.chatbot.lead_capturado` chega na fila `marketing.landing-leads` e é processado na hora:
   1. procura o lead por `sessao_id`; senão, por e-mail ou telefone normalizados; senão, cria um lead novo (`origem = landing_page`, `canal = chatbot`);
   2. grava contato, `formulario_id`, UTM do evento e o consentimento, e sobe para a etapa 1 se o lead estava abaixo;
   3. calcula a pontuação com as regras de `utm` (ainda não há respostas) e registra em `lead_eventos`
3. **Etapa 3, envio:** `landing.formulario.recebido` chega na mesma fila e é processado na hora:
   1. procura o lead por `envio_id` (pode já existir, se `crm.oportunidade.criada` chegou antes); senão, por `sessao_id`, quando o Landing passar a mandar no evento [Pendente · Grupo 7]; senão, por `crm_contato_id`; senão, cria um lead novo;
   2. grava `envio_id`, `formulario_id`, `canal`, `crm_empresa_id` e `crm_contato_id` e sobe para a etapa 3;
   3. lê `GET /api/landing/envios/{envioId}`: contato, respostas (`dados_formulario`), origem (UTM e toques) e consentimento, e marca `envio_lido_em`. Se o Landing não responder em 3 s, o lead fica gravado com a leitura pendente, e o job 4.6 tenta de novo;
   4. com as respostas lidas, faz o merge com outro lead de mesmo e-mail ou telefone, se houver (o mais antigo fica, o outro é marcado como excluído e a timeline registra a junção);
   5. calcula a pontuação e a faixa e, se for MQL sem vendedor, atribui pelo rodízio (9.3);
   6. registra em `lead_eventos`, notifica o vendedor (seção 8) e envia o evento SSE do painel.

   A confirmação de boas-vindas sai pelo job 4.5, em até 1 ou 2 minutos
4. **Oportunidade:** `crm.oportunidade.criada` chega na fila `marketing.crm-oportunidades`. Só interessa a de `origem = landing`:
   1. procura o lead por `envio_id = origemModuloId` [Pendente · Grupo 7]; senão, por `crm_contato_id`;
   2. se não achar, o evento chegou antes do envio: cria o lead na etapa 3 com `envio_id` e os ids do CRM, e o `landing.formulario.recebido` completa depois;
   3. grava `crm_oportunidade_id` e `handoff_crm` em `lead_eventos`;
   4. se o lead já tem vendedor, grava o vendedor como responsável da oportunidade no CRM [Pendente · Grupo 6]

**Consentimento:** nas etapas 1 e 3, o aceite é registrado em `lead_consentimentos` quando muda (seção 3): na etapa 1, com os dados do evento; na etapa 3, na leitura do envio.

Todo consumo é idempotente (`eventos_processados`) e a etapa nunca volta para trás, mesmo que os eventos cheguem fora de ordem. Depois do commit de cada passo, o módulo pode publicar evento em `marketing.eventos` e pedir notificação ou registro na timeline (seção 8).

---

## 6. Endpoints

### Superfície pública [Regra da infra]

Rotas sem usuário ficam sob `/public/marketing/**`. O gateway as libera de token e aplica rate limit por IP (hoje 120 por minuto). Nenhuma devolve dado de negócio (§12.6). O Marketing não tem rota de intake: os leads da landing chegam por evento do Landing (seção 8).

| Método | Rota | Autenticação | Quem chama |
|---|---|---|---|
| GET | `/public/marketing/{subdominio}/meta/leads` | handshake do Meta: confere `hub.verify_token` com `meta_verify_token` do tenant e devolve `hub.challenge` | Meta (servidor) |
| POST | `/public/marketing/{subdominio}/meta/leads` | HMAC `X-Hub-Signature-256` calculado com o `meta_app_secret` do tenant | Meta (servidor) |
| GET / POST | `/public/marketing/descadastro/{token}` | token opaco do lead | Destinatário do e-mail |

> **Como o tenant do webhook é resolvido.** O slug do caminho é o subdomínio do tenant. O módulo pergunta à plataforma a qual tenant ele pertence, em `GET /api/identity/tenants/resolver?subdominio=`, com o próprio token de serviço. A permissão `identity.tenant.ver` já foi concedida ao marketing em `permissoes/identity.yaml`. Subdomínio desconhecido responde `404`.
>
> Trocar o slug não dá acesso a outro tenant: a assinatura HMAC só confere com o App Secret daquele tenant. A chamada ao identity segue o timeout de 3 s (§9.6); a resposta pode ficar em cache curto.
>
> O corpo do webhook traz só `leadgen_id`, `page_id`, `form_id` e `ad_id`. O webhook grava isso no buffer e responde na hora; os campos do lead são buscados depois (4.1).

> **Descadastro:** o `GET` mostra a confirmação e o `POST` efetiva o opt-out, gravando `status_aquecimento = 'opt_out'`, `opt_out_em` e `consentimento_marketing = false`, e registrando em `lead_eventos` e a revogação em `lead_consentimentos`, com o IP e o *User-Agent* da requisição. O opt-out não acontece no `GET` porque leitores de e-mail costumam abrir links automaticamente. Token inválido responde a mesma página neutra, para não revelar se o lead existe.

### Superfície autenticada

Token JWT no cabeçalho, validado localmente pelo JWKS do identity (§4.2). Permissão verificada no back-end com `@PreAuthorize` (§5.2). Tenant sempre vindo do claim `tenant_id`; um `tenantId` no corpo ou na query é ignorado (§6).

| Método | Rota | Permissão | Observação |
|---|---|---|---|
| GET | `/api/marketing/leads` | `marketing.lead.ver` | paginada: `pagina`, `tamanho` (máx. 100), `ordenar`. Filtros **[Etapa 2]**: `etapa`, `faixa`, `origem`, `canal`, `campanha`, `vendedorId`, `periodo`, `q` |
| GET | `/api/marketing/leads/{id}` | `marketing.lead.ver` | lead de outro tenant responde 404. Traz respostas, pontuação com o detalhe, comunicações e timeline (9.2) |
| GET | `/api/marketing/leads/{id}/resumo` **[Etapa 2]** | `marketing.lead.ver_resumo` | para outros módulos (9.6) |
| GET | `/api/marketing/leads/resumo?ids=` **[Etapa 2]** | `marketing.lead.ver_resumo` | vários resumos de uma vez, até 100 ids (§9.4) |
| PUT | `/api/marketing/leads/{id}/vendedor` **[Etapa 2]** | `marketing.qualificacao.editar` | reatribuição manual do vendedor |
| GET | `/api/marketing/busca?q=` | `marketing.lead.ver` | busca global (§8.6) |
| GET | `/api/marketing/painel` | `marketing.dashboard.ver` | antes `/dashboard` |
| GET | `/api/marketing/painel/eventos` (SSE) | `marketing.dashboard.ver` | antes `/stream`; ver 9.1 |
| GET / PUT | `/api/marketing/canais-anuncio` | `marketing.configuracao.ver` / `.editar` | antes `/settings` |
| GET / POST | `/api/marketing/modelos-mensagem` | `marketing.template.ver` / `.criar` | antes `/templates`; lista paginada |
| PUT / DELETE | `/api/marketing/modelos-mensagem/{id}` | `marketing.template.editar` / `.excluir` | DELETE é soft delete |
| GET / PUT | `/api/marketing/ciclo-vida` | `marketing.automacao.ver` / `.editar` | antes `/lifecycle-settings` |
| GET / PUT | `/api/marketing/qualificacao` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | faixas, horário comercial, rodízio ligado e transbordo |
| GET | `/api/marketing/qualificacao/formularios` **[Etapa 2]** **[Novo]** | `marketing.qualificacao.ver` | formulários do Landing que já trouxeram lead, com nome e campos lidos no resumo do formulário. Serve para escolher o campo de uma regra (9.4) |
| GET / POST | `/api/marketing/regras-pontuacao` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | |
| PUT / DELETE | `/api/marketing/regras-pontuacao/{id}` **[Etapa 2]** | `marketing.qualificacao.editar` | DELETE é soft delete |
| GET / PUT | `/api/marketing/rodizio/vendedores` **[Etapa 2]** | `marketing.qualificacao.ver` / `.editar` | quem está na escala |
| GET | `/api/marketing/health` | nenhuma | `200` com o serviço de pé e conectado ao banco (§8.5) |

Saíram `GET/POST /api/marketing/formularios` e `GET/PUT /api/marketing/formularios/{id}`: o formulário é do Landing.

### Busca global

Todo módulo com registro que o usuário procura pelo nome expõe `GET /api/{modulo}/busca?q=` (§8.6): `q` com pelo menos 2 caracteres, no máximo 5 itens, sem paginação. Leads anonimizados não aparecem.

```
GET /api/marketing/busca?q=maria

→ 200 { "success": true,
        "data": [
          { "id": "9f1c4e2a-...",
            "titulo": "Maria Souza",
            "subtitulo": "Etapa 3 · landing_page",
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

### Chamadas a outros módulos

| Chamada | Para quê | Permissão |
|---|---|---|
| `GET http://landing:8088/api/landing/envios/{id}` | respostas, origem e consentimento do envio (seção 5) | `landing.envio.ver`, já com `servicos: [marketing]` |
| `GET http://landing:8088/api/landing/formularios/{id}/resumo` | nome e campos de um formulário, para a tela de regras (9.4) | `landing.formulario.ver_resumo`, já com `servicos: [marketing]` |
| `GET /api/identity/tenants/resolver?subdominio=` | tenant do webhook do Meta | `identity.tenant.ver`, já concedida |
| gravar o responsável de uma oportunidade no CRM | levar o vendedor do rodízio ao CRM (9.6) | **[Pendente · Grupo 6]** |

Todas com token de serviço e `X-Tenant-Id`, direto pelo nome do container, com o host vindo de variável de ambiente (`LANDING_BASE_URL`, `CRM_BASE_URL`, `IDENTITY_BASE_URL`) e timeout de 3 s (§9.1, §9.2, §9.6).

### Handoff para o CRM

| Passo | Quem faz |
|---|---|
| Criar ou reaproveitar a empresa (`POST /api/crm/empresas`, tratando o `409`) | Landing, antes de publicar `landing.formulario.recebido` |
| Criar ou reaproveitar o contato (`POST /api/crm/contatos`, tratando o `409`) | Landing, antes de publicar `landing.formulario.recebido` |
| Criar a oportunidade (`POST /api/crm/oportunidades`) | Landing, pelo Contrato §12.7. O CRM publica `crm.oportunidade.criada` |
| Ligar o lead à oportunidade | **Marketing**, ao consumir `crm.oportunidade.criada` (seção 5, passo 4) |
| Levar o vendedor do rodízio como responsável | **Marketing**, quando a oportunidade e o vendedor existirem [Pendente · Grupo 6] |

> **O que falta para fechar o handoff**
>
> - **[Pendente · Grupo 7]** O `landing.yaml` só cita a criação de empresa e contato; a da oportunidade está no §12.7 e na permissão do CRM. Pedimos ao Landing que confirme e use `origem: landing` e **`origemModuloId` igual ao `envioId`**, para o Marketing achar o lead certo.
> - **[Pendente · Grupo 6]** O CRM cria a oportunidade "sem responsável" quando quem chama é um serviço. Para o rodízio valer no CRM, o Marketing precisa gravar o `responsavelId`. Hoje só existe o `PUT /api/crm/oportunidades/{id}` completo (título, empresa, próximo passo...), que exigiria ler e regravar tudo. Pedimos uma ação só para o responsável (ex.: `PUT /api/crm/oportunidades/{id}/responsavel`) com `servicos: [marketing]`.
> - Pontuação e faixa não vão para a oportunidade: o CRM não tem esses campos. Quem quiser, lê o resumo do lead (9.6) pelo `origemModuloId`.

---

## 7. Permissões e menu

As listas entram por PR no `infra-integrador-2026` e o CI as valida contra os schemas JSON. Uma permissão que não está na lista nunca aparece no token. As duas abaixo **já estão no infra** (PR #29); as cópias em [`permissoes/`](permissoes/) e [`modulos/`](modulos/) precisam continuar iguais a esta seção.

### `permissoes/marketing.yaml`

Formato `modulo.recurso.acao` (§5.1). As permissões de leitura vão para ADMINISTRADOR, GESTOR e MARKETING; as de edição, para ADMINISTRADOR e MARKETING.

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
```

Saíram `marketing.formulario.ver` e `marketing.formulario.editar`. Nenhuma permissão declara `servicos` por enquanto; `marketing.lead.ver_resumo` ganha `servicos` quando um módulo pedir o resumo com token de serviço (9.6).

### Permissões de outros módulos que usamos

| Permissão | Para quê | Situação |
|---|---|---|
| `identity.tenant.ver` | resolver o tenant do webhook do Meta | já concedida (`servicos: [..., marketing, ...]`) |
| permissão de listar usuários do identity (código a confirmar) | listar quem tem perfil VENDEDOR, para a escala do rodízio e para mostrar o nome no painel (9.3) | a pedir **[Pendente · Grupo 2]** |
| `landing.envio.ver` | ler as respostas, a origem e o consentimento do envio, com token de serviço | já concedida (`servicos: [marketing]`) |
| `landing.formulario.ver_resumo` | ler nome e campos do formulário, com token de serviço | já concedida (`servicos: [marketing]`) |
| `crm.empresa.ver_resumo` | mostrar a razão social de um lead que já passou pelo handoff | já no perfil MARKETING |
| `crm.contato.ver_resumo` | mostrar o resumo do contato vinculado | já no perfil MARKETING |
| gravar o responsável da oportunidade (ação e permissão a definir pelo CRM) | levar o vendedor do rodízio ao CRM | a pedir **[Pendente · Grupo 6]** |

### `modulos/marketing.json`

Registro no menu da casca (§11). O `modulos/README.md` do infra reserva 60–69 ao Marketing, mas os registros publicados usam outra ordem (Chamados em 60, Landing em 80), e o nosso está em 50. Vale o que está publicado.

```json
{
  "codigo": "marketing",
  "nome": "Marketing",
  "grupo": "Grupo 4 — Marketing e Automações",
  "icone": "megaphone",
  "urlFrontend": "/modulos/marketing/",
  "prefixoApi": "/api/marketing",
  "permissaoMenu": "marketing.acessar",
  "ordemMenu": 50,
  "healthcheck": "/api/marketing/health",
  "itensSubmenu": [
    { "rota": "/painel",        "nome": "Painel",        "permissao": "marketing.dashboard.ver" },
    { "rota": "/leads",         "nome": "Leads",         "permissao": "marketing.lead.ver" },
    { "rota": "/configuracoes", "nome": "Configurações", "permissao": "marketing.configuracao.ver" }
  ]
}
```

Saiu o item "Formulários": os formulários ficam no menu do Landing. Esconder item de menu é usabilidade, não segurança: o back-end continua respondendo `403`.

---

## 8. Mensageria

Para **avisar** um fato, o módulo publica evento no RabbitMQ. Para **perguntar** ou pedir algo com resposta imediata, usa API (§9.7).

### Regras de publicação [Regra da infra]

- Publica só na exchange `marketing.eventos` (topic), com a chave de roteamento igual ao tipo do evento, e em `identity.entrada` para pedidos à plataforma
- O envelope é sempre o mesmo: `id` (uuid, chave de idempotência), `tipo` (`modulo.entidade.acao`, verbo no particípio), `versao`, `tenantId`, `moduloOrigem: "marketing"`, `ocorridoEm` em UTC, `usuarioId` (nulo em rotina), `correlacaoId` e `dados`
- Propriedade AMQP **`user_id = mq_marketing`** em toda mensagem. Sem ela, a plataforma manda o pedido para a `.dlq`
- Publica só depois do commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`)
- Sem senha, token, segredo ou dado pessoal além do necessário: mensagem fica em fila, em log e na `.dlq`
- Todo evento é descrito em `contratos/marketing.asyncapi.yaml` *antes* de ser publicado. Mudança incompatível sobe `versao`

### Eventos que publicamos [Etapa 2]

O contrato pede que só se publique o que algum módulo consome. Hoje nenhum evento nosso tem consumidor confirmado, e o `marketing.asyncapi.yaml` do infra está sem mensagens. Os candidatos:

| Tipo | Quando | Consumidor |
|---|---|---|
| `marketing.lead.qualificado` | lead entra numa faixa MQL ou muda de faixa | a confirmar (CRM, relatórios) |
| `marketing.lead.atribuido` | vendedor atribuído ou trocado | a confirmar (CRM) |
| `marketing.lead.descadastrado` | opt-out confirmado | a confirmar (CRM, pelo consentimento do contato; Landing) |

Saiu `marketing.formulario.publicado`, que não existe mais.

### Pedidos à plataforma (`identity.entrada`)

| Mensagem | Uso no marketing | Condição |
|---|---|---|
| `identity.notificacao.criar` | avisar o vendedor atribuído quando recebe um lead MQL, com a categoria `NOVO_LEAD` e `rota: "/leads/{id}"`. Lead na etapa 3 vai como prioritário | acesso do vendedor à tela do lead e destinatário quando não há vendedor estão em aberto (seção 11) |
| `identity.timeline.registrar` | registrar a ligação com a oportunidade na timeline da empresa | exige `empresaId`, que só existe depois do envio |
| `identity.email.enviar` | não se aplica ao aquecimento | só e-mail transacional (4.2) |

### Consumo

Cada fila tem a sua DLQ (`.dlq`), para mensagens que falharam três vezes. As filas e as ligações são declaradas pela própria API do Marketing (o `mq_marketing` configura `marketing.*` e lê qualquer `*.eventos`).

| Fila | Ligada a | Chaves |
|---|---|---|
| `marketing.landing-leads` | `landing.eventos` | `landing.formulario.recebido`, `landing.chatbot.lead_capturado` |
| `marketing.crm-oportunidades` **[Novo]** | `crm.eventos` | `crm.oportunidade.criada` |

| Evento | Dono | O que traz | Etapa | Processamento |
|---|---|---|---|---|
| `landing.chatbot.lead_capturado` | Landing (proposta) | `sessaoId`, `formularioId`, `paginaId`, contato (nome, e-mail, telefone), origem (UTM), consentimento (aceite e data) | 1 | na chegada (seção 5, passo 2) |
| `landing.formulario.recebido` | Landing | `envioId`, `formularioId`, `canal`, `empresaId`, `contatoId`. Contato, respostas, origem e consentimento vêm da leitura do envio | 3 | na chegada, com leitura do envio (seção 5, passo 3) |
| `crm.oportunidade.criada` | CRM | `oportunidadeId`, `empresaId`, `contatoId`, `responsavelId`, `origem`, `origemModuloId` | — | só `origem = landing`; liga o lead (seção 5, passo 4) |

Todo consumo é idempotente: grava o `id` em `eventos_processados` na mesma transação do efeito e ignora um `id` já visto. Os dados pessoais do chatbot viajam no evento porque o Landing os publica; não aparecem em log do Marketing.

---

## 9. Etapa 2 — Módulo integrado de marketing

É a Etapa 2 do projeto (seção 0): o módulo que a pessoa gestora de marketing usa dentro da casca, via `<iframe>`, e de onde as informações de lead saem para os outros módulos. Tem seis partes: painel (9.1), leads (9.2), qualificação e distribuição (9.3), configurações (9.4), formulários e respostas (9.5) e disponibilização (9.6).

> **Decisão de escopo:** a "camada intermediária" de configurações é **config pura** nesta fase. O cliente registra parâmetros e preferências por canal, sem integração real via OAuth ou API com Google Ads ou Meta Business. A única integração real é receber leads do Meta: o webhook e a busca de cada lead na Graph API, que o webhook não traz (4.1). A integração completa fica para a Fase 6 (Integrações Avançadas) do Prompt Mestre.

### Requisitos do iframe e da casca [Regra da infra]

- Funciona aberto direto, com token real de um usuário de teste (`marketing@empresa-a.dev`), e embutido, com o token recebido por `postMessage` (§12)
- Servido sob `/modulos/marketing/`: `base: '/modulos/marketing/'` no Vite e o mesmo prefixo no roteador. A API é chamada pelo caminho relativo `/api/marketing/`
- **Registra o ouvinte de `message` antes de enviar `modulo:pronto`** e guarda a sessão fora dos componentes (§12.2). Recomendação: copiar `exemplo-modulo/front/src/plataforma/sessao.ts`
- Confere `event.origin` (a própria origem) *e* `event.source === window.parent` em toda mensagem
- Envia `modulo:altura` a cada mudança de conteúdo, `modulo:navegar` com rota relativa (`/leads/9f1c`) e `modulo:token-expirado` ao receber `401`
- Token só em memória, nunca em `localStorage`
- O servidor do front envia `Content-Security-Policy: frame-ancestors 'self'` e nunca `X-Frame-Options: DENY` (§12.8)
- Tema: [Design System Centinela](DesignSystem/design-systemfinal.md) sobre Tailwind v4, com `lucide-react` e a fonte Inter. Ele substitui o shadcn/ui. Usar os tokens do Design System (`bg-brand-950`, `bg-brand-800`, `text-brand-300`...) em vez de hex e aplicar `data-tema` recebido da casca

> **Front independente da casca.** A integração com a casca está a cargo de outro grupo, e a decisão pode mudar (iframe, micro-frontend ou front único). Por isso o front do Marketing concentra tudo o que depende da casca numa camada só, `plataforma/`: sessão e token, navegação, altura, tema e aviso de token expirado. As telas não falam com `window.parent` nem com `postMessage`; usam essa camada. Caminho base e endereço da API vêm de configuração (`import.meta.env`), não fixos no código. Cada área (painel, leads, configurações) fica em `src/features/<area>/`, com as próprias rotas, e pode ser montada em outro app sem reescrita. Sozinho, o módulo abre com um layout de desenvolvimento e login de teste; embutido, sem sidebar nem header, que são da casca.

### 9.1 Painel

O painel reúne as informações de lead do tenant: funil, qualificação, campanhas, custo e distribuição. São as mesmas informações que a 9.6 disponibiliza para outros módulos.

**Fonte de dados inicial:** `GET /api/marketing/painel?periodo=&origem=&campanha=`, um único endpoint com tudo o que a primeira renderização precisa.

```json
{
  "success": true,
  "data": {
    "funil": { "etapa0": 120, "etapa1": 40, "etapa2": 0, "etapa3": 6 },
    "qualificacao": { "mqlQuente": 5, "mqlMorno": 9, "nutricao": 22, "semPontuacao": 145 },
    "serieTemporal": [
      { "data": "2026-09-01", "novosLeads": 8, "origem": "meta_ads" },
      { "data": "2026-09-01", "novosLeads": 3, "origem": "landing_page" }
    ],
    "taxaConversao": {
      "etapa1ParaEtapa3": 15.0,
      "leadsParaMql": 22.2222
    },
    "campanhas": [
      { "utmCampaign": "black-friday-2026", "canal": "meta_ads", "leads": 40, "mql": 9, "etapa3": 3 }
    ],
    "canais": [
      { "canal": "meta_ads", "orcamentoMensal": 1500.00, "leads": 40, "mql": 9, "custoPorLead": 37.50, "custoPorMql": 166.67 }
    ],
    "distribuicao": {
      "porVendedor": [ { "vendedorId": "2d4f6a8c-...", "nome": "Carlos Lima", "atribuidos": 6 } ],
      "aguardandoVendedor": 2
    },
    "leadsRecentes": [
      { "id": "...", "nome": "...", "etapa": 3, "faixa": "mql_morno", "origem": "landing_page", "canal": "embed", "criadoEm": "..." }
    ]
  },
  "message": null,
  "errors": []
}
```

> **Percentual em pontos [Regra da infra]:** `22.2222` significa 22,22%, com até 4 casas (§8.7).

> **Custo por lead é estimativa.** `custoPorLead` e `custoPorMql` dividem o orçamento mensal informado em `canais_anuncio` pelos leads e MQL do canal no mês. Sem integração com o Ads Manager (decisão de escopo acima), o gasto real não chega ao módulo. ROI e ROAS precisam do valor do contrato (seção 11).

> **Visitas e conversão da página ficam no Landing.** Saíram `visitantes` e `visitantesParaLeads`: o Marketing não recebe mais as visitas. Quem quer a conversão da página abre a tela Métricas do Landing (`landing.metrica.ver`). O painel do Marketing começa no lead. A etapa 2 aparece zerada até ter fonte (seção 2).

Filtros: `periodo` (7/30/90 dias ou intervalo customizado), `origem` (`meta_ads`, `landing_page` ou todas) e `campanha` (opcional, via `utm_campaign`).

**Atualização em tempo real via SSE** (`GET /api/marketing/painel/eventos`). O servidor envia eventos pontuais, não o resumo inteiro recalculado:

```
id: 10482
event: lead_criado
data: {"leadId": "...", "etapa": 0, "origem": "meta_ads"}

id: 10483
event: lead_etapa_alterada
data: {"leadId": "...", "etapaAnterior": 1, "etapaNova": 3}

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
- Campanhas: leads, MQL e etapa 3 por `utm_campaign`
- Canais: orçamento informado e custo estimado por lead e por MQL
- Distribuição: leads por vendedor e quantos aguardam vendedor
- Série temporal de novos leads, filtrável por origem
- Conversão da captura no chatbot para o envio (etapa 1 → 3)
- Lista de leads recentes, atualizada via SSE, com a marca "no CRM" e o link da oportunidade para quem já foi ligado a ela (9.6)
- Taxa de abertura e clique dos e-mails, quando houver rastreio (seção 11)

### 9.2 Leads

A consulta de leads é do Marketing. O CRM só passa a ter o lead quando ele vira empresa, contato e oportunidade, na etapa 3. Até lá, e mesmo depois, quem quer entender de onde o lead veio e como ele se comportou procura aqui.

**Lista:** `GET /api/marketing/leads`

- Colunas: nome, etapa, faixa de qualificação, pontuação, origem e canal, campanha, vendedor e data de entrada
- Filtros: etapa, faixa, origem, canal, campanha, vendedor, período e busca por nome ou e-mail
- Leads anonimizados aparecem sem dados pessoais, para as contagens baterem com o painel

**Detalhe:** `GET /api/marketing/leads/{id}`

- Contato, consentimento e origem (UTM, página de entrada, primeiro e último toque). O consentimento mostra o histórico de `lead_consentimentos`: aceite ou revogação, data, texto do termo e formulário. IP e *User-Agent* aparecem só aqui, nunca no resumo (9.6)
- Confirmação de boas-vindas: enviada, pendente ou com falha (4.5)
- Respostas do formulário, como foram lidas do Landing (rótulo e valor), com o nome do formulário e o canal. Leitura pendente aparece como "aguardando o Landing" (4.6)
- Pontuação com o detalhe: quais regras somaram quantos pontos (9.3)
- Vendedor atribuído, com a ação de reatribuir (`marketing.qualificacao.editar`)
- Timeline (`lead_eventos`) e e-mails de aquecimento enviados
- Depois da etapa 3: resumo da empresa e do contato no CRM (`crm.empresa.ver_resumo`, `crm.contato.ver_resumo`) e o link da oportunidade

**Busca global:** `GET /api/marketing/busca?q=` faz o lead aparecer na barra de busca da casca (seção 6).

### 9.3 Qualificação e distribuição

As regras de negócio completas estão em [Regras de Negócio](Regras%20de%20Negocio.md) (7.1 e 7.2). Aqui fica como elas viram dado e processamento no Marketing.

**Pontuação (lead scoring)**

- **Quando calcula:** na etapa 1, só com a origem (UTM); na etapa 3, com as respostas do envio e a origem; e no lead do Meta, com as respostas do formulário instantâneo e a origem
- **Como calcula:** soma os `pontos` de cada regra ativa de `regras_pontuacao` que bate:
  - `campo_formulario`: só as regras do `formulario_id` do lead; compara a resposta de mesmo `campoId` com `valores`
  - `campo_meta`: compara a resposta da pergunta de nome `campo_meta` com `valores`
  - `utm`: compara a UTM do lead
  - cada critério conta uma vez, pela regra de mais pontos
- **Faixa:** `pontuacao >= limite_mql_quente` é `mql_quente`; `>= limite_mql_morno` é `mql_morno`; abaixo disso, `nutricao`
- **O que grava:** `pontuacao`, `faixa_qualificacao`, `pontuacao_detalhe` e `qualificado_em`. Quando a faixa muda, registra `qualificacao_alterada` em `lead_eventos` e envia `lead_qualificado` pelo SSE

**Matriz inicial** (Regras de Negócio, 7.1):

| Critério | Fonte | Respostas | Pontos |
|---|---|---|---|
| Cargo | campo do formulário | Decisor, Sócio, Gerente | 30 |
| Cargo | campo do formulário | Analista, Operacional, Outros | 10 |
| Orçamento | campo do formulário | as opções acima do ticket mínimo, marcadas pela pessoa gestora | 30 |
| Prazo de compra | campo do formulário | Imediato, Até 30 dias | 25 |
| Prazo de compra | campo do formulário | Mais de 3 meses, Apenas pesquisando | 5 |
| Origem do tráfego | `utm` | fundo de funil: `google` + `cpc` (Search) ou sem UTM (direto) | 15 |
| Origem do tráfego | `utm` | topo de funil: display e redes sociais | 5 |

Faixas iniciais: 70 ou mais é MQL quente; de 40 a 69, MQL morno; abaixo de 40, nutrição. As regras de `utm` entram prontas em cada tenant. As de campo dependem dos formulários que o tenant criou no Landing: a tela de regras (9.4) sugere a matriz e a pessoa gestora escolhe, para cada formulário, o campo que corresponde a "Cargo", "Orçamento" e "Prazo de compra". O mesmo vale para as perguntas do formulário instantâneo do Meta.

**Distribuição (rodízio)**

- **Quem entra:** lead que vira MQL, quente ou morno, e tem contato. Nutrição não entra no rodízio
- **Como escolhe:** entre os vendedores ativos em `vendedores_rodizio`, o que recebeu lead há mais tempo (`ultima_atribuicao_em` mais antiga). Grava `vendedor_id` e `vendedor_atribuido_em`, registra `vendedor_atribuido` em `lead_eventos` e notifica o vendedor (seção 8). MQL quente e morno entram no mesmo rodízio; a notificação do quente vai como prioritária
- **No CRM [Pendente · Grupo 6]:** a oportunidade criada pelo Landing chega sem responsável. Quando o lead tem oportunidade e vendedor, o Marketing grava o vendedor como responsável (seção 6, handoff)
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

**Aba "Modelos de mensagem"**: `GET/POST/PUT/DELETE /api/marketing/modelos-mensagem`

- O modelo de boas-vindas no topo, separado da cadência: um por tenant, sempre ativo, editável e sem exclusão (4.5)
- Lista de modelos de aquecimento, indicando a etapa fria (0/1) de cada um e sua posição na cadência
- Editor com assunto e corpo, aceitando variáveis como `{{nome}}` e `{{origem}}`
- Pré-visualização do modelo com dados de exemplo antes de salvar, incluindo o rodapé com o link de descadastro
- Chave de ativo/inativo nos modelos de aquecimento: os inativos não entram no job 4.2

**Aba "Ciclo de vida do lead"**: `GET/PUT /api/marketing/ciclo-vida`

- Máximo de tentativas de aquecimento antes de marcar o lead como esgotado
- Meses de retenção após esgotamento e após opt-out, antes da anonimização automática
- Cada tenant define os próprios valores, sem padrão fixo no código, conforme a seção 83 do Prompt Mestre (LGPD)

**Aba "Qualificação e distribuição"** **[Etapa 2]**: `/api/marketing/qualificacao`, `/qualificacao/formularios`, `/regras-pontuacao` e `/rodizio/vendedores`

- Regras de pontuação: critério, fonte, alvo, respostas que pontuam e pontos. Adicionar, editar, desativar e remover
  - Em "campo do formulário", a pessoa escolhe o formulário e o campo numa lista dos formulários do Landing que já trouxeram lead (`/qualificacao/formularios`, montada com o resumo do formulário). Nos campos de opção, as respostas vêm das opções do campo
  - Em "pergunta do Meta", digita o nome da pergunta do formulário instantâneo
- Faixas: limites de MQL quente e MQL morno, com a prévia de quantos leads atuais cairiam em cada faixa
- Rodízio: ligar e desligar, vendedores na escala, horário comercial, dias de atendimento e minutos de transbordo

Criar e editar em modal com título, confirmação em modal pequeno e vermelho só para ação irreversível (regras de uso do tema).

### 9.5 Formulários e respostas

> **Decisão (outubro de 2026, contratos do infra PR #26 e #29): o Landing é o dono de formulários, campos e envios.** Criar, editar, ativar, embutir e configurar o chatbot de um formulário é no back-office do Landing. O Marketing não tem tela de formulários e não guarda cópia da definição. A proposta anterior, de o Marketing definir os campos, foi retirada do nosso contrato no infra.

**O que o Marketing lê do Landing:**

| O quê | Onde | Para quê |
|---|---|---|
| Respostas, origem e consentimento de um envio | `GET /api/landing/envios/{id}` | detalhe do lead (9.2), pontuação (9.3), UTM e consentimento (seção 3) |
| Nome, status e campos de um formulário | `GET /api/landing/formularios/{id}/resumo` | escolher o campo de uma regra de pontuação (9.4) e mostrar o nome do formulário |

Cada resposta do envio vem com `campoId`, `rotulo`, `tipo` e `valor`, na ordem dos campos, e um campo excluído depois do envio continua lá, com o rótulo da época. Por isso o Marketing não precisa da definição para mostrar as respostas: guarda a cópia em `leads.dados_formulario` e usa o resumo só na configuração.

**Tipos de campo do Landing:** `TEXTO`, `TEXTO_LONGO`, `EMAIL`, `TELEFONE`, `CNPJ`, `CPF`, `NUMERO`, `DATA`, `LISTA_OPCOES`, `MULTIPLA_ESCOLHA`, `CAIXA_MARCACAO`. Os de opção (`LISTA_OPCOES` e `MULTIPLA_ESCOLHA`) são os que melhor servem às regras de pontuação, porque os valores são fechados.

**Envio anonimizado no Landing:** volta com `anonimizado = true` e `valor` nulo. O Marketing não sobrescreve a cópia que já tem, que segue a retenção própria (4.3).

### 9.6 Disponibilização para outros módulos

As informações do painel não ficam presas na tela. O Marketing as oferece para quem precisar, por três caminhos:

| Caminho | O que entrega | Para quem |
|---|---|---|
| API `GET /api/marketing/leads/{id}/resumo` e `/leads/resumo?ids=` | origem, UTM, etapa, faixa, pontuação, vendedor e oportunidade de um lead | módulos com token de serviço (`servicos` em `marketing.lead.ver_resumo`) e usuários com essa permissão |
| Eventos em `marketing.eventos` | lead qualificado, lead atribuído, lead descadastrado | quem ligar uma fila a eles, quando houver consumidor (seção 8) |
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

**Lead quente (etapa 3)**

Quem enviou o formulário só precisa assinar o contrato. Esse lead não espera lote: é processado na chegada de `landing.formulario.recebido` (seção 5, passo 3), com as respostas lidas, a pontuação e o vendedor do rodízio.

- **No CRM:** a oportunidade é criada pelo Landing. O Marketing a liga ao lead pelo `crm.oportunidade.criada` e leva o vendedor como responsável (seção 6)
- **Continua no Marketing:** o lead aparece no painel e na lista com a marca "no CRM" e o link da oportunidade, e entra em todas as métricas
- **Não trava:** se a leitura do envio falhar, o lead fica gravado e o job 4.6 completa depois. A ordem dos eventos do Landing e do CRM não importa (seção 5, passo 4)
- **Avisa na hora:** notificação prioritária ao vendedor atribuído (seção 8)

---

## 10. Testes e conformidade

O módulo está integrado quando passa nos 19 itens do checklist (§15), verificados com o Grupo 2 numa sessão de 20 minutos. A lista vai na descrição do PR que registra o módulo em `modulos/`.

| Itens | Como o marketing cumpre |
|---|---|
| 1, 2, 14, 16 | sobe no compose na porta 8087, com as variáveis de ambiente da plataforma; `/api/marketing/health` sem token; imagem publicada pelo workflow reutilizável `publicar-imagem.yml`; Flyway com `own_marketing`, app com `usr_marketing` |
| 3, 4, 19 | teste de 401 sem token, 403 com token sem permissão (ex.: `vendedor@empresa-a.dev`) e cabeçalho de 32 KB com token de ADMINISTRADOR |
| 5, 6 | lead criado na Empresa A e buscado com token da Empresa B responde 404; `tenantId` no corpo é ignorado. Vale também para o webhook: slug de um tenant com assinatura de outro é recusado |
| 7, 8 | sete colunas em todas as tabelas de negócio (seção 3); nenhuma FK nem query em outro schema |
| 9, 10, 13 | envelope em tudo, inclusive nas rotas públicas; listagens paginadas e ordenáveis; `contratos/marketing.yaml` refletindo a API |
| 11, 12 | front sozinho e no iframe sob `/modulos/marketing/`, com altura ajustando; `modulos/marketing.json` entregue |
| 15 | suíte com Testcontainers (PostgreSQL e RabbitMQ) e WireMock para Landing, CRM, identity e Graph API do Meta; os eventos do Landing e do CRM são publicados pelo próprio teste no RabbitMQ do Testcontainers; nenhum outro módulo no ar |
| 17, 18 | eventos com envelope e `user_id = mq_marketing`, descritos no AsyncAPI; consumidor testado com a mesma mensagem entregue duas vezes |

O `exemplo-modulo` já cobre os itens 2 a 6, 9, 10, 15 e 18 com testes automatizados. Copiá-los e adaptar as rotas é o caminho mais curto. Para desenvolver contra o Landing e o CRM antes de eles existirem, use um stub do Prism a partir de `contratos/landing.yaml` e `contratos/crm.yaml` (sem tirar o `-m false`). O identity nunca é stub: use a imagem real e os usuários de `docs/usuarios-de-teste.md`.

Além do checklist, a Etapa 2 precisa de testes próprios: a pontuação com a matriz inicial da 9.3 (um lead de cada faixa), só com as regras do formulário do envio; o rodízio com três leads MQL e dois vendedores ativos; a atribuição fora do horário comercial; e a ligação com a oportunidade com `crm.oportunidade.criada` chegando antes e depois de `landing.formulario.recebido`.

A Etapa 1 também ganha testes próprios: a captura do chatbot e o envio da mesma conversa viram um lead só; o Landing fora do ar deixa o lead com a leitura pendente, e o job 4.6 completa; a confirmação de boas-vindas sai uma vez só e não sai para quem se descadastrou (4.5); o mesmo consentimento entregue duas vezes gera uma linha só em `lead_consentimentos`, e o descadastro grava a revogação com IP e *User-Agent*.

### Entrega no `infra-integrador-2026`

- `contratos/marketing.yaml` (OpenAPI 3.1) e `contratos/marketing.asyncapi.yaml` (AsyncAPI 3.0): **já no infra**, versão 0.2.0, sem formulários. O AsyncAPI está sem mensagens até algum evento ter consumidor
- `permissoes/marketing.yaml` e `modulos/marketing.json` (seção 7): **já no infra**
- `IMAGEM_MARKETING` e `IMAGEM_MARKETING_FRONT` com a conta do grupo, em `.env.example`
- `contratos/marketing.views.md`: só se o Grupo 3 pedir *views* `vw_pub_*` para relatórios

---

## 11. Pendências e decisões em aberto

| Pendência | Com quem | Situação |
|---|---|---|
| `sessaoId` também em `landing.formulario.recebido`: o AsyncAPI do Landing diz que o envio do chatbot traz o mesmo `sessaoId`, mas o schema do evento não tem o campo. Sem ele, a captura e o envio só se ligam pelo `contatoId` ou pelo e-mail | Grupo 7 | A pedir ([`landing-requisitos.md`](contratos/landing-requisitos.md)) |
| Oportunidade criada pelo Landing (§12.7), com `origem: landing` e `origemModuloId = envioId`. O `landing.yaml` cita só empresa e contato | Grupo 7 | A confirmar |
| Fonte para a etapa 2: um evento quando a sessão passo a passo ou o chatbot passa de "grande parte" do formulário, com o critério documentado | Grupo 7 | A pedir |
| IP e *User-Agent* do aceite no consentimento do envio (RC03, LGPD 8.1). Hoje o Landing guarda só o texto e a data | Grupo 7 | A pedir |
| Listar os formulários do tenant com token de serviço, para a tela de regras não depender de já ter chegado lead. Hoje só o resumo por id aceita token de serviço | Grupo 7 | A pedir (dá para seguir sem) |
| `landing.chatbot.lead_capturado` está como "proposta" no contrato do Landing; o formato pode mudar | Grupo 7 | Acompanhar |
| O aceite do Landing cobre "o contato comercial do tenant dono do formulário". Vale como consentimento para os e-mails de aquecimento (4.2)? A leitura do Grupo 4 é que sim, porque o aquecimento é contato comercial do mesmo tenant | Grupo 4, professores | Proposta a confirmar |
| Gravar o responsável da oportunidade criada pelo Landing: uma ação só para o responsável, com `servicos: [marketing]` | Grupo 6 | A pedir |
| Fronteira entre `leads` (dados de quem ainda não é contato) e Contato do CRM (§10, regra do dono único) | Grupo 6 | A confirmar |
| Primeiro contato do vendedor, para o transbordo do rodízio (evento ou consulta no CRM) | Grupo 6 | A pedir |
| Disponibilidade do vendedor ("Online/Disponível"): se existir no CRM, como o Marketing consulta | Grupo 6 | A confirmar |
| Listar usuários com perfil VENDEDOR e seus nomes no identity (escala do rodízio e painel) | Grupo 2 | A pedir |
| SMTP para os e-mails de boas-vindas e de aquecimento: o compose não entrega variáveis de SMTP ao `marketing`, e o provedor fora do desenvolvimento depende dos professores (P6) | Grupo 2, professores | A pedir |
| SSE pelo gateway: a conexão longa sobrevive à regra de `504` em 3 s? | Grupo 2 | A confirmar |
| Rate limit de 120 req/min por IP em `/public/**` pode barrar rajadas do webhook do Meta | Grupo 2 | A confirmar |
| Handshake do Meta exige devolver `hub.challenge` em texto puro, fora do envelope; o §8.2 só prevê exceção para download de arquivo | Grupo 2 | Pedir exceção registrada |
| Confirmação de boas-vindas sem consentimento de marketing para quem enviou o formulário (4.5): a leitura é que ela confirma o pedido da própria pessoa e não é comunicação de marketing | Grupo 4, professores | Proposta a confirmar |
| Consentimento de marketing dos leads do Meta Ads (etapa 0) antes do aquecimento | Grupo 4 | A definir |
| Token de acesso da página do Meta: buscar os dados na Graph API é uma chamada externa, além do que a "config pura" previa. Confirmar também que o container pode sair para a internet | Grupo 4, Grupo 2 | A decidir |
| Onde guardar App Secret, verify token e token da página do Meta por tenant (proposta: colunas cifradas em `canais_anuncio`) | Grupo 4 | Proposta a confirmar |
| Acesso do vendedor ao lead notificado: dar `marketing.lead.ver` ao perfil VENDEDOR ou notificar com a rota da oportunidade no CRM | Grupo 4 | A definir |
| Quem recebe a notificação `NOVO_LEAD` quando não há vendedor (rodízio desligado ou fora do horário) | Grupo 4 | A definir |
| Consumidores de `marketing.lead.qualificado`, `marketing.lead.atribuido`, `marketing.lead.descadastrado` e do resumo do lead (9.6) | Interessados | A confirmar antes do AsyncAPI |
| Rastreio de aberto e clicado em `lead_comunicacoes` (pixel e redirecionamento públicos, §12.5) | Grupo 4 | A definir |
| Custo real e ROI/ROAS: hoje o custo por lead é estimado pelo orçamento informado; ROI precisa do valor do contrato | Grupo 4, professores | Evolução futura |
| Construtor visual de automação (60), gatilhos e ações (61–62) e campanhas de e-mail (63), atribuídos ao Grupo 4 pelo Mapa e não cobertos aqui | Grupo 4, professores | Lacuna de escopo a definir |
| WhatsApp e telefonia (65–67), sem dono no Mapa | — | Fora desta fase; evolução futura |
| Auditoria em `audit_logs` no próprio schema (proposta do Mapa §5, ainda não decidida) | Grupo 2 | Acompanhar |
| Ratificação do Contrato v0.7, em vigor desde 25/09: o Marketing ainda aparece sem manifestação | Gestores | A manifestar |
| Decisão D4 (CRM dono de Empresas e Contatos): falta a manifestação do Marketing | Gestores | A manifestar |

**Resolvidas nesta revisão:** campos do formulário (do Landing); quem cria a oportunidade (o Landing, pelo §12.7); se o CRM recebe o lead quente por API ou por evento (por API, chamada pelo Landing); `GET /api/landing/envios/{id}` e `servicos: [marketing]` em `landing.envio.ver` (já no contrato do Landing); retenção do `payload_bruto` do buffer (ele não guarda mais dado pessoal); critério da etapa 2 (virou o pedido de uma fonte para ela).

---

## 12. Registro de alterações

### 05/11/2026 · Landing dono dos formulários e oportunidade criada pelo Landing

Alinhamento aos contratos publicados no `infra-integrador-2026`: `landing.yaml` e `landing.asyncapi.yaml` (PR #26), o nosso `marketing.yaml` 0.2.0 sem formulários (PR #29), `crm.yaml` e `crm.asyncapi.yaml`.

- **Formulários são do Landing.** Saíram a tela e a API de formulários, as tabelas `formularios` e `formulario_versoes`, as permissões `marketing.formulario.*`, o item de menu "Formulários" e o evento `marketing.formulario.publicado`. A 9.5 passou a descrever o que o Marketing lê do Landing: o envio, em `GET /api/landing/envios/{id}`, e o resumo do formulário, em `GET /api/landing/formularios/{id}/resumo`
- **Funil derivado pelo Marketing.** O Landing publica só `landing.formulario.recebido` e `landing.chatbot.lead_capturado`, sem etapa. Meta Ads é a etapa 0, a captura do chatbot é a 1 e o envio é a 3. A etapa 2 fica sem fonte, como pendência com o Landing. Visitas e cliques deixaram de chegar ao Marketing: são métricas do Landing
- **Sem buffer para o Landing.** A fila de etapas 0 e 1 saiu; os eventos do Landing são processados na chegada. `leads_entrada_buffer` ficou só para o webhook do Meta, sem dado pessoal
- **Oportunidade criada pelo Landing**, pelo Contrato §12.7. O Marketing consome `crm.oportunidade.criada` (nova fila `marketing.crm-oportunidades`), liga o lead pelo `envioId` e leva o vendedor do rodízio como responsável, o que depende de uma ação nova no CRM. Saiu a proposta de payload do `POST /api/crm/oportunidades`
- **Pontuação por campo do formulário do Landing.** `regras_pontuacao` troca `campo_chave` por `formulario_id` e `campo_id` e ganha a fonte `campo_meta`; saiu o `mapeamentoCampos` de `canais_anuncio`. Já está na `V6` (PR #14)
- `leads` (ajuste na D8): `sessao_id` no lugar de `visitante_id`, `canal`, `envio_lido_em` e `envio_id` único; sai `formulario_versao`. `lead_consentimentos`: `texto_termo` no lugar de `versao_termo`, origens novas e IP e *User-Agent* só no descadastro
- Novo job 4.6, leitura pendente do envio, para o Landing fora do ar não perder o lead. A boas-vindas (4.5) ficou com um gatilho só, o envio do formulário
- Nova rota `GET /api/marketing/qualificacao/formularios`; filtro `canal` na lista de leads; `GET /api/marketing/leads/resumo` listado na seção 6
- Painel sem `visitantes` e `visitantesParaLeads`; conversão da etapa 1 para a 3
- Variáveis de ambiente da plataforma registradas na seção 1; `ordemMenu` 50, igual ao registro publicado no infra
- Pendências reescritas, com as resolvidas listadas no fim da seção 11

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
