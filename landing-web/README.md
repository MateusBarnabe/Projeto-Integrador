# landing-web (placeholder do Landing)

> **Este app não é o nosso módulo.** A landing page, o formulário público e os eventos do funil são do **módulo Landing, do Grupo 7**. Este é um *placeholder*: uma imitação mínima e descartável, que cumpre o mesmo contrato que pedimos ao Grupo 7 e serve só para testar localmente a jornada inteira do lead.

A jornada que ele permite testar:

1. o visitante chega pelo anúncio, com UTM na URL;
2. clica em "Fale conosco" e preenche o formulário, cujos campos foram montados no construtor do Marketing;
3. cada passo publica um evento no RabbitMQ (`landing.*`), que o Marketing consome;
4. no envio, o Marketing cria a oportunidade no CRM.

Quando o Landing real existir, basta desligar este app e apontar o Marketing para o módulo do Grupo 7. O placeholder usa o mesmo nome de serviço (`landing`), a mesma porta (`8088`) e as mesmas rotas (`/api/landing/**`), então **o código do Marketing não muda**.

O contrato que ele imita:

- [Docs/contratos/landing-requisitos.md](../Docs/contratos/landing-requisitos.md): o que pedimos ao Grupo 7;
- [Docs/contratos/landing-eventos-propostos.asyncapi.yaml](../Docs/contratos/landing-eventos-propostos.asyncapi.yaml): os cinco eventos.

> **Estado atual (25/09/2026): esqueleto.** A página de exemplo e o `/api/landing/health` funcionam. O formulário, os eventos e a gravação dos envios ainda não existem (tarefas L1 a L5 em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md)).

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
| `MARKETING_API_URL` | `http://localhost:8087` | API do Marketing, de onde vem a definição do formulário |
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

É aqui que vão entrar a publicação dos eventos no RabbitMQ, a gravação dos envios e o `GET /api/landing/envios/{envioId}`.

### `server/config.ts`

Objeto `config` com `porta`, `marketingApiUrl` e `rabbitmqUrl`, lidos das variáveis de ambiente.

### `server/envelope.ts`

Tipo `Resposta<T>` e as funções `ok(data)` e `erro(mensagem, erros?)`, no mesmo formato de envelope da plataforma.

### `src/App.tsx`

Landing page de um tenant de teste ("Empresa A · Consultoria"): chamada principal, botão **Fale conosco** e três cartões de benefícios. O botão abre o espaço onde o formulário vai aparecer. Hoje esse espaço mostra só um texto explicando que ali entra o formulário montado a partir da definição publicada no Marketing.

### `src/AvisoPlaceholder.tsx`

Faixa amarela no topo: "Placeholder do Landing (Grupo 7) para testes locais do Grupo 4", com o estado do servidor (`/api/landing/health`).

## O que falta (resumo)

Detalhes e dependências em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md), tarefas L1 a L5:

1. **Visitante e UTM:** gerar o `visitanteId` num cookie e guardar UTM, `fbclid` e `gclid` da URL.
2. **Eventos:** publicar os cinco eventos em `landing.eventos`, com o envelope da §9.7, `moduloOrigem: "landing"` e `user_id = mq_landing`:

   | Quando | Evento | Etapa do funil |
   |---|---|---|
   | chegou pela URL com UTM | `landing.visita.registrada` | 0 |
   | clicou em "Fale conosco" | `landing.formulario.aberto` | 0 |
   | preencheu nome, telefone e e-mail | `landing.contato.informado` | 1 |
   | preencheu "grande parte" (ex.: 50% dos campos) | `landing.formulario.atualizado` | 2 |
   | enviou | `landing.formulario.recebido` | 3 |

3. **Formulário a partir do Marketing:** ler `GET /api/marketing/formularios/{id}`, com cache pela versão, e desenhar os campos fixos (nome, e-mail, telefone e aceite de marketing, com a versão do termo) e os configuráveis.
4. **Envios:** gravar as respostas e expor `GET /api/landing/envios/{envioId}`.
5. **Etapa 3:** criar ou reaproveitar empresa e contato no CRM (stub). **Nunca** criar a oportunidade, porque isso é do Marketing.

**Regras de desacoplamento:** o Marketing nunca importa código daqui, e este app nunca lê o schema `marketing`. Os dois conversam só por RabbitMQ e HTTP, como o Landing real faria. Se o Grupo 7 mudar um nome ou formato, atualize primeiro o contrato em `Docs/contratos/`, depois este placeholder e o consumidor do Marketing.
