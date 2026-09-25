# Projeto Integrador 2026 · Grupo 4 · Marketing e Automações

Módulo `marketing` da plataforma integrada do Projeto Integrador 2026 (PUC Goiás, Análise e Desenvolvimento de Sistemas). Cuida da captação e do funil de leads (Meta Ads e landing pages), do aquecimento por e-mail, da qualificação (lead scoring e MQL), do rodízio entre vendedores, do painel da pessoa gestora de marketing e dos campos do formulário de captação.

O módulo roda dentro da plataforma do Grupo 2 (`infra-integrador-2026`) e conversa com o CRM (Grupo 6) e com o Landing (Grupo 7) por API e eventos no RabbitMQ.

> **Situação:** por enquanto o repositório tem só documentação; o desenvolvimento está começando.

## Stack prevista

- **Back-end:** Java 21, Spring Boot 3.x, Maven, Flyway, Spring AMQP, PostgreSQL (schema `marketing`), RabbitMQ
- **Front-end:** React + TypeScript + Vite, Tailwind CSS v4, `lucide-react`, Design System Centinela; aberto pela casca da plataforma em `<iframe>` em `/modulos/marketing/`
- **Testes:** Testcontainers (PostgreSQL e RabbitMQ) e WireMock
- **Entrega:** imagens Docker em `ghcr.io`, integradas pelo compose do `infra-integrador-2026`

## Estrutura

```
.
├── .claude/     contexto e skills do Claude Code, só local (ver abaixo)
├── Docs/        documentação do módulo
└── README.md
```

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

A pasta resume a documentação; ela não substitui o documento norteador. Quando o desenho mudar, atualize primeiro o norteador e depois o resumo correspondente em `.claude/contexto/`.

## Testes locais da jornada completa

A landing page e o formulário são do Grupo 7, mas o grupo precisa testar a jornada inteira localmente, do anúncio até a oportunidade no CRM, passando pelo construtor de formulário da Etapa 2. Para isso o repositório vai ter um **placeholder do Landing**, desacoplado do módulo e com o mesmo contrato, a ser trocado pelo Landing real quando ele estiver pronto.

O placeholder lê a definição do formulário na API do Marketing, publica os mesmos eventos que o Landing publicaria e expõe a consulta de respostas do envio. Ele usa o mesmo nome de serviço e a mesma porta do Landing real, então a troca é só de configuração, sem mudar o código do Marketing.
