# landing-web (placeholder do Landing)

> **Este app não é o nosso módulo.** A landing page, os formulários, os envios e os eventos `landing.*` são do **módulo Landing, do Grupo 7**. Este é um *placeholder*: uma imitação mínima e descartável, que cumpre o contrato publicado pelo Grupo 7 e serve só para testar localmente a jornada inteira do lead.

A jornada que ele permite testar:

1. o visitante chega pelo anúncio, com UTM na URL;
2. clica em "Fale conosco" e preenche um formulário de exemplo, definido aqui mesmo (o formulário é do Landing);
3. no envio, o placeholder cria empresa, contato e oportunidade no stub do CRM e publica `landing.formulario.recebido`;
4. o Marketing consome o evento, lê as respostas em `GET /api/landing/envios/{id}` e liga o lead à oportunidade quando recebe `crm.oportunidade.criada`.

Quando o Landing real existir, basta desligar este app e apontar o Marketing para o módulo do Grupo 7. O placeholder usa o mesmo nome de serviço (`landing`), a mesma porta (`8088`) e as mesmas rotas (`/api/landing/**`), então **o código do Marketing não muda**.

O contrato que ele imita:

- `contratos/landing.yaml` e `contratos/landing.asyncapi.yaml` do `infra-integrador-2026`: o contrato publicado pelo Grupo 7;
- [Docs/contratos/landing-requisitos.md](../Docs/contratos/landing-requisitos.md): o que consumimos e o que ainda pedimos a eles.

> **Estado atual (25/09/2026, revisado em 05/11/2026): esqueleto.** A página de exemplo e o `/api/landing/health` funcionam. O formulário, os eventos, a gravação dos envios e o stub do CRM ainda não existem (tarefas L1 a L5 em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md)).

| | |
|---|---|
| Stack | página em React 19 + Vite 8 + Tailwind v4; servidor Node 24 + Express 5 rodando `.ts` direto, sem build |
| Portas | `3008` (Vite, só em desenvolvimento) e `8088` (servidor Node) |
| Rotas | `/api/landing/**` |
| Regras | não precisa seguir o Design System nem o checklist da plataforma; é ferramenta de teste e deve continuar pequeno |

## Como rodar

```bash
npm install
npm run dev          # página em http://localhost:3008 e servidor em http://localhost:8088
```

Da raiz do repositório: `npm run dev:landing`. Em desenvolvimento, o Vite repassa `/api/landing` para o servidor Node.

| Script | O que faz |
|---|---|
| `npm run dev` | Vite (página) e servidor Node juntos, os dois com recarga automática |
| `npm run dev:front` / `npm run dev:server` | cada um separado |
| `npm run build` | confere os tipos da página e do servidor e gera o build da página em `dist/` |
| `npm start` | só o servidor; se existir `dist/`, ele entrega a página também |
| `npm run typecheck` | confere os tipos da página e do servidor |

### Pelo Docker

```bash
docker compose up -d --build landing    # na raiz
```

Em container, roda **um processo só**: o servidor Node entrega a página (o build em `dist/`) e a API na porta `8088`. Abra http://localhost:8088.

## Variáveis de ambiente

| Variável | Padrão | Para quê |
|---|---|---|
| `PORTA` | `8088` | porta do servidor Node |
| `RABBITMQ_URL` | `amqp://mq_landing:mq_landing@localhost:5672/plataforma` | RabbitMQ; conecta como `mq_landing`, porque o `user_id` das mensagens precisa bater com o usuário |
| `SERVIDOR_PROXY_ALVO` | `http://localhost:8088` | para onde o Vite repassa `/api/landing` em desenvolvimento |

## Estrutura de pastas

```
landing-web/
├── Dockerfile                  build da página + imagem só com o servidor Node
├── index.html                  página base (div #raiz)
├── vite.config.ts              porta 3008 e proxy de /api/landing
├── tsconfig.json               tipos da página (src/)
├── server/                     o "back-end do Grupo 7" de mentira
│   ├── index.ts                servidor Express: rotas /api/landing e entrega do build
│   ├── config.ts               configuração por variável de ambiente
│   ├── envelope.ts             envelope padrão {success, data, message, errors}
│   └── tsconfig.json           tipos do servidor (Node)
└── src/                        a landing page de um tenant de teste
    ├── main.tsx                renderiza o App
    ├── App.tsx                 página da "Empresa A" com o botão "Fale conosco"
    ├── AvisoPlaceholder.tsx    faixa amarela avisando que é placeholder, com o estado do servidor
    └── index.css               Tailwind e as mesmas cores da marca
```

## Arquivos

### `server/index.ts`

Servidor Express. Hoje tem:

- `GET /api/landing/health`: `200` com `{"status": "UP"}` no envelope;
- entrega da página: se `dist/` existir, como no container, serve os arquivos e manda qualquer outra rota para o `index.html`. Em desenvolvimento, quem entrega a página é o Vite.

É aqui que vão entrar a publicação dos eventos no RabbitMQ, a gravação dos envios, o `GET /api/landing/envios/{id}` e o `GET /api/landing/formularios/{id}/resumo`.

### `server/config.ts`

Objeto `config` com `porta` e `rabbitmqUrl`, lidos das variáveis de ambiente. O placeholder não chama a API do Marketing, assim como o Landing real.

### `server/envelope.ts`

Tipo `Resposta<T>` e as funções `ok(data)` e `erro(mensagem, erros?)`, no mesmo formato de envelope da plataforma.

### `src/App.tsx`

Landing page de um tenant de teste ("Empresa A · Consultoria"): chamada principal, botão **Fale conosco** e três cartões de benefícios. O botão abre o espaço onde o formulário vai aparecer. Hoje esse espaço mostra só um texto explicando que ali entra o formulário de exemplo do placeholder.

### `src/AvisoPlaceholder.tsx`

Faixa amarela no topo: "Placeholder do Landing (Grupo 7) para testes locais do Grupo 4", com o estado do servidor (`/api/landing/health`).

## O que falta (resumo)

Detalhes e dependências em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md), tarefas L1 a L5:

1. **Página e UTM (L1):** guardar a primeira e a última origem (UTM, `fbclid`, `gclid`, referenciador e página de entrada) e mostrar um formulário de exemplo, com o aceite de consentimento.
2. **Eventos (L2):** publicar em `landing.eventos`, com o envelope da §9.7, `moduloOrigem: "landing"` e `user_id = mq_landing`, os dois eventos do `landing.asyncapi.yaml`:

   | Quando | Evento | Etapa no Marketing |
   |---|---|---|
   | o chatbot de exemplo recebe e-mail ou telefone, com consentimento | `landing.chatbot.lead_capturado` (`sessaoId`, contato, origem, consentimento) | 1 |
   | o formulário é enviado | `landing.formulario.recebido` (`envioId`, `formularioId`, `canal`, `empresaId`, `contatoId`) | 3 |

   A etapa não vai no evento: quem decide é o Marketing.
3. **Envios (L3):** gravar as respostas e expor `GET /api/landing/envios/{id}` e `GET /api/landing/formularios/{id}/resumo`, no formato do `landing.yaml`.
4. **CRM (L4 e L5):** no envio, antes do evento, criar empresa, contato e oportunidade no stub do CRM, com `origem: landing` e `origemModuloId` igual ao `envioId`. O stub publica `crm.oportunidade.criada` em `crm.eventos`.

**Regras de desacoplamento:** o Marketing nunca importa código daqui, e este app nunca lê o schema `marketing`. Os dois conversam só por RabbitMQ e HTTP, como o Landing real faria. Se o Grupo 7 mudar um nome ou formato, atualize primeiro o contrato em `Docs/contratos/`, depois este placeholder e o consumidor do Marketing.
