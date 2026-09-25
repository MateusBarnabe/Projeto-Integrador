# Projeto Integrador 2026 · Grupo 4 · Marketing e Automações

Módulo `marketing` da plataforma integrada do Projeto Integrador 2026 (PUC Goiás, Análise e Desenvolvimento de Sistemas). Cuida da captação e do funil de leads (Meta Ads e landing pages), da confirmação de boas-vindas e do aquecimento por e-mail, da qualificação (lead scoring e MQL), do rodízio entre vendedores, do painel da pessoa gestora de marketing e dos campos do formulário de captação.

O módulo roda dentro da plataforma do Grupo 2 (`infra-integrador-2026`) e conversa com o CRM (Grupo 6) e com o Landing (Grupo 7) por API e eventos no RabbitMQ.

## Estrutura (monorepo)

```
.
├── marketing-api/     API do módulo: Java 21, Spring Boot 3.5, Maven, Flyway, Spring AMQP
├── marketing-web/     front do módulo integrado na plataforma (Etapa 2): React, Vite, Tailwind v4
├── landing-web/       placeholder do Landing (Etapa 1): landing page, formulário público e servidor Node
├── infra/             scripts do Postgres e definições do RabbitMQ usados pelo docker-compose
├── scripts/           atalhos usados pelo package.json da raiz
├── Docs/              documentação do módulo
├── docker-compose.yml ambiente local completo
└── package.json       atalhos para rodar tudo junto
```

| App | O que é | Porta | Imagem na plataforma |
|---|---|---|---|
| `marketing-api` | API `/api/marketing/**` e rotas públicas `/public/marketing/**` | 8087 | `marketing` |
| `marketing-web` | Front que a casca abre em `/modulos/marketing/` | 3007 | `marketing-front` |
| `landing-web` | Página do tenant (Vite na 3008 em desenvolvimento) e API `/api/landing/**` | 8088 | não vai para a plataforma |

O `landing-web` **não é nosso módulo**: é um placeholder que cumpre o mesmo contrato que pedimos ao Grupo 7 ([landing-requisitos.md](Docs/contratos/landing-requisitos.md)). Ele serve para testar a jornada inteira localmente, do anúncio à oportunidade no CRM, passando pelo construtor de formulário da Etapa 2. Ele usa o nome de serviço e a porta do Landing real (`landing:8088`), então a troca pelo módulo do Grupo 7 é só de configuração, sem mudar o código do Marketing.

## Pré-requisitos

- Docker Desktop
- Node 24 e npm
- JDK 21 ou mais novo (o Maven vem pelo wrapper `mvnw`, não precisa instalar)

## Como rodar

### Tudo no Docker

```bash
docker compose up -d --build
```

| Endereço | O que abre |
|---|---|
| http://localhost:3007/modulos/marketing/ | front do módulo (modo sozinho) |
| http://localhost:8087/api/marketing/health | saúde da API |
| http://localhost:8087/swagger-ui.html | Swagger da API (OpenAPI em `/v3/api-docs`; `SWAGGER_HABILITADO=false` desliga) |
| http://localhost:8088 | landing page de teste |
| http://localhost:15672 | painel do RabbitMQ (`admin` / `admin`) |
| http://localhost:8025 | e-mails enviados (Mailpit) |

Um app só: `docker compose up -d marketing` (sobe junto o Postgres e o RabbitMQ de que ele depende).

### Apps fora do Docker (desenvolvimento)

A infraestrutura continua no Docker; os apps rodam na máquina, com recarga automática.

```bash
npm run instalar     # uma vez: dependências da raiz e dos dois fronts
npm run infra        # Postgres, RabbitMQ e Mailpit
npm run dev          # API, marketing-web e landing-web juntos
```

Cada um separado:

| Comando na raiz | Ou dentro da pasta | Endereço |
|---|---|---|
| `npm run dev:api` | `./mvnw spring-boot:run` (`mvnw.cmd` no Windows) | http://localhost:8087 |
| `npm run dev:web` | `npm run dev` | http://localhost:3007/modulos/marketing/ |
| `npm run dev:landing` | `npm run dev` | http://localhost:3008 (servidor na 8088) |

Em desenvolvimento, o Vite repassa `/api/marketing` para a API (8087) e `/api/landing` para o servidor do placeholder (8088).

### Testes

```bash
npm run test:api     # ou ./mvnw verify dentro de marketing-api
```

Os testes da API sobem um PostgreSQL real com Testcontainers, com os mesmos usuários e o mesmo schema da plataforma. Sem Docker, eles são pulados.

## O ambiente local e a plataforma

O `docker-compose.yml` imita só o pedaço da plataforma que o Marketing usa:

- **Postgres:** banco `plataforma`, schema `marketing`, o usuário `own_marketing` para o Flyway e o `usr_marketing` para a aplicação ([infra/postgres/init](infra/postgres/init/01-marketing.sh)).
- **RabbitMQ:** vhost `plataforma`, um usuário por módulo (`mq_marketing`, `mq_landing`, com senha igual ao nome) e as exchanges `marketing.eventos`, `landing.eventos` e `identity.entrada` ([infra/rabbitmq](infra/rabbitmq/definitions.json)). As filas são declaradas pelo módulo que consome.
- **Mailpit:** recebe os e-mails de boas-vindas e de aquecimento.

O **identity**, o **gateway** e a **casca** vêm do compose do `infra-integrador-2026`. O identity nunca é simulado: as rotas autenticadas precisam de um token real de um usuário de teste, e o endereço do JWKS vai em `IDENTITY_JWKS_URI`. Sem ele, só as rotas públicas e o `/health` respondem. Se os dois composes rodarem ao mesmo tempo, as portas 5432, 5672 e 15672 conflitam: suba só os apps deste repositório e aponte-os para a infraestrutura da plataforma.

## Documentação (`Docs/`)

| Arquivo | Conteúdo |
|---|---|
| [documento-norteador.md](Docs/documento-norteador.md) | **Fonte da verdade do grupo:** etapas do projeto, funil, tabelas, jobs, endpoints, permissões, mensageria, Etapa 2, testes e pendências. O `.html` é a mesma coisa em página |
| [contratos/](Docs/contratos/) | O que atravessa a fronteira do módulo: OpenAPI e AsyncAPI do Marketing e o que pedimos ao Landing |
| [permissoes/marketing.yaml](Docs/permissoes/marketing.yaml) · [modulos/marketing.json](Docs/modulos/marketing.json) | Permissões e registro no menu da casca, para o PR no `infra-integrador-2026` |
| [DesignSystem/](Docs/DesignSystem/) | Design System Centinela: tokens, componentes e padrões de tela |
| [Fluxo.md](Docs/Fluxo.md) · [Requisitos.md](Docs/Requisitos.md) · [Regras de Negocio.md](Docs/Regras%20de%20Negocio.md) · [LGPD.md](Docs/LGPD.md) · [Troubleshooting.md](Docs/Troubleshooting.md) | Levantamento de negócio: fluxo do lead, requisitos, pontuação e rodízio, LGPD e operação |

## Pasta `.claude/`

Contexto e automações para quem desenvolve com o [Claude Code](https://claude.com/claude-code). A pasta está no `.gitignore`: existe só na máquina de quem a criou e não vai para o repositório.

| Caminho | O que é |
|---|---|
| `.claude/CLAUDE.md` | Instruções carregadas automaticamente pelo Claude Code: fonte da verdade, regras que não se quebram e convenções do repositório |
| `.claude/contexto/` | Resumo da documentação por assunto: visão geral, regras da plataforma, domínio e dados, API e eventos, Etapa 2, ambiente local e placeholders, front-end e pendências |
| `.claude/skills/commit/` | Skill `/commit`: organiza as mudanças em commits lógicos no formato `<tipo>(<contexto>): <descrição>` e cria os commits |
