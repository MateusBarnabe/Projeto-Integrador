# Marketing ← Landing: o que consumimos e o que ainda pedimos

**Para:** Grupo 7 (Landing Pages e Formulários) · **De:** Grupo 4 (Marketing) · **Revisão:** 09/11/2026 (pedidos 6 e 7, rastreamento do anúncio)

Este documento substitui a proposta de 24/09, que pedia cinco eventos com a etapa do funil e formulários definidos pelo Marketing. Seguimos o contrato que vocês publicaram no `infra-integrador-2026` (`contratos/landing.yaml` e `contratos/landing.asyncapi.yaml`, PR #26): **o Landing é o dono de formulários, campos e envios, e o Marketing só consome.** Do nosso lado, formulários já saíram do `marketing.yaml`, das permissões e do menu (PR #29).

## 1. O que já usamos do contrato de vocês

| O quê | Como o Marketing usa |
|---|---|
| `landing.formulario.recebido` | fila `marketing.landing-leads`, ligada a `landing.eventos`. Cada envio vira um lead na etapa 3 do nosso funil, processado na chegada |
| `landing.chatbot.lead_capturado` | mesma fila. Vira um lead na etapa 1 (contato deixado no chatbot, conversa ainda aberta) |
| `GET /api/landing/envios/{id}` (`landing.envio.ver`) | lido com o token de serviço do `marketing`, direto em `http://landing:8088`, com `X-Tenant-Id`, logo depois do evento. Guardamos a cópia das respostas no lead, como registro do que a pessoa respondeu |
| `GET /api/landing/formularios/{id}/resumo` (`landing.formulario.ver_resumo`) | mesmo jeito. Usado na nossa tela de regras de pontuação, para a pessoa gestora escolher o campo de cada regra |

Se o Landing não responder em 3 s, o lead fica gravado com a leitura pendente e tentamos de novo a cada minuto. Envio anonimizado (`anonimizado = true`) não apaga a cópia que já temos: ela segue a retenção do Marketing.

## 2. O que ainda pedimos

| # | Pedido | Por quê | Prioridade |
|---|---|---|---|
| 1 | **`sessaoId` em `landing.formulario.recebido`**, quando o envio vem de uma sessão (chatbot ou passo a passo) | O AsyncAPI diz que o envio do chatbot traz o mesmo `sessaoId` do `lead_capturado`, mas o schema de `FormularioRecebido` não tem o campo. Sem ele, ligamos a captura ao envio só pelo `contatoId` ou pelo e-mail, e uma pessoa que muda o e-mail no meio vira dois leads | Alta |
| 2 | **Confirmar que o Landing cria a oportunidade** no CRM, como diz o Contrato §12.7 (o `landing.yaml` cita só empresa e contato), com **`origem: landing` e `origemModuloId` igual ao `envioId`** | O Marketing liga o lead à oportunidade quando recebe `crm.oportunidade.criada`. O `origemModuloId` é o único campo desse evento que aponta para o envio | Alta |
| 3 | **IP e *User-Agent* do aceite** em `ConsentimentoRegistrado` (hoje só `texto` e `aceitoEm`) | A LGPD (8.1) e o nosso requisito RC03 pedem a evidência completa do consentimento. Vocês recebem os dois na rota pública de envio | Média |
| 4 | **Um evento para o preenchimento parcial** (ex.: `landing.sessao.atualizada`), quando a sessão passo a passo ou o chatbot passa de "grande parte" do formulário, com o critério documentado | É a etapa 2 do nosso funil (lead morno). Hoje ela não tem fonte e aparece zerada no painel | Média |
| 5 | **Listar os formulários do tenant com token de serviço** (só id, nome e status) | A nossa tela de regras só conhece os formulários que já trouxeram lead. Com a lista, a pessoa gestora configura a pontuação antes do primeiro envio | Baixa (seguimos sem) |
| 6 | **Carregar o script de rastreamento do Marketing nas landing pages** (`<script src="https://<plataforma>/public/marketing/rastreamento.js" async></script>`, uma linha fixa no HTML) e **manter a URL de entrada inteira, com a consulta, em `ToqueOrigem.paginaEntrada`** (revisão de 09/11) | É o ponto de IoT do Marketing, o "lead 0": o anúncio aponta para um link nosso, que segue para a landing page com o `mkt_visita` na URL. O script manda o que a pessoa faz na página (tempo, rolagem, cliques em botões e seções vistas) e só age com o `mkt_visita`; não lê campos do formulário nem usa cookie. No envio, achamos o `mkt_visita` na `paginaEntrada` e ligamos a visita ao lead. Nada muda no contrato de vocês | Média |
| 7 | **Página de entrada em `landing.chatbot.lead_capturado`** (o `paginaEntrada` da origem, como no envio) | Para ligar a visita do anúncio também à captura do chatbot. Hoje só o envio a liga | Baixa (seguimos sem) |

Os pedidos 1 e 2 não mudam nada para quem já consome os eventos de vocês: são campos a mais.

## 3. O que combinamos e não muda

- O tenant vem do envelope do evento (`tenantId`), e na API do token de serviço com `X-Tenant-Id`. O Marketing nunca lê o schema `landing`.
- O Marketing não chama nenhuma outra rota do Landing nem publica nada em `landing.eventos`.
- Formulário, campos, embed, chatbot, landing pages e métricas de visita ficam com vocês. Quem quer a conversão da página abre a tela Métricas do Landing; o painel do Marketing começa no lead.
- O Marketing não guarda a definição dos formulários. As regras de pontuação apontam para o `formularioId` e o `campoId` de vocês, só pelo UUID.

## 4. Para testar juntos

No ambiente local, o placeholder `landing-web/` do nosso repositório imita o contrato de vocês (mesmo serviço `landing`, porta `8088` e rotas). Quando o módulo real subir na plataforma, desligamos o placeholder e o Marketing não muda. Se um nome ou formato mudar no `landing.yaml` ou no `landing.asyncapi.yaml`, avisem no canal do projeto que ajustamos o consumidor.
