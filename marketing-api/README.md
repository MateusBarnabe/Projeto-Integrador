# marketing-api

API do módulo de Marketing (Grupo 4). Recebe os leads, guarda o funil, qualifica, distribui entre vendedores e atende o front do módulo e os outros módulos da plataforma.

> **Estado atual (06/11/2026): jornada do lead pronta no back.** Além da fundação (F1 a F8: JWT, entidade base, publicador e consumo de eventos, cliente HTTP com token de serviço, infra de testes e variáveis da plataforma), a API já consome a captura do chatbot (E1), o envio do formulário com a leitura do envio no Landing (E5) e a oportunidade do CRM (E6); pontua (Q1), distribui pelo rodízio (Q2), pede notificação e timeline à plataforma (N1) e roda os jobs 4.4 e 4.6. A API de leads (A2) tem lista, detalhe, resumo e busca. Faltam as tabelas de e-mail e as configurações (D3, D7, A3), o painel (A4), o Meta (E3, E4), o descadastro e os e-mails (E7 a E10). Plano em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

| | |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Maven (wrapper `mvnw`), Spring Security (resource server JWT), Spring Data JPA, Flyway, Spring AMQP, springdoc |
| Porta | `8087` |
| Rotas | `/api/marketing/**` (com token) e `/public/marketing/**` (sem token) |
| Pacote raiz | `br.com.centinela.marketing` |
| Banco | Postgres, schema `marketing`; a aplicação conecta como `usr_marketing` e o Flyway como `own_marketing` |
| Mensageria | RabbitMQ, vhost `plataforma`, usuário `mq_marketing` |
| Nome na plataforma | serviço e imagem `marketing` (`http://marketing:8087`) |

As regras que a API precisa seguir (envelope, multi-tenant, sete colunas, eventos) vêm do Contrato de Integração do Grupo 2 e estão resumidas no [documento norteador](../Docs/documento-norteador.md), seções 1, 3, 6 e 8.

## Como rodar

A infraestrutura (Postgres, RabbitMQ, Mailpit) precisa estar de pé. Da raiz do repositório:

```bash
npm run infra               # sobe Postgres, RabbitMQ e Mailpit no Docker
npm run dev:api             # roda a API (atalho para o comando abaixo)
```

Ou de dentro desta pasta:

```bash
./mvnw spring-boot:run      # Linux/macOS
mvnw.cmd spring-boot:run    # Windows
```

Não é preciso instalar o Maven: o wrapper baixa a versão certa. Precisa do JDK 21 ou mais novo.

| Endereço | O que é |
|---|---|
| http://localhost:8087/api/marketing/health | saúde da API e da conexão com o banco |
| http://localhost:8087/swagger-ui.html | Swagger UI (tela para ver e testar os endpoints) |
| http://localhost:8087/v3/api-docs | OpenAPI em JSON, gerado a partir dos controllers |

### Testes

```bash
./mvnw verify               # ou, na raiz: npm run test:api
```

Os testes sobem um PostgreSQL e um RabbitMQ de verdade com o Testcontainers, rodando os mesmos scripts da plataforma ([infra/postgres/init](../infra/postgres/init/01-marketing.sh) e [infra/rabbitmq](../infra/rabbitmq/definitions.json)), e simulam Landing, CRM, identity e Graph API do Meta com o WireMock (ver [Testes](#testes-1)). Os que usam container precisam do Docker ligado; sem ele, são pulados.

### Imagem Docker

O [Dockerfile](Dockerfile) compila com Maven e JDK 21 e gera uma imagem final só com o JRE 21. É a imagem que o `docker-compose.yml` da raiz sobe como serviço `marketing`:

```bash
docker compose up -d --build marketing    # na raiz; sobe junto o Postgres e o RabbitMQ
```

## Variáveis de ambiente

Todo endereço e credencial vem de variável de ambiente (Contrato §9.1). **Os nomes são os mesmos que o `docker-compose.yml` do `infra-integrador-2026` entrega ao serviço `marketing`**: na plataforma, a imagem recebe essas variáveis e não usa os padrões. Os valores padrão servem para rodar local com o `docker-compose.yml` da raiz. A lista completa está em [application.yml](src/main/resources/application.yml).

| Variável | Padrão | Para quê |
|---|---|---|
| `SERVER_PORT` | `8087` | porta HTTP |
| `DB_URL` | `jdbc:postgresql://localhost:5432/plataforma` | endereço do Postgres. O schema vem do `search_path` dos usuários, ajustado pelo init do banco |
| `DB_APP_USER`, `DB_APP_PASSWORD` | `usr_marketing` | usuário da aplicação (só lê e escreve nas tabelas) |
| `DB_OWNER_USER`, `DB_OWNER_PASSWORD` | `own_marketing` | dono do schema, roda as migrations |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_VHOST` | `localhost`, `5672`, `plataforma` | endereço do RabbitMQ |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `mq_marketing` | usuário do módulo no RabbitMQ |
| `JWKS_URI` | `http://localhost:8081/api/identity/.well-known/jwks.json` | chaves públicas do identity para validar o JWT |
| `IDENTITY_BASE_URL` | `http://identity:8081` | identity, para o token de serviço e o `tenants/resolver` (vem do compose da plataforma) |
| `LANDING_BASE_URL` | `http://landing:8088` | Landing, para ler o envio e o resumo do formulário. Ainda não está no compose da plataforma (X14) |
| `CRM_BASE_URL` | `http://crm:8082` | CRM. Ainda não está no compose da plataforma (X14) |
| `SVC_CLIENT_ID`, `SVC_CLIENT_SECRET` | `marketing`, vazio | token de serviço; o compose da plataforma preenche o segredo com o `SVC_MARKETING_SEGREDO` do `.env`. Sem ele, a API sobe e as chamadas a outros módulos falham com `NAO_CONFIGURADO` |
| `SWAGGER_HABILITADO` | `true` | `false` desliga o Swagger UI e o `/v3/api-docs` |

No perfil `dev` ([application-dev.yml](src/main/resources/application-dev.yml)), os três endereços apontam para `localhost`. Propriedades do módulo, sem variável de ambiente própria: `marketing.rodizio.atribuicao-pendente.intervalo` e `marketing.entrada.leitura-pendente.intervalo` (padrão `PT1M`, os jobs 4.4 e 4.6), `marketing.rodizio.fuso-horario` (padrão `America/Sao_Paulo`, para o horário comercial) e `marketing.consumo.landing-leads.auto-startup` e `marketing.consumo.crm-oportunidades.auto-startup` (padrão `true`; os testes que leem as filas direto desligam os consumidores).

## Estrutura de pastas

```
marketing-api/
├── Dockerfile                    build (Maven + JDK 21) e imagem final (JRE 21)
├── mvnw, mvnw.cmd, .mvn/         Maven Wrapper
├── pom.xml                       dependências e versão do Spring Boot
└── src/
    ├── main/
    │   ├── java/br/com/centinela/marketing/
    │   │   ├── MarketingApplication.java      ponto de entrada
    │   │   ├── compartilhado/                 o que todas as áreas usam
    │   │   │   ├── agendamento/               liga o @Scheduled dos jobs
    │   │   │   ├── dados/                     entidade base, conversores e paginação (F2)
    │   │   │   ├── documentacao/OpenApiConfig.java
    │   │   │   ├── http/                      cliente HTTP para Landing, CRM e identity, com token de serviço (F5)
    │   │   │   ├── mensageria/                publicação (F3) e consumo idempotente com DLQ (F4)
    │   │   │   ├── plataforma/                pedidos em identity.entrada: notificação e timeline (N1)
    │   │   │   ├── seguranca/                 JWT, permissões e SegurancaConfig (F1)
    │   │   │   ├── tenant/                    TenantContexto e TenantFiltro
    │   │   │   └── web/                       envelope, erros e X-Request-Id
    │   │   ├── crm/                           ponto para levar o vendedor ao CRM (C1, pendente)
    │   │   ├── entrada/                       consumidores do Landing e do CRM, identidade, junção e job 4.6 (E1, E5, E6, E11)
    │   │   ├── leads/                         lead, timeline e consentimentos (D1, D8)
    │   │   │   └── api/                       API de leads: lista, detalhe, resumo e busca (A2)
    │   │   ├── notificacoes/                  NOVO_LEAD e registro na timeline (N1)
    │   │   ├── qualificacao/                  regras, faixas e motor de pontuação (D5, Q1)
    │   │   ├── rodizio/                       rodízio, job 4.4 e reatribuição manual (Q2)
    │   │   └── saude/SaudeController.java     GET /api/marketing/health
    │   └── resources/
    │       ├── application.yml                configuração
    │       ├── application-dev.yml            perfil dev (Flyway fora de ordem, endereços em localhost)
    │       └── db/migration/                  migrations do Flyway (V1, V2, V6, V8, V9, V10)
    └── test/java/br/com/centinela/marketing/
        ├── FundacaoApiTest.java               testes do alicerce (Testcontainers)
        ├── compartilhado/                     dados, http, mensageria, segurança e a base de integração (F6)
        ├── entrada/                           consumo do chatbot, do envio, da oportunidade e job 4.6
        ├── leads/                             regras do lead, repositórios e a API (leads/api)
        ├── notificacoes/, rodizio/            pedidos à plataforma e rodízio
        └── qualificacao/                      regras da qualificação e motor de pontuação
```

**Onde colocar código novo:** um pacote por área de domínio ao lado de `compartilhado/` e `saude/`, por exemplo `leads/`, `qualificacao/`, `rodizio/`, `configuracoes/`, `painel/`, `entrada/` (consumidores dos eventos do Landing e do CRM, webhook e buffer do Meta). Clientes HTTP de outros módulos (Landing, CRM, identity) ficam em `compartilhado/`. Cada pacote reúne o controller, o serviço, as entidades e os repositórios daquela área. O que servir para mais de uma área vai para `compartilhado/`.

## Classes

### `MarketingApplication`

Ponto de entrada do Spring Boot. Não tem lógica.

### `compartilhado.web.Resposta<T>`

*Record* do envelope que **toda** resposta HTTP do módulo usa, inclusive as de erro (Contrato §8.2):

```json
{ "success": true, "data": { "status": "UP" }, "message": null, "errors": [] }
```

- `Resposta.ok(data)`: sucesso, com `message` nulo e `errors` vazio.
- `Resposta.erro(mensagem)` e `Resposta.erro(mensagem, erros)`: falha, com `data` nulo.

Todo controller devolve `ResponseEntity<Resposta<...>>`. Nunca devolva o objeto "cru".

### `compartilhado.web.ErroCampo`

*Record* de cada item de `errors[]`: `campo`, `codigo` e `detalhe`. Exemplo: `{"campo": "email", "codigo": "CAMPO_INVALIDO", "detalhe": "deve ser um e-mail válido"}`.

### `compartilhado.web.TratadorDeErros`

`@RestControllerAdvice` que converte exceções em respostas com o envelope e o código HTTP do Contrato §8.4:

| Exceção | Código | Mensagem |
|---|---|---|
| `MethodArgumentNotValidException` (Bean Validation, `@Valid`) | `400` | "Dados inválidos.", com um `ErroCampo` por campo |
| `HttpMessageNotReadableException` (JSON inválido) | `400` | "Corpo da requisição inválido." |
| `NoResourceFoundException` (rota inexistente) | `404` | "Recurso não encontrado." |
| `HttpRequestMethodNotSupportedException` | `405` | "Método não suportado." |
| qualquer outra `Exception` | `500` | "Erro inesperado." (o detalhe vai só para o log) |

Quando surgirem as regras de negócio, este é o lugar para tratar `404` de registro de outro tenant, `409` de duplicidade e `422` de regra violada.

### `compartilhado.web.RequestIdFilter`

Filtro que roda antes de todos. Lê o cabeçalho `X-Request-Id`, ou gera um UUID se ele não vier, e:

- coloca o valor no MDC com a chave `requestId`, e o padrão de log do `application.yml` o mostra em toda linha;
- devolve o mesmo valor no cabeçalho `X-Request-Id` da resposta.

Esse mesmo valor será o `correlacaoId` dos eventos publicados (Contrato §3).

### `compartilhado.seguranca.SegurancaConfig`

Configuração do Spring Security:

- API sem estado (sem sessão), sem CSRF;
- **sem token:** `/api/marketing/health`, `/public/marketing/**`, `/error` e as rotas da documentação (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`);
- **todo o resto exige JWT** do identity, validado localmente pelas chaves do `JWKS_URI` (Contrato §4.2);
- `401` ("Token ausente, inválido ou expirado.") e `403` ("Sem permissão para esta ação.") saem no formato do envelope;
- `@EnableMethodSecurity` liga o `@PreAuthorize` para conferir permissões nos controllers (Contrato §5.2).

O claim `perms` é convertido em *authorities* sem prefixo, para uso com `@PreAuthorize`. O `tenant_id` e o `sub` do token podem ser obtidos por `ContextoSeguranca.exigirUsuarioAtual()`, que devolve um `UsuarioAtual`; `ContextoSeguranca.usuarioAtual()` devolve um `Optional`, vazio em rotina, rota pública ou token de serviço. O tenant nunca é lido do corpo ou da query; tokens de serviço e o cabeçalho `X-Tenant-Id` entram nas tarefas de integração correspondentes.

### `compartilhado.mensageria` (F3)

Publicação de mensagens no RabbitMQ, seguindo o Contrato §9.7 e o norteador §8:

| Classe | O que é |
|---|---|
| `Evento` | *record* do envelope: `id`, `tipo`, `versao`, `tenantId`, `moduloOrigem` (`marketing`), `ocorridoEm` (UTC), `usuarioId`, `correlacaoId` e `dados` |
| `PublicadorDeEventos` | o que o domínio chama. `publicar(tipo, versao, dados)` vai para a exchange `marketing.eventos`; `pedirAPlataforma(tipo, versao, dados)` vai para `identity.entrada`. A routing key é o tipo. Monta o envelope com o tenant de `TenantContexto`, o usuário do token (nulo em rotina e token de serviço) e o `X-Request-Id` como `correlacaoId`. Recusa tipo fora do padrão `modulo.entidade.acao` e chamada fora de transação |
| `EnvioDeEventos` | `@TransactionalEventListener(AFTER_COMMIT)`: só envia depois do commit, então um rollback descarta a mensagem. Põe `user_id` igual ao usuário da conexão (`mq_marketing`), `message_id` igual ao `id` do envelope, `type` igual ao tipo e entrega persistente |
| `MensageriaConfig` | mensagens em JSON com o `ObjectMapper` da API |

Uso típico, dentro do serviço transacional que muda o dado:

```java
publicador.publicar("marketing.lead.qualificado", 1,
        Map.of("leadId", lead.getId(), "faixa", lead.getFaixaQualificacao().valor(), "pontuacao", lead.getPontuacao()));
```

Todo tipo novo precisa estar em [Docs/contratos/marketing.asyncapi.yaml](../Docs/contratos/marketing.asyncapi.yaml) antes de ser publicado, e só depois que algum módulo confirmar que consome (hoje o AsyncAPI está sem mensagens). `dados` não leva senha, token, segredo nem dado pessoal além do necessário.

**Limite conhecido:** se o RabbitMQ estiver fora do ar no momento do envio, a transação já foi confirmada e o evento se perde; o erro vai para o log com o tipo, o `id` e o tenant, sem os dados. Se algum fluxo não puder perder evento, o caminho é uma tabela de saída (*outbox*) gravada na mesma transação.

### `compartilhado.documentacao.OpenApiConfig`

Monta o OpenAPI mostrado no Swagger: título, descrição e o esquema de segurança Bearer JWT (botão **Authorize**, onde se cola o token do identity). Todas as rotas aparecem exigindo token por padrão. A rota pública marca a exceção com `@SecurityRequirements` vazio, como faz o `SaudeController`.

O Swagger documenta tudo o que a API tem. O contrato publicado para os outros grupos continua sendo o [Docs/contratos/marketing.yaml](../Docs/contratos/marketing.yaml), escrito à mão, com só o que eles consomem.

### `saude.SaudeController`

`GET /api/marketing/health`, sem token (Contrato §8.5). Faz um `SELECT 1` no banco:

- banco respondeu: `200` com `{"status": "UP"}`;
- banco fora: `503` com `{"status": "DOWN"}` e a mensagem "Banco indisponível.".

## Banco e migrations

Toda mudança de banco é uma migration do Flyway em `src/main/resources/db/migration`, com o nome `V<número>__descricao.sql` (dois sublinhados). O Flyway roda sozinho quando a API sobe, conectado como `own_marketing`, que é dono do schema. A aplicação usa o `usr_marketing`, que só pode ler e escrever nas tabelas; ele não cria nem altera tabela.

Cada tarefa de dados do [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md) tem um número de migration fixo (`V2` é a D1, `V3` é a D2 e assim por diante), e os PRs podem ser mergeados fora dessa ordem. Por isso o ambiente local roda com o perfil `dev` ([application-dev.yml](src/main/resources/application-dev.yml)), que liga o `spring.flyway.out-of-order`: se o seu banco já tem a `V5` e chega a `V3`, ela é aplicada em vez de travar a subida. O perfil é ativado pelo `spring-boot:run` (configurado no `pom.xml`) e pelo `docker-compose.yml` da raiz; a imagem publicada na plataforma roda sem ele, e os testes também, porque sobem o banco do zero.

| Migration | O que faz |
|---|---|
| `V1__cria_eventos_processados.sql` | tabela `eventos_processados` (`evento_id`, `tipo`, `processado_em`), obrigatória em quem consome eventos. Cada evento consumido grava o `id` aqui, na mesma transação do efeito, para não ser processado duas vezes |
| `V2__cria_leads.sql` | D1: `leads` (com `temperatura` gerada a partir da `etapa`), `lead_eventos` (com `sequencia` por identidade, usada pelo SSE) e `lead_consentimentos` (só inclusão). Chaves únicas `(tenant_id, meta_lead_id)` e `token_descadastro`; `CHECK` nos valores fechados do norteador (etapa, origem, faixa, status e tipos). Parte do desenho dela foi trocada pela `V8` |
| `V6__cria_qualificacao.sql` | D5: `regras_pontuacao` (`valores` em `jsonb`; cada fonte preenche só o próprio alvo: `formulario_id` e `campo_id` do Landing em `campo_formulario`, `campo_meta` em `campo_meta`, nada em `utm`), `qualificacao_configuracoes` (uma por tenant; `dias_atendimento` em `smallint[]` de 1 a 7; morno abaixo de quente; início antes do fim) e `vendedores_rodizio` (único por `(tenant_id, vendedor_id)`) |
| `V8__ajusta_leads_ao_contrato_do_landing.sql` | D8: ajusta a `V2` ao contrato do Landing. Em `leads`, sai `visitante_id` e `formulario_versao` e entram `sessao_id` (único por tenant), `canal` (`formulario`, `landing_page`, `embed`, `chatbot`) e `envio_lido_em`; `envio_id` passa a ser único por tenant; índices da leitura pendente e do contato do CRM. Em `lead_eventos`, `respostas_lidas` no lugar de `formulario_atualizado`. Em `lead_consentimentos`, `texto_termo` (`text`) no lugar de `versao_termo`, `sessao_id` novo, sai `formulario_versao` e as origens passam a ser `landing.formulario.recebido`, `landing.chatbot.lead_capturado` e `descadastro` |
| `V9__indice_leads_aguardando_vendedor.sql` | Q2: índice parcial `(tenant_id, qualificado_em, id)` dos leads MQL sem vendedor, ativos e não anonimizados. "Aguardando vendedor" é derivado, sem coluna |
| `V10__leitura_pendente_do_envio.sql` | E5/E11: `leitura_envio_falhas` e `leitura_envio_desistida_em` em `leads`, o índice da leitura pendente que sabe da desistência (no lugar do da `V8`) e os tipos `leads_unidos` e `respostas_indisponiveis` em `lead_eventos` |

Regras para as próximas tabelas (norteador, seção 3):

- nome em português, `snake_case`, plural e sem acento;
- as sete colunas obrigatórias: `id uuid` (gerado pela aplicação), `tenant_id uuid not null` indexado, `created_at`, `updated_at`, `deleted_at` (`timestamptz`; `deleted_at` nulo = ativo), `created_by` e `updated_by`;
- *soft delete*: nunca `DELETE`, preencher `deleted_at`;
- FK só dentro do schema `marketing`; referência a outro módulo guarda só o UUID;
- dinheiro em `numeric(15,2)`; percentual em `numeric(9,4)`, em pontos percentuais.

### Fundação das entidades (F2)

- `EntidadeBase` fornece as sete colunas, gera o UUID da aplicação e preenche datas e auditoria nos callbacks JPA;
- `TenantFiltro` coloca o `tenant_id` do token em `TenantContexto` durante a requisição;
- `ResolvedorDeTenant` integra o contexto ao `@TenantId` do Hibernate;
- entidades concretas devem declarar seu próprio `@SQLDelete` com o nome da tabela e o `@SQLRestriction("deleted_at IS NULL")`. O Hibernate não herda o `@SQLRestriction` de uma `@MappedSuperclass`: sem ele na entidade, os registros excluídos continuam aparecendo nas consultas;
- `Paginacao.pedido` limita `tamanho` a 100 e `Pagina` devolve `{itens, pagina, tamanho, total}`.

Os valores fechados do norteador (`meta_ads`, `opt_out`, `landing.formulario.recebido`...) viram enums Java que implementam `ValorPersistido`. Cada enum tem um `Conversor` (subclasse de `ConversorDeValor`) para gravar o valor do norteador, e não o nome da constante; a entidade usa `@Convert(converter = OEnum.Conversor.class)`.

### Leads (D1 e D8)

Pacote `leads`, com as três tabelas da `V2`, ajustadas pela `V8`:

| Classe | O que é |
|---|---|
| `Lead` | registro central. `avancarEtapa` nunca rebaixa a etapa (§2); `atualizarContato` não apaga um dado existente com valor vazio; `possuiIdentificadorDeContato` diz se há Meta, e-mail ou telefone; `registrarConsentimento` muda só o estado atual. Ligações com o Landing e o CRM: `vincularSessao` (chatbot), `vincularEnvio` (um envio novo deixa a leitura pendente), `registrarRespostas` (cópia do envio; envio anonimizado no Landing não apaga a cópia), `leituraDoEnvioPendente`, `vincularCrm` e `vincularOportunidade`. O `token_descadastro` é gerado na criação (32 bytes aleatórios em base64url). `temperatura` é coluna gerada pelo banco e só de leitura |
| `LeadEvento` | item da timeline. A `sequencia` vem do banco e é a ordem usada pelo SSE; `etapaAlterada` monta o registro de mudança de etapa |
| `LeadConsentimento` | evidência de aceite ou revogação, sem métodos de alteração. `doLanding` para a captura do chatbot e para o envio (com o texto do termo, sem IP e *User-Agent*, que o Landing não informa); `revogacaoPorDescadastro` para o link de descadastro, com IP e *User-Agent* (cortado em 500 caracteres); `repete` evita gravar de novo o mesmo aceite da captura e do envio da mesma conversa |
| `LeadRepository`, `LeadEventoRepository`, `LeadConsentimentoRepository` | Spring Data. Buscas de identidade por `meta_lead_id`, `sessao_id`, `envio_id` e `crm_contato_id` (o lead mais antigo); timeline em ordem de `sequencia` |
| `OrigemLead`, `CanalLead`, `Temperatura`, `StatusAquecimento`, `FaixaQualificacao`, `TipoEventoLead`, `OrigemConsentimento` | valores fechados das colunas |

Fora de requisição (jobs, consumidores de eventos), toda leitura e gravação deve rodar dentro de `TenantContexto.executar(tenant, ...)`: o Hibernate fixa o tenant quando abre a sessão e usa esse valor tanto para preencher `tenant_id` quanto para filtrar as consultas, inclusive o `findById`. Por isso ainda não há busca por `token_descadastro`: a rota pública de descadastro (E7) não tem tenant no token e precisa resolver o tenant antes de consultar.

### Qualificação (D5)

Pacote `qualificacao`, com as três tabelas da `V6` (norteador §3 e §9.3). O cálculo da pontuação é da Q1 e o rodízio da Q2; aqui ficam os dados e as validações de cada registro.

| Classe | O que é |
|---|---|
| `RegraPontuacao` | critério da pontuação. `porCampo(criterio, formularioId, campoId, valores, pontos)` para a resposta de um campo de formulário do Landing (ids do resumo do formulário, sem FK), `porCampoMeta(criterio, pergunta, valores, pontos)` para o formulário instantâneo do Meta e `porUtm(criterio, valores, pontos)` para a origem do tráfego. `alterar` muda critério, valores e pontos; `apontarPara...` troca a fonte e limpa o alvo anterior. Recusa critério vazio, alvo incompleto, lista de valores vazia e pontos negativos |
| `QualificacaoConfiguracao` | faixas e rodízio do tenant. `padrao()` traz os valores iniciais das Regras de Negócio: MQL quente a partir de 70, morno a partir de 40, rodízio ligado, de segunda a sexta das 8h às 18h e transbordo de 15 minutos. Os dias de atendimento saem como `Set<DayOfWeek>`, com a mesma numeração do banco (1 = segunda) |
| `VendedorRodizio` | vendedor na escala, só com o UUID do identity. `registrarAtribuicao` move o vendedor para o fim da fila |
| `FonteRegra` | `campo_formulario`, `campo_meta` ou `utm`, gravado pelo `ConversorDeValor` |
| `RegraPontuacaoRepository`, `QualificacaoConfiguracaoRepository`, `VendedorRodizioRepository` | Spring Data. `findByAtivoTrueAndFormularioId` traz as regras do formulário de um envio e `findByAtivoTrueAndFonte` as de UTM e do Meta, para o motor da Q1; `findFirstBy` devolve a configuração do tenant atual |

### Pontuação (Q1)

`MotorDePontuacao.calcular` é o cálculo puro (sem banco) e `PontuacaoDeLeads.pontuar(lead)` o serviço que os consumidores e jobs chamam: lê do próprio lead o formulário, as respostas e a UTM, soma as regras ativas (cada critério uma vez, pela de mais pontos), define a faixa pelos limites do tenant, grava `pontuacao_detalhe` e registra `qualificacao_alterada`. O formato das condições de `utm` está no norteador, 9.3.

### Entrada do lead (E1, E5, E6, E11)

Pacote `entrada`, que segue a seção 5 do norteador. Os consumidores usam `ConsumoDeEventos.consumir(...)`.

| Classe | O que é |
|---|---|
| `ConsumidorDeLeadsDoLanding` | fila `marketing.landing-leads`; despacha por `tipo` para `CapturaDoChatbot` (etapa 1) ou `EnvioDoFormulario` (etapa 3) |
| `ConsumidorDeOportunidadesDoCrm`, `OportunidadeDoCrm` | fila `marketing.crm-oportunidades`; liga o lead à oportunidade (`handoff_crm`) ou cria o lead pendente quando a oportunidade chega antes do envio |
| `NormalizacaoDeContato`, `ChavesDeIdentidade`, `IdentificacaoDeLeads` | normaliza e-mail e telefone e acha o lead por Meta, envio, sessão, contato do CRM, e-mail e telefone, nessa ordem |
| `GravacaoDeLeads`, `RegistroDeConsentimentos`, `Procedencia` | cria o lead, avança a etapa (nunca recua) e grava consentimentos sem duplicar, registrando na timeline de onde veio |
| `LeituraDoEnvio`, `JuncaoDeLeads`, `TravasDeLeads` | lê o envio no Landing e aplica no lead; junta duplicados no mais antigo; trava e relê a linha do lead antes de mudar |
| `LeituraPendenteDoEnvio` | job 4.6: relê os envios pendentes, até 50 por tenant, e desiste depois de 10 falhas |

### Rodízio e notificações (Q2, N1)

`RodizioDeVendedores.atribuir(lead)` escolhe o vendedor ativo que recebeu há mais tempo (só MQL com contato), grava, registra `vendedor_atribuido` e notifica; fora do horário comercial devolve "aguardando". `AtribuicaoPendente` é o job 4.4. `VendedorDoLeadController` é a reatribuição manual. `NotificacoesDeLead` monta `NOVO_LEAD` e o registro na timeline e publica por `PedidosAPlataforma`; sem vendedor, `DestinatarioSemVendedor` decide (hoje, não notifica). Em `crm/`, `ResponsavelDaOportunidade` é o ponto onde a C1 vai levar o vendedor ao CRM; a implementação atual só registra a pendência.

### API de leads (A2)

Pacote `leads/api`: `LeadsController`, as consultas em `LeadConsultas` e os DTOs do detalhe, da lista, do resumo e da busca. Os parâmetros da lista estão no norteador, seção 6.

## Endpoints atuais

| Método | Rota | Permissão | Resposta |
|---|---|---|---|
| GET | `/api/marketing/health` | nenhuma | `200` `{"status":"UP"}` ou `503` |
| GET | `/api/marketing/leads` | `marketing.lead.ver` | lista paginada com filtros e ordenação |
| GET | `/api/marketing/leads/{id}` | `marketing.lead.ver` | detalhe em seções; outro tenant dá `404` |
| GET | `/api/marketing/leads/{id}/resumo` | `marketing.lead.ver_resumo` | resumo sem dado pessoal, no formato do `marketing.yaml` |
| GET | `/api/marketing/leads/resumo?ids=` | `marketing.lead.ver_resumo` | até 100 resumos |
| PUT | `/api/marketing/leads/{id}/vendedor` | `marketing.qualificacao.editar` | reatribuição manual do vendedor |
| GET | `/api/marketing/busca?q=` | `marketing.lead.ver` | busca global, até 5 itens |
| qualquer | outra rota em `/api/marketing/**` | — | sem token: `401` com envelope |

Os outros endpoints previstos estão na seção 6 do norteador e nas tarefas A3 e A4 dos próximos passos.

## Testes

`FundacaoApiTest` sobe a API numa porta aleatória contra um PostgreSQL do Testcontainers, criado com o mesmo script de usuários da plataforma. Ele cobre:

| Teste | O que confere |
|---|---|
| `healthRespondeSemTokenComEnvelope` | `/health` sem token dá `200`, com o envelope completo e o `X-Request-Id` na resposta |
| `rotaAutenticadaSemTokenResponde401ComEnvelope` | rota protegida sem token dá `401` no formato do envelope |
| `documentacaoOpenApiRespondeSemTokenComAsRotasDoModulo` | `/v3/api-docs` abre sem token e lista `/api/marketing/health` |

`compartilhado.mensageria.PublicadorDeEventosTest` sobe a aplicação com o PostgreSQL e um RabbitMQ do Testcontainers carregado com o mesmo [definitions.json](../infra/rabbitmq/definitions.json) do ambiente local, com os mesmos usuários, permissões e exchanges. O broker confere o `user_id` de verdade. O teste observa as exchanges como `admin`, com filas temporárias:

| Teste | O que confere |
|---|---|
| `publicaOEnvelopeEmMarketingEventosDepoisDoCommit` | envelope completo, `user_id = mq_marketing`, routing key, `message_id` e `correlacaoId` vindo do `X-Request-Id` |
| `nadaSaiQuandoATransacaoFazRollback` | rollback não publica nada |
| `pedidoAPlataformaVaiParaIdentityEntradaComOUsuarioDoToken` | pedido em `identity.entrada`, e não em `marketing.eventos`, com o `usuarioId` do token |
| `publicarForaDeTransacaoEhRecusado`, `tipoForaDoPadraoEhRecusado` | as duas proteções do publicador |

### Infra de testes de integração (F6)

Pacote de teste `compartilhado.teste`, para os testes que sobem a aplicação inteira (consumidores, clientes HTTP, webhook):

| Classe | O que é |
|---|---|
| `IntegracaoDeTeste` | base `@SpringBootTest` com PostgreSQL e RabbitMQ do Testcontainers (mesmos scripts de `infra/`), únicos por JVM, e os simuladores abaixo. Zera os stubs antes de cada teste. `conexaoRabbitAdmin()` dá uma conexão `admin` para observar exchanges e publicar os eventos do Landing e do CRM |
| `ModulosSimulados` | um WireMock por destino: `LANDING`, `CRM`, `IDENTITY` e `META_GRAPH`. As URLs entram no Spring como `LANDING_BASE_URL`, `CRM_BASE_URL`, `IDENTITY_BASE_URL` e `META_GRAPH_BASE_URL`, junto com `SVC_CLIENT_ID`/`SVC_CLIENT_SECRET`. Já vem com o stub do token de serviço (`POST /api/identity/auth/token-servico` devolve `TOKEN_SERVICO`); `stubTenantResolvido`, `stubLeadDoMeta` e `comTokenDeServico(stub, tenant)` (exige `Authorization` e `X-Tenant-Id`) cobrem o resto |
| `Envelopes` | respostas no envelope `{success, data, message, errors}`: `ok(data)`, `ok(201, data)`, `okJson("{...}")`, `pagina(itens, pagina, tamanho, total)` e `erro(422, mensagem, erros...)` |

```java
class ConsumoDoEnvioTest extends IntegracaoDeTeste {
    @Test
    void leOEnvioNoLanding() {
        LANDING.stubFor(comTokenDeServico(get(urlPathEqualTo("/api/landing/envios/" + envio)), tenant)
                .willReturn(Envelopes.ok(Map.of("id", envio, "respostas", List.of()))));
        // publica landing.formulario.recebido com conexaoRabbitAdmin() e confere o lead
    }
}
```

`ModulosSimuladosTest` confere os simuladores sem Docker; `InfraDeTesteTest` confere a base (URLs no ambiente do Spring e RabbitMQ). `PublicadorDeEventosTest` já usa a base.

### Testes da jornada do lead

Todos sobre a `IntegracaoDeTeste`, publicando os eventos reais no RabbitMQ do Testcontainers:

| Classe | O que confere |
|---|---|
| `ConsumoDeEventosIntegracaoTest`, `ConsumoDeEventosTest` | idempotência, retentativa, DLQ depois de 3 falhas e remetente errado (F4) |
| `ClienteDeModuloTest` | token de serviço, timeout, `401` com nova tentativa e falhas classificadas, sem segredo no log (F5) |
| `MotorDePontuacaoTest`, `PontuacaoDeLeadsTest` | regras, desempate e um lead de cada faixa (Q1) |
| `CapturaDoChatbotIntegracaoTest`, `NormalizacaoDeContatoTest` | captura do chatbot e normalização do contato (E1) |
| `EnvioDoFormularioIntegracaoTest`, `LeituraPendenteDoEnvioIntegracaoTest` | envio com leitura do Landing (WireMock), junção, Landing fora do ar e job 4.6 (E5, E11) |
| `OportunidadeDoCrmIntegracaoTest` | ligação com a oportunidade e lead criado antes do envio (E6) |
| `RodizioDeVendedoresTest`, `HorarioDeAtendimentoTest`, `NotificacoesDeLeadTest` | rodízio com 3 leads e 2 vendedores, fora do horário, job 4.4 e as mensagens à plataforma (Q2, N1) |
| `LeadsApiTest` | filtros, paginação, `403`, `404` de outro tenant, resumo sem dado pessoal e busca (A2) |

`BancoDeTeste` é a base dos testes de repositório (`@DataJpaTest` contra o mesmo PostgreSQL do Testcontainers, com Flyway). Os testes não rodam dentro de uma transação única, para cada chamada abrir sua sessão com o tenant de `TenantContexto`.

| Teste | O que confere |
|---|---|
| `leads.LeadTest` | etapa nunca rebaixa, contato vazio não apaga dado, identificador de contato, token de descadastro, leitura pendente do envio, envio anonimizado sem apagar a cópia, oportunidade ligada uma vez, consentimento da captura e do envio sem duplicar e corte do *User-Agent* |
| `leads.LeadRepositoryTest` | a `V2` e a `V8` contra o banco real: sete colunas, `temperatura` gerada (também ao avançar a etapa), isolamento por tenant no `findById` e nas consultas, unicidade de `sessao_id`, `envio_id` e `meta_lead_id` por tenant, respostas e canal gravados, busca pelo contato do CRM, `CHECK` de canal, origem de consentimento e tipo de evento, soft delete, ordem da timeline, histórico de consentimentos com texto longo e FK do evento para o lead |
| `qualificacao.QualificacaoTest` | valores padrão das Regras de Negócio e recusa de limites, horários, dias e regras incoerentes |
| `qualificacao.QualificacaoRepositoryTest` | a `V6` contra o banco real: valores das regras em JSON, filtro de ativas por formulário e por fonte, `CHECK` do alvo de cada fonte, configuração única por tenant com `dias_atendimento` em array, e vendedor único na escala do tenant |

Os testes do checklist da plataforma (§15) que faltam, como `403` sem permissão, cabeçalho de 32 KB e `404` para dado de outro tenant, dependem das primeiras rotas de negócio (tarefa F7).
