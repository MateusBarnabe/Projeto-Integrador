# O que o Marketing precisa receber do Landing

*De: Grupo 4 — Marketing e Automações · Para: Grupo 7 — Landing Pages e Formulários*
*Rascunho de 24/09/2026 · Base: Contrato de Integração v0.7 (§9.7, §12.7) e PR #16 do Landing*

O Marketing não captura o visitante na landing page. Quem vê a visita, o clique, o preenchimento e o envio é o Landing. Sem esses avisos, o funil do Marketing não existe. Este documento diz **o que** precisamos receber, **quando** e **em que formato**, para cada etapa do lead.

> **"Etapa" aqui é sempre a etapa do funil do lead (0 a 3)**, definida na seção 1. Não tem relação com as fases do projeto do Grupo 4 (Etapa 1 e Etapa 2), que ficam no documento norteador.

## Resumo

- O Landing avisa cada fato por **evento no RabbitMQ**, na própria exchange `landing.eventos`. O Marketing liga uma fila a ela. Nenhuma chamada síncrona ao Marketing é necessária.
- São **cinco eventos**. Um deles já existe no PR #16 (`landing.formulario.recebido`) e precisa de campos a mais. Os nomes dos outros quatro são proposta; quem decide é o Landing, que é o dono deles.
- **Quem decide a etapa do lead (0 a 3) é o Landing**, e todo evento traz o campo `etapa`. Isso inclui o critério de "grande parte do formulário" da etapa 2.
- O evento carrega **contato e UTM**. As respostas completas do formulário o Marketing consulta na API do Landing, pelo `envioId`.
- Dois identificadores ligam as etapas: **`visitanteId`**, do primeiro acesso até o envio, e **`envioId`**, do primeiro preenchimento até o envio.
- Na etapa 3, o Landing continua criando ou reaproveitando **empresa e contato** no CRM, como no PR #16. **A oportunidade o Landing não cria**: quem cria é o Marketing.
- **O lead continua sendo gerido no módulo de Marketing em todas as etapas**, inclusive depois da etapa 3. O CRM recebe só empresa, contato e oportunidade (seção 7).
- **Os campos do formulário passam a ser definidos no Marketing** (seção 10). O Landing continua dono da página, do layout, do editor visual e das respostas; lê a lista de campos na nossa API.

## 1. Etapas do lead

Cada evento corresponde a uma etapa e traz o campo **`etapa`**, sempre com o valor da tabela abaixo. **Quem decide a etapa é o Landing**, porque é ele que vê o que o visitante fez. Isso inclui o critério da etapa 2: o Landing só publica `landing.formulario.atualizado` quando o preenchimento já é "grande parte" do formulário.

O Marketing grava a etapa recebida sem recalcular. A única regra do nosso lado é que a etapa nunca volta para trás: se chegar uma etapa menor do que a que o lead já tem, por atraso ou reentrega, ela só atualiza os dados complementares.

| Etapa (`etapa`) | O que o visitante fez | Evento esperado | Como o Marketing processa |
|---|---|---|---|
| 0 | Chegou por anúncio (com UTM de campanha) e não interagiu | `landing.visita.registrada` | em lote, até ~5 min |
| 0 | Clicou em "fale conosco", mas não preencheu nada | `landing.formulario.aberto` | em lote, até ~5 min |
| 1 | Preencheu os dados de contato: nome, telefone e e-mail | `landing.contato.informado` | em lote, até ~5 min |
| 2 | Preencheu grande parte do formulário, mas não enviou | `landing.formulario.atualizado` | na hora |
| 3 | Enviou o formulário completo | `landing.formulario.recebido` (PR #16) | na hora; o Marketing faz o handoff |

As etapas 0 e 1 têm volume alto e nenhuma pressa, então o Marketing guarda esses eventos e os processa em lote. As etapas 2 e 3 são processadas na chegada. Para o Landing o envio é igual nos dois casos: publicar o evento e seguir.

## 2. Como enviar

As regras são do Contrato (§9.7) e valem para qualquer evento do sistema:

- **Exchange:** `landing.eventos` (topic), com a chave de roteamento igual ao `tipo` do evento.
- **Envelope padrão:** `id` (uuid, chave de idempotência), `tipo`, `versao`, `tenantId`, `moduloOrigem: "landing"`, `ocorridoEm` em UTC, `usuarioId: null` (o visitante não é usuário), `correlacaoId` e `dados`.
- **`tenantId`:** o tenant da página, resolvido pelo subdomínio ou pelo slug (§12.6). Nunca vem de um parâmetro que o visitante consiga trocar.
- **Propriedade AMQP `user_id = mq_landing`** em toda mensagem.
- **Publicar depois do commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`).
- **Documentar no `contratos/landing.asyncapi.yaml`** antes de publicar. Deixamos um rascunho pronto em [landing-eventos-propostos.asyncapi.yaml](landing-eventos-propostos.asyncapi.yaml).

Do lado do Marketing: a fila `marketing.landing-leads` fica ligada a `landing.eventos` pelas cinco chaves. O consumidor é idempotente (o mesmo `id` entregue duas vezes produz um efeito só), e mensagens que falharem três vezes vão para `marketing.landing-leads.dlq`. Reenviar um evento é seguro.

## 3. Identificadores que ligam as etapas

| Campo | O que é | Desde | Regra |
|---|---|---|---|
| `visitanteId` | uuid do visitante, gerado pelo Landing no primeiro acesso | etapa 0 | O mesmo em todos os eventos daquele visitante, inclusive depois do envio. Sugestão: cookie first-party com validade de 90 dias |
| `envioId` | uuid do envio (o formulário daquele visitante) | etapa 1 | Criado no primeiro preenchimento e **o mesmo até o envio final**. Já existe no PR #16 |
| `formularioId` | uuid do formulário | etapa 0 (clique) | Já existe no PR #16. Com a seção 10, passa a ser o `id` do formulário no Marketing |
| `formularioVersao` | versão do formulário usada | etapa 1 | Para sabermos a que versão cada lead respondeu. Com a seção 10, é a versão publicada no Marketing |

Sem `visitanteId`, um clique na etapa 0 e o preenchimento na etapa 1 viram dois leads diferentes. Sem `envioId` estável, cada salvamento parcial vira um lead novo.

## 4. UTM e origem

Não fazemos o rastreamento de campanha, mas ele é **indispensável** para o Marketing: é o que diz de qual anúncio e de qual campanha veio cada lead. Pedimos em **todos os cinco eventos** o objeto `origem`:

| Campo | Tipo | Observação |
|---|---|---|
| `utmSource` | string ou null | `utm_source` da URL |
| `utmMedium` | string ou null | `utm_medium` |
| `utmCampaign` | string ou null | `utm_campaign` |
| `utmTerm` | string ou null | `utm_term` |
| `utmContent` | string ou null | `utm_content` |
| `fbclid` / `gclid` | string ou null | identificadores de clique do Meta e do Google, quando vierem na URL |
| `landingPageUrl` | string | URL da página, **sem** a query string |
| `referrer` | string ou null | página de onde o visitante veio |
| `primeiraVisitaEm` | date-time | quando esse `visitanteId` apareceu pela primeira vez |
| `canal` | enum | `formulario`, `landing_page`, `embed`, `chatbot`, como no PR #16 |

**Regra:** a UTM é capturada na chegada do visitante e **acompanha o `visitanteId`** nos eventos seguintes, mesmo que ele navegue para páginas sem UTM na URL. Se ele voltar depois por outro anúncio, os eventos novos levam a UTM nova. O Marketing guarda a primeira e não a sobrescreve com valor vazio.

## 5. Eventos

Em todos os exemplos, o envelope é o mesmo; só o `tipo` e o `dados` mudam. Todo `dados` começa pelo campo `etapa` (inteiro), com o valor fixo de cada evento.

### 5.1 `landing.visita.registrada` — etapa 0 · proposta

Publicar quando um visitante chega a uma landing page **com parâmetros de campanha** (qualquer `utm_*`, `fbclid` ou `gclid`). Visita orgânica sem campanha e sem clique **não** precisa ser enviada. Um evento por `visitanteId` por dia basta.

| Campo de `dados` | Obrigatório | Tipo |
|---|---|---|
| `etapa` | sim | inteiro, sempre `0` |
| `visitanteId` | sim | uuid |
| `paginaId` | sim | uuid da landing page |
| `origem` | sim | objeto da seção 4 |

```json
{
  "id": "0b8e6f3a-6d1c-4f7e-9a2b-1c3d4e5f6a7b",
  "tipo": "landing.visita.registrada",
  "versao": 1,
  "tenantId": "a0000000-0000-4000-8000-00000000000a",
  "moduloOrigem": "landing",
  "ocorridoEm": "2026-09-24T13:02:11Z",
  "usuarioId": null,
  "correlacaoId": "c41d9e2a",
  "dados": {
    "etapa": 0,
    "visitanteId": "7f3c1a9e-2b4d-4c8e-9f1a-3b5d7e9f1a2c",
    "paginaId": "5a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
    "origem": {
      "utmSource": "facebook", "utmMedium": "cpc", "utmCampaign": "black-friday-2026",
      "utmTerm": null, "utmContent": "carrossel-a",
      "fbclid": "IwAR3xYz", "gclid": null,
      "landingPageUrl": "https://centinela.exemplo.com.br/consultoria",
      "referrer": "https://www.facebook.com/",
      "primeiraVisitaEm": "2026-09-24T13:02:11Z",
      "canal": "landing_page"
    }
  }
}
```

### 5.2 `landing.formulario.aberto` — etapa 0 · proposta

Publicar quando o visitante clica em "fale conosco" (ou abre o formulário) **e ainda não preencheu nada**. Vale com ou sem UTM.

| Campo de `dados` | Obrigatório | Tipo |
|---|---|---|
| `etapa` | sim | inteiro, sempre `0` |
| `visitanteId` | sim | uuid |
| `formularioId` | sim | uuid |
| `paginaId` | não | uuid; nulo em embed ou chatbot |
| `origem` | sim | objeto da seção 4 |

### 5.3 `landing.contato.informado` — etapa 1 · proposta

Publicar quando o visitante termina de preencher **nome, telefone e e-mail**, antes de enviar o formulário. A partir daqui o lead existe de fato para o Marketing.

| Campo de `dados` | Obrigatório | Tipo |
|---|---|---|
| `etapa` | sim | inteiro, sempre `1` |
| `visitanteId` | sim | uuid |
| `envioId` | sim | uuid, criado aqui e mantido até o envio |
| `formularioId` / `formularioVersao` | sim | uuid / inteiro |
| `contato.nome` | sim | string |
| `contato.email` | sim | string (e-mail) |
| `contato.telefone` | sim | string, só dígitos, com DDI e DDD (E.164 sem o `+`) |
| `contato.whatsapp` | não | string, mesmo formato, quando o formulário tiver o campo |
| `consentimento.marketing` | sim | boolean: o visitante aceitou receber comunicação de marketing |
| `consentimento.registradoEm` | sim | date-time |
| `origem` | sim | objeto da seção 4 |

```json
"dados": {
  "etapa": 1,
  "visitanteId": "7f3c1a9e-2b4d-4c8e-9f1a-3b5d7e9f1a2c",
  "envioId": "3e9d2c1b-8a7f-4e6d-9c5b-4a3f2e1d0c9b",
  "formularioId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
  "formularioVersao": 3,
  "contato": {
    "nome": "Maria Souza",
    "email": "maria.souza@exemplo.com.br",
    "telefone": "5511987654321",
    "whatsapp": null
  },
  "consentimento": { "marketing": true, "registradoEm": "2026-09-24T13:04:40Z" },
  "origem": { "...": "objeto da seção 4" }
}
```

**Por que o consentimento:** o Marketing só manda e-mail de aquecimento para quem aceitou (LGPD, seção 64 do Prompt Mestre). Sem esse campo, não podemos enviar nada para o lead.

### 5.4 `landing.formulario.atualizado` — etapa 2 · proposta

Publicar quando o visitante já preencheu **grande parte do formulário** depois do contato, sem enviá-lo. **O critério de "grande parte" é do Landing**: o evento só é publicado quando o preenchimento já atende a esse critério. Antes disso, o visitante continua na etapa 1 e nada precisa ser enviado. Pedimos só que o critério nos seja informado (seção 9), para o painel do Marketing explicar o que conta como etapa 2.

**Frequência sugerida:** depois do primeiro evento, novos salvamentos do mesmo envio são agrupados (debounce) em no máximo **um evento a cada 30 s por `envioId`**, mais um quando o visitante sai da página. Isso mantém contato e consentimento atualizados sem um evento por tecla.

| Campo de `dados` | Obrigatório | Tipo |
|---|---|---|
| `etapa` | sim | inteiro, sempre `2` |
| `visitanteId` / `envioId` | sim | uuid |
| `formularioId` / `formularioVersao` | sim | uuid / inteiro |
| `contato` | sim | objeto da 5.3, com os valores atuais (o visitante pode ter corrigido) |
| `consentimento` | sim | objeto da 5.3 |
| `origem` | sim | objeto da seção 4 |

As respostas em si **não** vão no evento. Se precisarmos delas, consultamos na API pelo `envioId` (seção 6).

### 5.5 `landing.formulario.recebido` — etapa 3 · já no PR #16, com campos a mais

O evento do PR #16 continua como está, publicado depois que o Landing cria ou reaproveita empresa e contato no CRM. Pedimos **acrescentar** estes campos. Como o PR ainda não foi mergeado, entram direto na `versao: 1`:

| Campo de `dados` | Situação | Tipo |
|---|---|---|
| `envioId`, `formularioId`, `empresaId`, `contatoId`, `canal` | já existem | — |
| `etapa` | **acrescentar** | inteiro, sempre `3` |
| `visitanteId` | **acrescentar** | uuid |
| `formularioVersao` | **acrescentar** | inteiro |
| `contato` | **acrescentar** | objeto da 5.3 |
| `consentimento` | **acrescentar** | objeto da 5.3 |
| `origem` | **acrescentar** | objeto da seção 4 (o `canal` passa a vir aqui também) |

## 6. Consulta das respostas (API do Landing)

As respostas do formulário não trafegam no evento. O Marketing as consulta quando precisa, com **token de serviço**, direto pelo nome do container (§9.2):

```
GET http://landing:8088/api/landing/envios/{envioId}
Authorization: Bearer {token de serviço do marketing}
X-Tenant-Id: {tenantId do evento}
```

Precisamos que o Landing:

1. Publique este endpoint no `contratos/landing.yaml`, também para envios **ainda não enviados** (etapas 1 e 2). Na etapa 2 usamos as respostas parciais para calcular a pontuação do lead.
2. Acrescente `servicos: [marketing]` na permissão `landing.envio.ver`, em `permissoes/landing.yaml`. Sem isso, o token de serviço do Marketing recebe `403`.

O que precisamos na resposta:

```json
{
  "success": true,
  "data": {
    "envioId": "3e9d2c1b-8a7f-4e6d-9c5b-4a3f2e1d0c9b",
    "formularioId": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
    "formularioVersao": 3,
    "status": "rascunho",
    "respostas": [
      { "campoId": "c1a2b3c4-...", "label": "Quantos funcionários?", "tipo": "lista", "valor": "11 a 50" },
      { "campoId": "d5e6f7a8-...", "label": "CNPJ", "tipo": "cnpj", "valor": "28954678000122" }
    ],
    "atualizadoEm": "2026-09-24T13:06:02Z"
  },
  "message": null,
  "errors": []
}
```

`status` é `rascunho` ou `enviado`. `tipo` segue os tipos da seção 56 do Prompt Mestre: texto, email, telefone, whatsapp, cnpj, cpf, lista, checkbox, radio, textarea, data. Com a seção 10, `campoId` é o `id` do campo na definição do Marketing; pedimos também a `chave` do campo em cada resposta.

## 7. Etapa 3 do funil: o que vai para o CRM

O lead continua sendo gerido no módulo de Marketing em todas as etapas: painel, lista, pontuação e vendedor. Depois da etapa 3, ele segue aparecendo lá, com o link da oportunidade. O CRM recebe só a empresa, o contato e a oportunidade, que são dele pela regra do dono único (§10).

| Passo | Quem faz |
|---|---|
| Criar ou reaproveitar a **empresa** no CRM (`POST /api/crm/empresas`, tratando o `409`) | Landing, como no PR #16 |
| Criar ou reaproveitar o **contato** no CRM (`POST /api/crm/contatos`, tratando o `409`) | Landing, como no PR #16 |
| Publicar `landing.formulario.recebido` com `empresaId` e `contatoId` | Landing |
| Criar a **oportunidade** no CRM | **Marketing**, ao consumir o evento |

**O Landing não deve criar a oportunidade.** O §12.7 do contrato mostra o Landing chamando `POST /api/crm/oportunidades`, mas nesse fluxo quem cria é o Marketing. Se os dois criarem, cada lead gera duas oportunidades no funil do CRM.

## 8. O que não precisa mandar

- Visita orgânica, sem campanha e sem clique.
- Respostas do formulário dentro do evento: elas ficam na API (seção 6).
- CPF, documentos, senha ou qualquer dado sensível no evento. Mensagem fica em fila, em log e na `.dlq` (§9.7).
- Evento a cada tecla: use o agrupamento da 5.4.
- `landing.formulario.atualizado` antes de o preenchimento atingir o critério da etapa 2.

## 9. Pontos a combinar

| Ponto | Proposta do Marketing |
|---|---|
| Nomes finais dos quatro eventos novos | Os da seção 5. O dono é o Landing, que pode renomear; só precisamos saber antes da implementação |
| Critério da etapa 2 ("grande parte do formulário") | Definido e aplicado pelo Landing. Precisamos só conhecer a regra, para documentá-la no painel do Marketing |
| CRM fora do ar na etapa 3 | Publicar `landing.formulario.recebido` mesmo assim, com `empresaId` e `contatoId` nulos, e publicar de novo com os ids quando o CRM voltar. O Marketing só cria a oportunidade quando tiver `empresaId` |
| Validade do `visitanteId` | 90 dias, em cookie first-party |
| Visitante que muda de e-mail entre etapas | O Marketing liga pelo `visitanteId` e pelo `envioId`, não pelo e-mail |
| Texto do consentimento de marketing | Definido pelo Landing no formulário; só precisamos do booleano e da data |
| Quem define os campos do formulário | O Marketing (seção 10). O Landing fica com layout, estilo, editor visual e respostas |

## 10. Campos do formulário vêm do Marketing

O Marketing (Grupo 4) passa a definir **quais campos** o formulário da landing page pergunta. A pessoa gestora de marketing escolhe as perguntas no módulo de Marketing, porque são elas que qualificam o lead (pontuação e faixa MQL) e aparecem no painel. Isso muda o que o Mapa de Fronteiras previa para a seção 56 do Prompt Mestre, por isso precisa do aceite de vocês.

**Continua com o Landing:** a página, o layout, o estilo, o editor visual de arrastar e soltar, a exibição do formulário, a validação no navegador e a gravação das respostas (envios).

**Passa para o Marketing:** a lista de campos, com rótulo, tipo, chave, obrigatoriedade, ordem e opções, e as versões publicadas.

**Parte fixa:** nome, e-mail, telefone e o aceite de comunicação de marketing estão sempre na definição, marcados com `fixo: true`. São os dados que o Landing usa para publicar `landing.contato.informado` (5.3).

**Como ler a definição:**

```
GET http://marketing:8087/api/marketing/formularios/{formularioId}
Authorization: Bearer {token de serviço do landing}
X-Tenant-Id: {tenantId da página}
```

```json
{
  "success": true,
  "data": {
    "id": "9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
    "nome": "Consultoria — Black Friday",
    "versao": 3,
    "publicadoEm": "2026-09-20T18:00:00Z",
    "campos": [
      { "id": "a1b2c3d4-...", "chave": "nome", "label": "Nome", "tipo": "texto", "obrigatorio": true, "fixo": true, "ordem": 1, "opcoes": null },
      { "id": "c1a2b3c4-...", "chave": "cargo", "label": "Qual o seu cargo?", "tipo": "lista", "obrigatorio": true, "fixo": false, "ordem": 5, "opcoes": ["Decisor", "Sócio", "Gerente", "Analista", "Operacional", "Outros"] }
    ]
  },
  "message": null,
  "errors": []
}
```

- A permissão `marketing.formulario.ver` declara `servicos: [landing]`, então o token de serviço do Landing é aceito.
- A resposta é sempre a **versão ativa**. O Landing pode guardá-la em cache pela `versao`.
- Quando uma versão nova é publicada, o Marketing publica **`marketing.formulario.publicado`** em `marketing.eventos`, com `formularioId` e `versao`. O Landing liga uma fila a esse evento e renova o cache.
- Nos eventos da seção 5, `formularioId` e `formularioVersao` passam a ser os do Marketing. Nas respostas (seção 6), `campoId` é o `id` do campo acima, e pedimos a `chave` junto.
- Um visitante que começou a preencher a versão 3 termina na versão 3, mesmo que a 4 seja publicada no meio. O `formularioVersao` do envio não muda.

## 11. Resumo para o PR do Landing no `infra-integrador-2026`

- [ ] `contratos/landing.asyncapi.yaml`: quatro eventos novos e campos novos em `landing.formulario.recebido` (rascunho em [landing-eventos-propostos.asyncapi.yaml](landing-eventos-propostos.asyncapi.yaml))
- [ ] `contratos/landing.yaml`: `GET /api/landing/envios/{envioId}`, com `chave` em cada resposta
- [ ] `permissoes/landing.yaml`: `servicos: [marketing]` em `landing.envio.ver`
- [ ] Ler a definição do formulário em `GET /api/marketing/formularios/{formularioId}` e ouvir `marketing.formulario.publicado` (seção 10)
