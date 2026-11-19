# O que falta e o que depende de outros grupos

Estado em **19/11/2026**, depois do merge das rodadas 1 e 2 da fase 3 (PRs #24, #25 e #26). Serve para o grupo saber o que ainda é dele e o que está parado esperando alguém, e para cobrar os outros grupos com o texto certo na mão. A lista de tarefas e a ordem de trabalho estão em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md); o desenho está no [documento norteador](documento-norteador.md), seção 11.

## 1. Onde estamos

Pronto e conferido:

- **Back:** a jornada do lead inteira (chatbot, envio do formulário, oportunidade do CRM, pontuação, rodízio, notificações), a API de leads, o painel com tempo real, as configurações, a comunicação (boas-vindas, aquecimento, descadastro, anonimização), o rastreamento do anúncio (lead 0), os leads do Meta (webhook e job 4.1) e a localização por IP real.
- **Front:** Painel, Leads e Configurações, dentro da casca real, com sessão, tema, abas por permissão e controles de edição escondidos para quem só pode ver.
- **Testes:** `./mvnw verify` com **305 testes, 0 falhas, 0 pulados**; o checklist da plataforma (19 itens) tem uma classe de teste própria; e a jornada ponta a ponta tem script (64 conferências, 0 falhas) e foi refeita à mão no Chrome em 19/11 (ver a seção 5).

O que sobra é de três tipos: o que só o nosso grupo resolve (seção 2), o que depende de outro grupo (seção 3) e os achados da validação ao vivo (seção 4).

## 2. O que depende só de nós

| # | O quê | Detalhe | Esforço |
|---|---|---|---|
| N1 | **Ler `SMTP_HOST` e `SMTP_PORT`** | o `application.yml` lê só `MAIL_HOST` e `MAIL_PORT`, mas a plataforma entrega `SMTP_*` (é o que o identity recebe no compose). Passar a ler `${SMTP_HOST:${MAIL_HOST:localhost}}` e `${SMTP_PORT:${MAIL_PORT:1025}}`, e atualizar o norteador (seções 1 e 11), o README da API e a X3. É pré-requisito da I3 | pequeno |
| N2 | **Conferir a Graph API de verdade** | o job 4.1 só foi exercitado contra WireMock. Falta uma rodada com credenciais de teste do Meta: o token da página no cabeçalho `Authorization: Bearer`, a versão `v23.0` e o formato de `field_data`. Sem isso, a E4 não está validada | médio (depende de uma conta do Meta) |
| N3 | **Validações manuais da casca (V2 a V5)** | reconexão do SSE derrubando a API, painel ao vivo com filtro de campanha, modal em janela baixa (1024×600) e abas com um perfil parcial de verdade. Detalhes na seção 6 | pequeno |
| N4 | **Decidir o consentimento dos leads do Meta (X11)** | hoje o lead do Meta fica com `consentimento_marketing = false` e **não entra no aquecimento**. Falta decidir com os professores se o formulário do Meta vale como aceite. Junto, decidir quem recebe a notificação `NOVO_LEAD` quando não há vendedor, e se o vendedor acessa o lead notificado (hoje o perfil VENDEDOR não tem `marketing.lead.ver`) | decisão |
| N5 | **Declarar `servicos` em `marketing.lead.ver_resumo` (X16)** | o código já aceita token de serviço; falta declarar nas quatro cópias da permissão (norteador, `Docs/permissoes`, `Docs/modulos` e infra) e saber quem consome o resumo do lead | pequeno, mas precisa de um PR no infra |
| N6 | **Base legal do rastreamento do lead 0 (X18)** | proposta: legítimo interesse, sem cookie, dados apagados no prazo, com aviso na landing page. Precisa do aceite do Grupo 7 e dos professores | decisão |
| N7 | **Corrigir os achados da validação ao vivo** | A1 e A2 da seção 4: cliques perdidos antes do envio e o texto "aguardando vendedor" em lead que nunca terá vendedor | pequeno |
| N8 | **Nome do vendedor** | a tela mostra só o início do id. Quando o Grupo 2 liberar a listagem (X7), trocar pelo nome | pequeno, depois da X7 |
| N9 | **Base GeoLite2 no ambiente** | o código da localização real existe, mas cada ambiente precisa baixar a base (conta gratuita na MaxMind), montá-la no container e definir `RASTREAMENTO_GEOIP_ARQUIVO`. A base não vai para o git (licença) | operação |
| N10 | **Escopo do Mapa de funcionalidades** | itens atribuídos ao Grupo 4 e ainda não cobertos: construtor visual de automação (60), gatilhos e ações (61 e 62), campanhas de e-mail (63) e rastreio de e-mail aberto e clicado. Decidir com os professores se entram | decisão |

## 3. O que depende de outros grupos

Quem destrava, o que pedimos, o que funciona enquanto isso. Os códigos X vêm do [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

### Grupo 2 (plataforma e infra)

| X | O que pedimos | Por quê | O que funciona sem isso |
|---|---|---|---|
| I3 | **Entregar o módulo no `infra-integrador-2026`**: trocar `conta-do-grupo` por `mateusbarnabe` em `IMAGEM_MARKETING` e `IMAGEM_MARKETING_FRONT` no `.env.example` e sessão do checklist (19 itens) | hoje o `.env.example` do infra aponta para `ghcr.io/conta-do-grupo/marketing`: quem subir o compose da plataforma sem o nosso script não acha a imagem | tudo roda localmente com o `dev-plataforma.ps1`; as imagens já são publicadas pelo CI da `main` |
| X3 | **Variáveis de SMTP no serviço `marketing`** do compose: `SMTP_HOST: mailpit` e `SMTP_PORT: "1025"`, como o identity. Provedor de e-mail fora do desenvolvimento | sem elas, o job de boas-vindas tenta `localhost:1025` dentro do container e falha | localmente o Mailpit recebe os e-mails. Depende do N1 |
| X6 | **`MARKETING_CHAVE_CIFRA`** no serviço `marketing`, com um valor real só no `.env` (linha nova `MARKETING_CHAVE_CIFRA=troque` no `.env.example`) | a chave cifra os segredos do Meta. Sem ela o módulo sobe, mas gravar um segredo responde `503 CHAVE_CIFRA_AUSENTE` | o perfil `dev` usa uma chave fixa |
| X19 | **`MARKETING_URL_PUBLICA`** (o endereço que o navegador do visitante alcança; sugestão: `https://centinela-pi.duckdns.org`) e a **exceção ao envelope** para três respostas públicas nossas: o `302` do link de campanha, o `rastreamento.js` e a página HTML do descadastro | a URL monta o link de campanha e a tag do script. O §8.2 do contrato só prevê exceção para download de arquivo | localmente vale `http://localhost:8087` |
| X4 | **Exceção ao envelope para o `hub.challenge` do Meta** (texto puro) e o **rate limit de 120/min em `/public/**`** | o Meta exige o texto puro no handshake e pode mandar rajadas de leads | o webhook responde certo; só o contrato não registra a exceção |
| X5 | **Saída do container para a internet** (`graph.facebook.com`) | o job 4.1 chama a Graph API | sem a saída, o job soma tentativas e a linha vira `falha_permanente` na quinta |
| X8 | **SSE pelo gateway**: confirmar que a regra de `504` em 3 s vale só para o primeiro byte e se há teto de duração da conexão | o painel em tempo real usa uma conexão longa. Testado em 19/11 com o gateway local: passa de 40 s e conecta em ~6 s | se cortar, a tela relê o resumo a cada 30 s e avisa "atualização em tempo real indisponível" |
| X7 | **Listar usuários com perfil VENDEDOR** (com nome) no identity | a escala do rodízio hoje pede o UUID à mão, e a tela mostra só o início do id | o rodízio funciona com os ids cadastrados à mão |
| X14 | `LANDING_BASE_URL` e `CRM_BASE_URL` no serviço `marketing` | hoje valem os padrões `http://landing:8088` e `http://crm:8082`, que já batem com os nomes dos containers | funciona pelos padrões |
| X9 | **[resolvida do nosso lado]** a casca recarrega o `<iframe>` a cada item do submenu; as abas internas ficam escondidas no modo embutido | — | — |

**Já confirmado em 19/11 (não precisa pedir):** o gateway local já encaminha `/public/marketing/**` (o `302` do link, o `rastreamento.js` e o handshake do Meta responderam pela porta 8080). Falta só confirmar o mesmo no gateway do staging (`docker-compose.staging.yml` e Caddy).

### Grupo 6 (CRM)

| X | O que pedimos | Por quê | O que funciona sem isso |
|---|---|---|---|
| X2 / C1 | **Ação no CRM para gravar o responsável de uma oportunidade**, com `servicos: [marketing]` na permissão | o vendedor escolhido pelo rodízio precisa chegar à oportunidade no CRM (norteador 9.6) | o vendedor fica só no Marketing; a oportunidade no CRM segue sem responsável |

A C1 (levar o vendedor ao CRM) só começa quando o Grupo 6 publicar essa ação.

### Grupo 7 (Landing)

| X | O que pedimos | Por quê | O que funciona sem isso |
|---|---|---|---|
| X17 | **A landing page real carregar a tag do `rastreamento.js`** e manter a URL de entrada inteira (com `mkt_visita`) em `paginaEntrada` | é o que liga o clique do anúncio ao lead e traz o comportamento na página | sem a tag, o clique ainda dá o contexto técnico (aparelho, sistema), mas não o comportamento |
| X1 | **`sessaoId` em `landing.formulario.recebido`** e confirmação de que o Landing cria a oportunidade com `origemModuloId = envioId` | ligar a captura do chatbot ao envio | o Marketing liga por `envio_id` e `contatoId`, e aceita `sessaoId` se vier |
| X12 | **Uma fonte para a etapa 2 do funil** (preenchimento parcial do formulário) | hoje não há evento para isso | a etapa 2 fica zerada no painel |
| X13 | **IP e *User-Agent* do aceite** no consentimento do envio | a evidência de consentimento (LGPD) fica incompleta | grava o texto do termo e a data; IP e *User-Agent* ficam vazios |
| X15 | **Campos `fbclid` e `gclid`** no `ToqueOrigem` do envio | hoje só chegam dentro de `paginaEntrada` | o Marketing extrai da URL |
| — | **Chatbot (`landing.chatbot.lead_capturado`) com página de entrada** | sem ela, a captura do chatbot não liga a visita do anúncio; só o envio liga | segue sem a ligação no chatbot |

### Professores

| O quê | Por quê |
|---|---|
| Provedor de e-mail fora do desenvolvimento (P6) | o Mailpit só existe em desenvolvimento |
| Consentimento do lead do Meta (X11) e base legal do rastreamento (X18) | decisões de LGPD |
| Escopo: construtor de automação, campanhas de e-mail, WhatsApp e telefonia (65 a 67, sem dono no Mapa) | itens fora do que foi construído |

## 4. Achados da validação ao vivo de 19/11 (rodada 2)

Jornada refeita à mão no Chrome, pela casca (login, link de campanha, clique, landing page, formulário, lead, detalhe, painel, reatribuição, e-mail e permissões do `gestor@`). Tudo passou; estes são os pontos que merecem correção ou decisão:

| # | Achado | Evidência | Sugestão |
|---|---|---|---|
| **A1** | **Cliques da landing page se perdem se o formulário for enviado antes do próximo envio de sinais.** O script manda os sinais a cada 15 s; no envio do formulário a visita vira "convertida" e os sinais seguintes são ignorados. Quem clica em "Fale conosco" e preenche o formulário em menos de 15 s fica com "Nenhum clique registrado" e perde as regras de pontuação por clique | no teste, os cliques em "Quero este" e "Simular o investimento" vieram depois do 2º envio e antes do formulário; o detalhe do lead mostrou "Nenhum clique registrado" e 15 s na página, e a rede mostrou só 2 envios de sinais antes do `POST .../envios` | em `rastreamento.js`, enviar os sinais também a cada clique (com um pequeno atraso para juntar cliques seguidos) e no evento `submit` |
| **A2** | **"aguardando vendedor" aparece no detalhe de lead que nunca terá vendedor.** Só MQL (quente e morno) entra no rodízio; um lead de Nutrição mostra o rótulo como se estivesse na fila, enquanto o painel (corretamente) conta 0 em "Aguardando vendedor" | lead de 35 pontos (Nutrição) no detalhe: "Vendedor: aguardando vendedor" | mostrar "sem vendedor" (ou "não se aplica") quando a faixa for Nutrição, e "aguardando vendedor" só para MQL |
| **A3** | **A E4 não foi exercitada contra a Graph API real**, só contra WireMock | seção 2, N2 | rodada com credenciais de teste do Meta |
| **A4** | **O banco local criado por branch antiga recusa a `V3` nova** (a branch `feat/iot-ingestao-leads` usava a `V3` para outra tabela) | relato do agente da rodada 2; num banco limpo a `V3` entrou sozinha (fora de ordem, pelo perfil `dev`) | documentado em [como-rodar.md](como-rodar.md), seção 9 |
| **A5** | **O `favicon.ico` do placeholder do Landing dá 404** | único erro de console em toda a jornada | irrelevante (o placeholder não é nosso), mas some se adicionarem o arquivo |
| **A6** | **Ruído vermelho no `dev-plataforma.ps1`**: o PowerShell 5.1 mostra o *stderr* do Docker como erro | a saída termina com `Pronto.`; as linhas vermelhas são só progresso do Docker | documentado em [como-rodar.md](como-rodar.md); dá para silenciar redirecionando o *stderr* no script |

## 5. O que foi conferido ao vivo em 19/11

| Fluxo | Resultado |
|---|---|
| Login na casca como `marketing@` e abertura do Painel, Leads e Configurações dentro do `<iframe>` | ok; painel "Ao vivo" |
| Criar link de campanha pela tela | ok; URL pública gerada |
| Clique no anúncio (`302`), script de rastreamento e formulário no placeholder | ok; `mkt_visita` no rodapé, sinais com `200` |
| Lead na lista (etapa 3, pontuação, campanha, aparelho, "no CRM") | ok, em poucos segundos |
| Detalhe do lead, aba por aba (Dados, Origem, Interação, Respostas, Pontuação, Timeline com 6 eventos) | ok, com A1 e A2 |
| Painel: lead novo, lead recente, lead 0 (cliques 7 → 8, convertidas 3 → 4) | ok |
| Reatribuir vendedor pela tela | ok; evento na timeline e aviso "Vendedor reatribuído." à casca |
| E-mail de boas-vindas no Mailpit | ok, em cerca de 1 minuto |
| `gestor@` (só leitura) em todas as abas de Configurações | ok: nenhuma ação de edição; só os botões de copiar |
| Webhook do Meta: handshake `200` em texto puro, token errado `403`, assinatura errada `401`, reentrega sem duplicar, sem segredo `404` | ok; uma única linha no buffer, só com ids |
| Gateway local encaminha `/public/marketing/**` | ok |
| Console e rede | sem erros; só o `favicon.ico` do placeholder (A5) |

## 6. Validações manuais que ainda faltam (V2 a V5)

Sem código novo, a menos que apareça defeito. A V1 (configurações sem permissão de edição) foi corrigida, mesclada e conferida em 19/11 (seção 5).

| # | O que conferir | Como |
|---|---|---|
| V2 | **Reconexão do SSE.** Derrubar a API com o painel aberto e ver o selo voltar a "Ao vivo" com `Last-Event-ID`, sem repetir lead; e o fim da conexão no `exp` do token (15 min), com a casca entregando o token novo e o fluxo reabrindo | painel aberto na casca por mais de 15 min; `-Parar` e subir a API de novo |
| V3 | **Painel ao vivo com filtro.** Os contadores só mudam na hora quando o evento cabe no filtro; senão a tela relê o resumo | filtrar por uma campanha, enviar um lead com outra `utm_campaign` e outro com essa |
| V4 | **Modal maior que a janela.** Só foi medido com 1280×720 | casca com a janela em 1024×600 e o detalhe de um lead aberto |
| V5 | **Abas por permissão com um perfil parcial de verdade.** Foi testado com token sem assinatura | criar em Administração › Perfis de acesso um perfil só com `marketing.lead.ver`, um usuário com ele, e entrar na casca |

## 7. Mensagens prontas

### Para o Grupo 2

> **Grupo 4 (Marketing) → Grupo 2**
>
> Oi, pessoal! O módulo do Marketing está com a jornada completa rodando na plataforma local (login, casca, painel, leads e os dois placeholders), e queremos alinhar a entrega antes da sessão do checklist.
>
> **Vamos abrir um PR no `infra-integrador-2026`** (como o Landing fez no #37) com:
> - `.env.example`: `IMAGEM_MARKETING=ghcr.io/mateusbarnabe/marketing:latest` e `IMAGEM_MARKETING_FRONT=ghcr.io/mateusbarnabe/marketing-front:latest` (hoje estão com `conta-do-grupo`), e uma linha nova `MARKETING_CHAVE_CIFRA=troque`.
> - No serviço `marketing` do `docker-compose.yml`:
>   - `SMTP_HOST: mailpit` e `SMTP_PORT: "1025"`, iguais ao identity, para os e-mails de boas-vindas e aquecimento;
>   - `MARKETING_CHAVE_CIFRA: ${MARKETING_CHAVE_CIFRA}`, a chave que cifra os segredos do Meta Ads (no staging, com valor real no `.env`);
>   - `MARKETING_URL_PUBLICA`, o endereço que o navegador alcança, para os links de campanha e o script de rastreamento.
>
> **Perguntas que só vocês conseguem responder:**
> 1. **Gateway do staging:** ele encaminha `/public/marketing/**` para `marketing:8087`, como o local já faz? O rate limit de 120/min por IP vale lá também? (O Meta pode mandar rajadas no webhook.)
> 2. **Exceções ao envelope:** o §8.2 só prevê download de arquivo, e temos quatro respostas públicas fora do envelope: o `302` do link de campanha (`/public/marketing/c/{codigo}`), o `GET /public/marketing/rastreamento.js`, a página HTML do descadastro e o `hub.challenge` do Meta (texto puro). Vocês registram no contrato, ou preferem que a gente abra um PR com a proposta?
> 3. **Domínio público:** podemos usar `https://centinela-pi.duckdns.org` em `MARKETING_URL_PUBLICA`?
> 4. **Internet:** o container do `marketing` vai poder sair para `graph.facebook.com`?
> 5. **SSE:** uma conexão longa passa pelo gateway? Com o gateway local, ela passou de 40 s, e o `504` só aparece se o primeiro byte demorar mais de uns 3 s. Existe um teto de duração?
> 6. **Vendedores:** existe um jeito de listar os usuários com perfil VENDEDOR e os nomes deles no identity? É para a escala do rodízio.
>
> Obrigado!

### Para o Grupo 6

> **Grupo 4 → Grupo 6 (CRM).** Para levar o vendedor escolhido pelo rodízio de leads até a oportunidade, precisamos de uma ação no CRM que grave o responsável de uma oportunidade, aceitando token de serviço (`servicos: [marketing]` na permissão). Vocês já têm isso previsto? Enquanto não existir, o vendedor fica só no Marketing. Também confirmem que continuam publicando `crm.oportunidade.criada` com `origemModuloId` igual ao `envioId` do Landing: é por ele que ligamos o lead à oportunidade.

### Para o Grupo 7

> **Grupo 4 → Grupo 7 (Landing).** Quatro pedidos para fechar o lead 0: (1) a landing page carregar `<script src="<MARKETING_URL_PUBLICA>/public/marketing/rastreamento.js" async></script>` e manter a URL de entrada inteira, com o `mkt_visita`, em `paginaEntrada`; (2) `sessaoId` em `landing.formulario.recebido` e `landing.chatbot.lead_capturado` com página de entrada; (3) o IP e o *User-Agent* do aceite no consentimento do envio, para a evidência de LGPD; (4) `fbclid` e `gclid` no `ToqueOrigem`. Os detalhes estão em [landing-requisitos.md](contratos/landing-requisitos.md). Sem eles nada quebra: perdemos só o comportamento da visita, o vínculo do chatbot, a evidência completa do aceite e a extração direta dos identificadores.
