# crm-stub (stub do CRM)

> **Este app não é o nosso módulo.** Empresas, contatos, oportunidades e os eventos `crm.*` são do **módulo CRM, do Grupo 6**. Este é um stub mínimo para o ambiente local (tarefa L5): imita só o pedaço do contrato do CRM que a jornada do lead usa.

Contrato imitado: `contratos/crm.yaml` (0.5.0) e `contratos/crm.asyncapi.yaml` (0.1.0) do `infra-integrador-2026`. Usa o nome de serviço e a porta do CRM real (`crm:8082`), então trocar pelo módulo do Grupo 6 é só desligar este app.

Quem chama: o placeholder do Landing, no envio (empresa, contato e oportunidade, com `origem: landing` e `origemModuloId = envioId`, tarefa L4). O Marketing não cria oportunidade; ele consome `crm.oportunidade.criada`.

## Rotas

Todas respondem no envelope `{success, data, message, errors}`. O tenant vem do cabeçalho **`X-Tenant-Id`** (UUID; sem ele, `400`). O token não é conferido. Os dados ficam **em memória**, separados por tenant, e somem ao reiniciar.

| Rota | Corpo (`crm.yaml`) | Respostas |
|---|---|---|
| `POST /api/crm/empresas` | `NovaEmpresa` (`razaoSocial`, `origem`, `origemModuloId` obrigatórios) | `201` + `Location`; `409 EMPRESA_DUPLICADA` com `data.empresaId` para CNPJ repetido no tenant; `400` |
| `POST /api/crm/contatos` | `NovoContato` (`empresaId`, `nome` obrigatórios) | `201` + `Location`; `409 CONTATO_DUPLICADO` com `data.contatoId` para e-mail repetido na empresa; `400` (empresa inexistente inclusive) |
| `POST /api/crm/oportunidades` | `NovaOportunidade` (`titulo`, `empresaId` obrigatórios) | `201` + `Location` e publica `crm.oportunidade.criada`; `400` para empresa, contato, unidade ou etapa inexistentes e `PRECISAO_EXCEDIDA` |
| `GET /api/crm/health` | — | `200` `{status: UP}` |

Como na criação por serviço do CRM real: sem `etapaId`, a oportunidade entra na primeira etapa do funil padrão (um funil de mentira por tenant); sem `responsavelId`, fica sem responsável; sem próximo passo, recebe "Fazer o primeiro contato" com a data do dia.

## Evento

Ao criar a oportunidade, publica em `crm.eventos` (topic), chave `crm.oportunidade.criada`, conectado como `mq_crm` e com a propriedade AMQP `user_id = mq_crm`. Envelope da §9.7: `id`, `tipo`, `versao: 1`, `tenantId`, `moduloOrigem: "crm"`, `ocorridoEm`, `usuarioId: null`, `correlacaoId` (o `X-Request-Id` da requisição, se vier) e `dados` com `oportunidadeId`, `titulo`, `empresaId`, `contatoId`, `funilId`, `etapaId`, `tipo`, `responsavelId`, `valorImplantacao`, `valorMrr`, `origem`, `origemModuloId`.

A publicação vem depois da gravação; se o RabbitMQ estiver fora, a oportunidade continua criada, a resposta é `201` e o log avisa que o evento não saiu.

## Como rodar

```bash
npm install
npm run dev          # http://localhost:8082, com recarga automática
```

Da raiz: `npm run dev:crm` (ou junto com os outros em `npm run dev`). Pelo Docker: `docker compose up -d --build crm`.

| Variável | Padrão | Para quê |
|---|---|---|
| `PORTA` | `8082` | porta do servidor |
| `RABBITMQ_URL` | `amqp://mq_crm:mq_crm@localhost:5672/plataforma` | RabbitMQ; o usuário da URL vira o `user_id` das mensagens |

## Arquivos

| Arquivo | O que tem |
|---|---|
| `src/index.ts` | servidor Express, `/api/crm/health`, `404` e erros no envelope |
| `src/rotas.ts` | as três rotas `POST` e o filtro de `X-Tenant-Id` |
| `src/memoria.ts` | tipos e mapas em memória por tenant, com o funil padrão |
| `src/validacao.ts` | leitura do corpo acumulando os erros `{campo, codigo, detalhe}` |
| `src/publicador.ts` | conexão `amqplib` como `mq_crm`, canal com confirmação, envelope §9.7 |
| `src/envelope.ts` | envelope HTTP `ok` / `erro` |
