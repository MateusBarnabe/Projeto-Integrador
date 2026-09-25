# marketing-api

API do módulo de Marketing (Grupo 4). Recebe os leads, guarda o funil, qualifica, distribui entre vendedores e atende o front do módulo e os outros módulos da plataforma.

> **Estado atual (25/09/2026): alicerce.** A API sobe, conecta no Postgres com os usuários da plataforma, roda o Flyway, responde `/health` com o envelope, recusa rotas sem token com `401` e publica a documentação no Swagger. Ainda não tem nenhuma funcionalidade de negócio. O que falta está em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

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

Os testes sobem um PostgreSQL de verdade com o Testcontainers, rodando o mesmo script de usuários e schema da plataforma ([infra/postgres/init](../infra/postgres/init/01-marketing.sh)). Precisam do Docker ligado; sem ele, são pulados.

### Imagem Docker

O [Dockerfile](Dockerfile) compila com Maven e JDK 21 e gera uma imagem final só com o JRE 21. É a imagem que o `docker-compose.yml` da raiz sobe como serviço `marketing`:

```bash
docker compose up -d --build marketing    # na raiz; sobe junto o Postgres e o RabbitMQ
```

## Variáveis de ambiente

Todo endereço e credencial vem de variável de ambiente (Contrato §9.1). Os valores padrão servem para rodar local com o `docker-compose.yml` da raiz. A lista completa está em [application.yml](src/main/resources/application.yml).

| Variável | Padrão | Para quê |
|---|---|---|
| `PORTA` | `8087` | porta HTTP |
| `DB_HOST`, `DB_PORTA`, `DB_NOME` | `localhost`, `5432`, `plataforma` | endereço do Postgres |
| `DB_USUARIO`, `DB_SENHA` | `usr_marketing` | usuário da aplicação (só lê e escreve nas tabelas) |
| `FLYWAY_USUARIO`, `FLYWAY_SENHA` | `own_marketing` | dono do schema, roda as migrations |
| `RABBITMQ_HOST`, `RABBITMQ_PORTA`, `RABBITMQ_VHOST` | `localhost`, `5672`, `plataforma` | endereço do RabbitMQ |
| `RABBITMQ_USUARIO`, `RABBITMQ_SENHA` | `mq_marketing` | usuário do módulo no RabbitMQ |
| `IDENTITY_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | chaves públicas do identity para validar o JWT |
| `SWAGGER_HABILITADO` | `true` | `false` desliga o Swagger UI e o `/v3/api-docs` |

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
    │   │   │   ├── documentacao/OpenApiConfig.java
    │   │   │   ├── seguranca/SegurancaConfig.java
    │   │   │   └── web/                       envelope, erros e X-Request-Id
    │   │   │       ├── Resposta.java
    │   │   │       ├── ErroCampo.java
    │   │   │       ├── TratadorDeErros.java
    │   │   │       └── RequestIdFilter.java
    │   │   └── saude/SaudeController.java     GET /api/marketing/health
    │   └── resources/
    │       ├── application.yml                configuração
    │       └── db/migration/                  migrations do Flyway
    │           └── V1__cria_eventos_processados.sql
    └── test/java/br/com/centinela/marketing/
        └── FundacaoApiTest.java               testes do alicerce (Testcontainers)
```

**Onde colocar código novo:** um pacote por área de domínio ao lado de `compartilhado/` e `saude/`, por exemplo `leads/`, `formularios/`, `qualificacao/`, `rodizio/`, `configuracoes/`, `painel/`, `entrada/` (buffer e consumidores). Cada pacote reúne o controller, o serviço, as entidades e os repositórios daquela área. O que servir para mais de uma área vai para `compartilhado/`.

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
- **todo o resto exige JWT** do identity, validado localmente pelas chaves do `IDENTITY_JWKS_URI` (Contrato §4.2);
- `401` ("Token ausente, inválido ou expirado.") e `403` ("Sem permissão para esta ação.") saem no formato do envelope;
- `@EnableMethodSecurity` liga o `@PreAuthorize` para conferir permissões nos controllers (Contrato §5.2).

Ainda falta ler o `tenant_id`, o `sub` e as permissões do token e transformá-los em *authorities* do Spring. Isso é a tarefa F1 dos próximos passos.

### `compartilhado.documentacao.OpenApiConfig`

Monta o OpenAPI mostrado no Swagger: título, descrição e o esquema de segurança Bearer JWT (botão **Authorize**, onde se cola o token do identity). Todas as rotas aparecem exigindo token por padrão. A rota pública marca a exceção com `@SecurityRequirements` vazio, como faz o `SaudeController`.

O Swagger documenta tudo o que a API tem. O contrato publicado para os outros grupos continua sendo o [Docs/contratos/marketing.yaml](../Docs/contratos/marketing.yaml), escrito à mão, com só o que eles consomem.

### `saude.SaudeController`

`GET /api/marketing/health`, sem token (Contrato §8.5). Faz um `SELECT 1` no banco:

- banco respondeu: `200` com `{"status": "UP"}`;
- banco fora: `503` com `{"status": "DOWN"}` e a mensagem "Banco indisponível.".

## Banco e migrations

Toda mudança de banco é uma migration do Flyway em `src/main/resources/db/migration`, com o nome `V<número>__descricao.sql` (dois sublinhados). O Flyway roda sozinho quando a API sobe, conectado como `own_marketing`, que é dono do schema. A aplicação usa o `usr_marketing`, que só pode ler e escrever nas tabelas; ele não cria nem altera tabela.

| Migration | O que faz |
|---|---|
| `V1__cria_eventos_processados.sql` | tabela `eventos_processados` (`evento_id`, `tipo`, `processado_em`), obrigatória em quem consome eventos. Cada evento consumido grava o `id` aqui, na mesma transação do efeito, para não ser processado duas vezes |

Regras para as próximas tabelas (norteador, seção 3):

- nome em português, `snake_case`, plural e sem acento;
- as sete colunas obrigatórias: `id uuid` (gerado pela aplicação), `tenant_id uuid not null` indexado, `created_at`, `updated_at`, `deleted_at` (`timestamptz`; `deleted_at` nulo = ativo), `created_by` e `updated_by`;
- *soft delete*: nunca `DELETE`, preencher `deleted_at`;
- FK só dentro do schema `marketing`; referência a outro módulo guarda só o UUID;
- dinheiro em `numeric(15,2)`; percentual em `numeric(9,4)`, em pontos percentuais.

## Endpoints atuais

| Método | Rota | Token | Resposta |
|---|---|---|---|
| GET | `/api/marketing/health` | não | `200` `{"status":"UP"}` ou `503` |
| qualquer | outra rota em `/api/marketing/**` | sim | sem token: `401` com envelope |

Os endpoints previstos estão na seção 6 do norteador e nas tarefas A1 a A4 dos próximos passos.

## Testes

`FundacaoApiTest` sobe a API numa porta aleatória contra um PostgreSQL do Testcontainers, criado com o mesmo script de usuários da plataforma. Ele cobre:

| Teste | O que confere |
|---|---|
| `healthRespondeSemTokenComEnvelope` | `/health` sem token dá `200`, com o envelope completo e o `X-Request-Id` na resposta |
| `rotaAutenticadaSemTokenResponde401ComEnvelope` | rota protegida sem token dá `401` no formato do envelope |
| `documentacaoOpenApiRespondeSemTokenComAsRotasDoModulo` | `/v3/api-docs` abre sem token e lista `/api/marketing/health` |

Os testes do checklist da plataforma (§15) que faltam, como `403` sem permissão, cabeçalho de 32 KB e `404` para dado de outro tenant, dependem das primeiras rotas de negócio (tarefa F7).
