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

> **Estado atual (06/11/2026): L1 a L4 feitas.** A página guarda a primeira e a última origem e mostra o formulário de exemplo e um chatbot de exemplo, com o aceite de consentimento. Os envios ficam em memória; no envio, o placeholder cria ou reaproveita empresa e contato e cria a oportunidade no stub do CRM ([`crm-stub/`](../crm-stub/)) e só então publica `landing.formulario.recebido`, com `empresaId` e `contatoId`. As duas rotas lidas pelo Marketing respondem no formato do `landing.yaml`, exigindo `Authorization: Bearer` e `X-Tenant-Id` (decisão X10). Plano em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

| | |
|---|---|
| Stack | página em React 19 + Vite 8 + Tailwind v4; servidor Node 24 + Express 5 rodando `.ts` direto, sem build |
| Portas | `3008` (Vite, só em desenvolvimento) e `8088` (servidor Node) |
| Rotas | `/api/landing/**` e a superfície pública `/public/landing/**` |
| Regras | não precisa seguir o Design System nem o checklist da plataforma; é ferramenta de teste e deve continuar pequeno |

## Como rodar

```bash
npm install
npm run dev          # página em http://localhost:3008 e servidor em http://localhost:8088
```

Da raiz do repositório: `npm run dev:landing`. Em desenvolvimento, o Vite repassa `/api/landing` e `/public/landing` para o servidor Node.

Para simular a chegada por um anúncio, abra a página com os parâmetros na URL, por exemplo
`http://localhost:3008/?utm_source=instagram&utm_medium=cpc&utm_campaign=black-friday-2026&fbclid=abc123`.
O rodapé mostra a primeira e a última origem guardadas e tem um botão para esquecê-las.

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

Em container, roda **um processo só**: o servidor Node entrega a página (o build em `dist/`) e a API na porta `8088`. Abra http://localhost:8088. O compose sobe junto o stub do CRM (`crm`), que o placeholder chama em `http://crm:8082`. Fora do Docker, rode o stub com `npm run dev:crm` na raiz; sem ele, o envio funciona do mesmo jeito, só sem os ids do CRM.

## Variáveis de ambiente

| Variável | Padrão | Para quê |
|---|---|---|
| `PORTA` | `8088` | porta do servidor Node |
| `RABBITMQ_URL` | `amqp://mq_landing:mq_landing@localhost:5672/plataforma` | RabbitMQ; conecta como `mq_landing`, porque o `user_id` das mensagens precisa bater com o usuário |
| `CRM_BASE_URL` | `http://localhost:8082` | API do CRM (o stub local ou o real), chamada direto, sem gateway; no `docker-compose.yml`, `http://crm:8082` |
| `SERVIDOR_PROXY_ALVO` | `http://localhost:8088` | para onde o Vite repassa `/api/landing` e `/public/landing` em desenvolvimento |
| `VITE_MARKETING_URL` | `http://localhost:8087` | API do Marketing de onde a página carrega o `rastreamento.js` (R3). Lida no build do Vite |

## Estrutura de pastas

```
landing-web/
├── Dockerfile                  build da página + imagem só com o servidor Node
├── index.html                  página base (div #raiz)
├── vite.config.ts              porta 3008 e proxy de /api/landing e /public/landing
├── tsconfig.json               tipos da página (src/)
├── server/                     o "back-end do Grupo 7" de mentira
│   ├── index.ts                servidor Express: rotas /api/landing, /public/landing e entrega do build
│   ├── formulario.ts           o formulário de exemplo (tenant Empresa A), fixo no código
│   ├── validacao.ts            validação das respostas por tipo de campo e normalização da origem
│   ├── envios.ts               envios gravados, em memória, e o formato `Envio` do contrato
│   ├── crm.ts                  empresa, contato e oportunidade no CRM, antes do evento
│   ├── chatbot.ts              sessões do chatbot de exemplo
│   ├── eventos.ts              os dois eventos landing.*
│   ├── mensageria.ts           conexão com o RabbitMQ e envelope da §9.7
│   ├── config.ts               configuração por variável de ambiente
│   ├── envelope.ts             envelope padrão {success, data, message, errors}
│   └── tsconfig.json           tipos do servidor (Node)
└── src/                        a landing page de um tenant de teste
    ├── main.tsx                renderiza o App
    ├── App.tsx                 página da "Empresa A" com o botão "Fale conosco" e o rodapé de origem
    ├── FormularioExemplo.tsx   formulário renderizado a partir do schema público, com o consentimento
    ├── ChatbotExemplo.tsx      chatbot de exemplo (dispara a captura e o envio do canal chatbot)
    ├── origem.ts               primeira e última origem (UTM, fbclid, gclid, referenciador, página de entrada)
    ├── api.ts                  chamadas à superfície pública e tipos do contrato
    ├── AvisoPlaceholder.tsx    faixa amarela avisando que é placeholder, com o estado do servidor
    └── index.css               Tailwind e as mesmas cores da marca
```

## Arquivos

### `server/index.ts`

Servidor Express. Hoje tem:

- `GET /api/landing/health`: `200` com `{"status": "UP"}` no envelope;
- `GET /public/landing/formularios/{chavePublica}`: schema público do formulário (`FormularioPublico` do `landing.yaml`), sem `mapeamentoCrm`;
- `POST /public/landing/formularios/{chavePublica}/envios`: corpo `EnvioPublico` (`respostas` como mapa `campoId → valor`, `consentimento.aceito`, `paginaSlug`, `origem {primeira, ultima}`). Sem aceite responde `400 CONSENTIMENTO_OBRIGATORIO`; campo inválido, `400` com `CAMPO_OBRIGATORIO`, `EMAIL_INVALIDO`, `TELEFONE_INVALIDO`, `CNPJ_INVALIDO` ou `OPCAO_INVALIDA`. Grava o envio com o texto do termo vigente e a data do aceite, e responde só a `mensagemSucesso` (a superfície pública não devolve o id do envio). Com `paginaSlug = empresa-a-consultoria` o canal é `landing_page`; sem ele, `formulario`. Depois de gravar, chama o CRM e publica `landing.formulario.recebido` (abaixo), sem segurar a resposta ao visitante;
- `POST /public/landing/formularios/{chavePublica}/sessoes`, `.../sessoes/{sessaoId}/consentimento`, `.../respostas` e `.../concluir`: o chatbot de exemplo, devolvendo o `PassoChatbot` do contrato. A resposta que traz e-mail ou telefone com o aceite publica `landing.chatbot.lead_capturado`; o `concluir` grava o envio com canal `chatbot` e publica `landing.formulario.recebido`;
- `GET /api/landing/envios/{id}` e `GET /api/landing/formularios/{id}/resumo`: as rotas lidas pelo Marketing (abaixo);
- entrega da página: se `dist/` existir, como no container, serve os arquivos e manda qualquer outra rota para o `index.html`. Em desenvolvimento, quem entrega a página é o Vite.

### `server/formulario.ts`

O formulário de exemplo: chave pública `fk_empresaA01`, id `a0000000-0000-4000-8000-0000000000f1`, do tenant Empresa A (`a0000000-0000-4000-8000-00000000000a`, o mesmo do identity em modo dev), com nove campos (nome, e-mail, telefone, empresa, CNPJ, porte, interesses, prazo e mensagem), o texto de consentimento e a landing page de teste (`empresa-a-consultoria`). O formulário é do Landing: o Marketing nunca o define.

### `server/envios.ts` e `server/validacao.ts`

Os envios ficam num `Map` em memória e somem quando o servidor reinicia (decisão X10: é ferramenta de teste; para reproduzir um cenário, basta enviar de novo). O consentimento guarda o texto e a data do aceite, como o `ConsentimentoRegistrado` do contrato; IP e *User-Agent* não são guardados nem logados. A origem é reduzida aos campos do `ToqueOrigem` do contrato: o `fbclid` e o `gclid` não têm campo próprio lá e chegam dentro da `paginaEntrada`.

### `server/crm.ts` (handoff para o CRM)

Depois de gravar o envio (formulário ou conclusão do chatbot) e **antes** de publicar `landing.formulario.recebido`, o placeholder faz o que o `landing.yaml` ("Como o CRM recebe o lead") e o Contrato §12.7 pedem ao Landing, chamando `CRM_BASE_URL` direto, com `X-Tenant-Id` do formulário, `Authorization: Bearer placeholder-landing` (o stub não confere o token), o `X-Request-Id` do envio e timeout de 3 s por chamada:

| Passo | Corpo (`crm.yaml`) | Duplicidade |
|---|---|---|
| `POST /api/crm/empresas` | campos com `mapeamentoCrm = empresa.*` (`empresa.nome` vira `razaoSocial`, `empresa.cnpj` vai só com dígitos), `origem: landing`, `origemModuloId` = id do envio | `409 EMPRESA_DUPLICADA` (mesmo CNPJ no tenant): usa o `data.empresaId` devolvido |
| `POST /api/crm/contatos` | campos com `mapeamentoCrm = contato.*` (nome, e-mail, telefone), `empresaId`, `consentimentoLgpd: concedido`, UTMs da última origem, `origem: landing`, `origemModuloId` = id do envio | `409 CONTATO_DUPLICADO` (mesmo e-mail na empresa): usa o `data.contatoId` devolvido |
| `POST /api/crm/oportunidades` | `titulo: "Formulário: <nome do formulário>"` (sem dado pessoal, porque vai no evento do CRM), `empresaId`, `contatoId`, `tipo: nova`, `origem: landing`, **`origemModuloId` = id do envio** | — (cada envio abre uma oportunidade) |

O stub publica `crm.oportunidade.criada` ao criar a oportunidade, então esse evento sai **antes** do `landing.formulario.recebido` do mesmo envio, um dos cenários que o Marketing precisa tratar. Os ids de empresa e contato ficam no envio (`GET /api/landing/envios/{id}`) e vão no evento.

**CRM fora do ar, lento (mais de 3 s) ou recusando** (`400`, `5xx`): para no passo que falhou, o envio continua gravado e o evento sai do mesmo jeito, com o que se conseguiu até ali (normalmente `empresaId` e `contatoId` nulos), como diz o `landing.yaml`. O log diz só o id do envio, o passo e o motivo (`ECONNREFUSED`, `sem resposta em 3000 ms` ou `HTTP 400 (campo:CODIGO)`), sem nenhum dado do visitante. Não há nova tentativa: o Landing real também não descreve uma.

**CNPJ:** o CRM só reconhece empresa repetida pelo CNPJ. Por isso o formulário de exemplo tem o campo opcional **CNPJ da empresa** (tipo `CNPJ`, `mapeamentoCrm: empresa.cnpj`, validado com os dígitos verificadores; `400 CNPJ_INVALIDO`). Com ele, reenviar com o mesmo CNPJ e e-mail reaproveita empresa e contato; sem ele, cada envio cria uma empresa nova (e, por consequência, um contato novo), como aconteceria no CRM real. CNPJ válido para teste: `11.222.333/0001-81`.

### `server/chatbot.ts`

Sessões do chatbot de exemplo, em memória. As perguntas são os campos do formulário, na ordem; o consentimento é pedido antes da primeira pergunta de contato. Na primeira resposta com e-mail ou telefone válido, já com o aceite, marca a captura (uma vez por sessão).

### `server/mensageria.ts` e `server/eventos.ts`

Publicação em `landing.eventos` com `amqplib`, num canal com confirmação do broker, aberto na primeira publicação e refeito se a conexão cair. O evento sai depois de gravar o fato e não segura a resposta ao visitante. Se o RabbitMQ estiver fora, o evento se perde e o log registra só tipo, id e tenant, nunca os `dados`.

Envelope da §9.7: `id`, `tipo`, `versao: 1`, `tenantId` (do formulário), `moduloOrigem: "landing"`, `ocorridoEm` em UTC, `usuarioId: null` (superfície pública), `correlacaoId` (`X-Request-Id`, ou um uuid novo) e `dados`. Routing key igual ao `tipo`. Propriedades AMQP: `user_id` igual ao usuário da `RABBITMQ_URL` (`mq_landing`), `message_id` igual ao `id`, `type` igual ao `tipo`, `correlation_id`, `content_type: application/json` e entrega persistente.

| Quando | Evento | `dados` |
|---|---|---|
| a conversa do chatbot recebe e-mail ou telefone, com o aceite | `landing.chatbot.lead_capturado` | `sessaoId`, `formularioId`, `paginaId`, `contato {nome, email, telefone}`, `origem {utmSource, utmMedium, utmCampaign}` (última origem), `consentimento {aceito: true, aceitoEm}` |
| o formulário é enviado, ou a conversa é concluída, depois da chamada ao CRM | `landing.formulario.recebido` | `envioId`, `formularioId`, `empresaId`, `contatoId` (do CRM; `null` se ele não respondeu), `canal` (`landing_page`, `formulario` ou `chatbot`) |

A etapa do funil não vai no evento: quem decide é o Marketing.

### Rotas lidas pelo Marketing

| Rota | Resposta |
|---|---|
| `GET /api/landing/envios/{id}` | `Envio` do `landing.yaml`: `nome`, `email`, `canal`, `paginaId`, `recebidoEm`, `anonimizado`, `respostas[] {campoId, rotulo, tipo, valor}` na ordem dos campos, `origem {primeira, ultima}`, `consentimento {aceito, texto, aceitoEm}`, `empresaId`, `contatoId` |
| `GET /api/landing/formularios/{id}/resumo` | `Formulario`: nome, status, modo, texto do termo e `campos[] {id, ordem, rotulo, tipo, obrigatorio, placeholder, opcoes}`, sem chave pública nem `mapeamentoCrm` |

As duas são as únicas que aceitam token de serviço no Landing real (`clientId` `marketing`, chamada direta em `landing:8088`, §9.2). Aqui, pela **decisão X10**:

- exigem `Authorization: Bearer <token>` e `X-Tenant-Id`; sem um ou outro, `401` no envelope, com `NAO_AUTENTICADO` no campo que faltou;
- **não conferem a assinatura, a validade nem as permissões do token**: qualquer valor serve. Quem valida pelo JWKS do identity é o Landing real; o placeholder só garante que o Marketing manda o cabeçalho;
- o tenant vem do `X-Tenant-Id`, e registro de outro tenant responde `404`.

O id do envio sai no evento e no log do servidor (`Envio <id> gravado ...`).

```bash
curl -H 'Authorization: Bearer qualquer' -H 'X-Tenant-Id: a0000000-0000-4000-8000-00000000000a' \
  http://localhost:8088/api/landing/envios/<envioId>
```

Diferenças conhecidas em relação ao `landing.yaml`: o `Formulario` do resumo não traz `criadoPor`/`atualizadoPor` (o formulário de exemplo não tem autor) nem `chavePublica` (a rota não devolve a chave de embed); `403` nunca acontece, porque as permissões do token não são lidas.

### `server/config.ts`

Objeto `config` com `porta`, `rabbitmqUrl` e `crmBaseUrl`, lidos das variáveis de ambiente. O placeholder não chama a API do Marketing, assim como o Landing real.

### `server/envelope.ts`

Tipo `Resposta<T>` e as funções `ok(data)` e `erro(mensagem, erros?)`, no mesmo formato de envelope da plataforma.

### `src/App.tsx`

Landing page de um tenant de teste ("Empresa A · Consultoria"): chamada principal, botão **Fale conosco**, que abre o formulário de exemplo, três cartões de benefícios e a seção **Planos e preços**. No rodapé, a primeira e a última origem guardadas e o `mkt_visita` da visita de anúncio, quando a página veio de um link de campanha do Marketing. Os botões e as seções têm `data-rastreio` e `data-rastreio-secao`, que o script do Marketing usa para nomear cliques e seções vistas.

### `src/rastreamentoMarketing.ts`

Simula a tag que pedimos ao Landing (pedido 6 do [landing-requisitos.md](../Docs/contratos/landing-requisitos.md)): carrega `VITE_MARKETING_URL/public/marketing/rastreamento.js` na página. O script é do Marketing e só age quando a URL tem o `mkt_visita` (lead 0, ver [Docs/demo-iot.md](../Docs/demo-iot.md)). No Landing real, é uma linha fixa no HTML.

### `src/origem.ts`

Guarda no `localStorage` a primeira e a última origem do visitante (UTM, `fbclid`, `gclid`, referenciador e página de entrada), como o `form.js` do Landing. A primeira nunca muda; a última só é trocada por uma visita com campanha ou vinda de outro site, para uma volta direta não apagar o anúncio que trouxe a pessoa.

### `src/FormularioExemplo.tsx`

Lê o schema público e renderiza os campos por tipo, com a caixa de aceite mostrando o texto do termo. Envia as respostas, o aceite, o slug da página e a origem, e mostra os erros devolvidos por campo.

### `src/ChatbotExemplo.tsx`

Botão flutuante **Converse com a gente**, que abre uma conversa pelas rotas de sessão. Pergunta os campos do formulário na ordem, pede o aceite do termo antes do e-mail, aceita pular campo opcional (resposta vazia) e, no fim, envia as respostas com **Enviar respostas**.

### `src/AvisoPlaceholder.tsx`

Faixa amarela no topo: "Placeholder do Landing (Grupo 7) para testes locais do Grupo 4", com o estado do servidor (`/api/landing/health`).

## O que falta (resumo)

Detalhes e dependências em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md), tarefas L1 a L5:

1. ~~**Página e UTM (L1)**~~ e ~~**Eventos (L2)**~~: feitas.
2. ~~**Envios (L3)**~~: feita. Envios em memória; as duas rotas exigem Bearer e `X-Tenant-Id`, sem conferir a assinatura (X10).
3. ~~**CRM (L4 e L5)**~~: feitas. No envio, antes do evento, empresa, contato e oportunidade no stub do CRM, com `origem: landing` e `origemModuloId` igual ao `envioId`; o stub publica `crm.oportunidade.criada` em `crm.eventos`.
4. ~~**Script do Marketing (R3)**~~: feita. A página carrega o `rastreamento.js` e guarda a URL de entrada inteira (com o `mkt_visita`) na origem do envio.

**Regras de desacoplamento:** o Marketing nunca importa código daqui, e este app nunca lê o schema `marketing`. Os dois conversam só por RabbitMQ e HTTP, como o Landing real faria. Se o Grupo 7 mudar um nome ou formato, atualize primeiro o contrato em `Docs/contratos/`, depois este placeholder e o consumidor do Marketing.
