# Como rodar o módulo de Marketing (comece por aqui)

Guia para quem entrou no grupo agora: do clone até ver o lead andar pela tela. Conferido em 19/11/2026 numa máquina Windows 11 (PowerShell 5.1, Docker Desktop, Node 24 e JDK 24; o projeto compila para o Java 21), rodando a jornada inteira no Chrome, pela casca da plataforma.

Os READMEs de cada pasta ([marketing-api](../marketing-api/README.md), [marketing-web](../marketing-web/README.md), [landing-web](../landing-web/README.md), [crm-stub](../crm-stub/README.md), [infra](../infra/README.md)) explicam o código e as variáveis de cada app. Este arquivo é o caminho curto para colocar tudo de pé e conferir que funciona.

## 1. O que vai ficar rodando

O módulo não roda sozinho: ele vive dentro da plataforma do Grupo 2 (`infra-integrador-2026`). O script deste repositório sobe a parte da plataforma de que precisamos em containers e, no seu computador (fora do Docker), os quatro apps deste repositório:

| O quê | Onde | Porta |
|---|---|---|
| Postgres, RabbitMQ, Mailpit | container (compose do infra) | 5432, 5672 (painel em 15672), 1025 (e-mails em 8025) |
| identity (login e token) | container | 8081 |
| gateway e casca (o site da plataforma) | container, com `-ComCasca` | **8080** |
| `marketing-api` | seu computador | 8087 |
| `marketing-web` | seu computador | 3007 |
| `landing-web` (placeholder do Landing, Grupo 7) | seu computador | 3008 (página) e 8088 (servidor) |
| `crm-stub` (placeholder do CRM, Grupo 6) | seu computador | 8082 |

O identity **nunca** é simulado: as telas precisam de um token de um usuário real de teste. Por isso o caminho recomendado é o script `dev-plataforma.ps1`, que sobe o identity, faz o login e entrega tudo configurado.

## 2. O que você precisa ter instalado

| Item | Como conferir | Observação |
|---|---|---|
| Windows com PowerShell 5.1 | `$PSVersionTable.PSVersion` | o `dev-plataforma.ps1` é só PowerShell e só foi testado no Windows. Em Linux ou macOS, use o caminho manual de [demo-iot.md](demo-iot.md#sem-o-script) |
| Docker Desktop **ligado** | `docker info` | sem ele, a plataforma não sobe e os testes com container são **pulados em silêncio** |
| Node 24 e npm | `node -v` | o README da raiz pede o 24; os scripts `.mjs` rodam a partir do 18 |
| JDK 21 ou mais novo | `java -version` | o Maven vem pelo wrapper (`mvnw`), não instale |
| Git e Git Bash | `git --version` | o Git Bash só é usado se o `.env` do infra ainda não existir (o script o cria) |
| Portas livres | — | 5432, 5672, 15672, 1025, 8025, 8081, 8080, 8087, 3007, 3008, 8088 e 8082. Se você já roda outro Postgres ou RabbitMQ, pare-os antes |

O repositório fica num caminho com espaço e acento (`Repositórios pessoais`) na máquina de quem escreveu este guia. Os scripts já tratam isso; se aparecer erro de caminho, use aspas.

## 3. Primeira vez, passo a passo

**1. Clone os dois repositórios lado a lado** (o script procura o infra em `..\infra-integrador-2026`, ao lado deste repositório; se ele estiver em outro lugar, passe `-Infra`):

```powershell
git clone https://github.com/MateusBarnabe/Projeto-Integrador.git
git clone https://github.com/karolAlbuquerque/infra-integrador-2026.git
```

Se o infra já existe na sua máquina, atualize antes: `git -C infra-integrador-2026 pull`. Os contratos e os usuários de teste mudam nele.

**2. Suba tudo**, a partir da raiz deste repositório, no PowerShell:

```powershell
cd Projeto-Integrador
.\scripts\dev-plataforma.ps1 -Infra 'C:\caminho\para\infra-integrador-2026' -ComCasca -Ocultos
```

O que o script faz, em ordem: confere (ou cria) o `.env` do infra; para a infraestrutura deste repositório, se estiver de pé (as portas conflitam); sobe os containers; faz login como `marketing@empresa-a.dev`; instala as dependências dos fronts na primeira vez; abre os quatro apps; espera a API (as migrations criam as tabelas); e aplica `infra/demo/prazo-descarte.sql` (prazo de descarte de 2 minutos, para a demo).

Espere a linha verde **`Pronto.`**. Na primeira vez, baixar as imagens e compilar a API leva alguns minutos.

> **Linhas vermelhas no meio da saída são normais.** O PowerShell 5.1 mostra como erro o que o Docker escreve no *stderr* (`Container ... Running`, `Copying ...`, `NativeCommandError`). Se o script terminou com `Pronto.`, deu certo.

**3. Abra a casca**: http://localhost:8080/, entre com `marketing@empresa-a.dev` / `Plataforma2026` e clique em **Marketing** no menu lateral. O módulo abre num `<iframe>`, com Painel, Leads e Configurações no submenu.

**4. Confira a jornada** com o [roteiro ponta a ponta](roteiro-ponta-a-ponta.md): ou pelo script (`node scripts/ponta-a-ponta.mjs`), ou à mão (seção 5 abaixo).

### Variações do script

| Opção | Para quê |
|---|---|
| `-ComCasca` | sobe também a casca e aponta o gateway para o front e a API que rodam no seu computador. **É o modo que reproduz a plataforma**: sessão, tema, aviso à casca e abas por permissão |
| `-Ocultos` | não abre uma janela por app; os logs ficam em `%TEMP%\marketing-dev\` (`dev-api.log`, `dev-web.log`, `dev-landing.log`, `dev-crm.log`). Sem ele, cada app abre numa janela do PowerShell |
| `-SemInfra` | não mexe nos containers (já estão de pé). Não combina com `-ComCasca` |
| `-SoToken` | renova só o token do front no modo sozinho (dura 15 minutos) |
| `-Parar` | encerra os apps. Os containers da plataforma continuam de pé |

**Sem a casca** (sem `-ComCasca`), o módulo abre direto em http://localhost:3007/modulos/marketing/, com o token do `marketing@empresa-a.dev` gravado em `marketing-web\.env.local`. Esse token vale 15 minutos: depois dele, as chamadas à API passam a dar `401`; rode `.\scripts\dev-plataforma.ps1 -SoToken` e recarregue a página. Dentro da casca isso não acontece, porque ela renova o token sozinha.

## 4. Quem você pode ser (usuários de teste)

Senha de todos: `Plataforma2026`. A lista completa está em `docs/usuarios-de-teste.md` do infra.

| Usuário | O que enxerga no Marketing |
|---|---|
| `marketing@empresa-a.dev` | tudo (ver e editar). É o do dia a dia |
| `administrador@empresa-a.dev` | tudo |
| `gestor@empresa-a.dev` | **só leitura**: as telas abrem, mas nenhuma aba oferece ação de edição. Serve para testar as permissões |
| `vendedor@empresa-a.dev` e os demais | nenhuma permissão do Marketing: a casca esconde o módulo |
| `*@empresa-b.dev` | outro tenant (Empresa B), para provar que um tenant não enxerga o outro |

Para ver um segundo usuário sem sair do primeiro, abra uma janela anônima do navegador.

## 5. Ver a jornada à mão (uns 5 minutos)

A mesma jornada que o script confere, vista pela tela. Foi o caminho percorrido em 19/11:

1. **Configurações › Rastreamento › Novo link.** Nome livre, URL da landing `http://localhost:3008/`, `utm_source=google`, `utm_medium=cpc` e uma `utm_campaign` nova. Salve: a lista mostra a URL pública do link (`http://localhost:8087/public/marketing/c/<código>`).
2. **Clique no anúncio.** Abra essa URL numa aba nova (ou use "Simular clique"). Ela responde `302` para `http://localhost:3008/?utm_...&mkt_visita=<id>`. O rodapé da landing page mostra o `mkt_visita`.
3. **Navegue na landing page**: role até o fim e clique em "Fale conosco" e nos botões de preço. O script de rastreamento manda tempo, rolagem e cliques **a cada 15 segundos**: espere uns 15 segundos depois do último clique antes de enviar o formulário. O que acontecer nesse intervalo se perde, porque a visita vira "convertida" no envio (ver o achado A1 em [pendências](pendencias-e-dependencias.md)).
4. **Preencha e envie o formulário** (com o consentimento marcado). A página responde "Recebemos seus dados".
5. **Leads.** Em poucos segundos o lead aparece na etapa 3, com a pontuação, a campanha, o aparelho e a marca "no CRM". Abra o detalhe: **Dados** (contato, funil, consentimento com o texto do termo, comunicações, CRM), **Origem**, **Interação** (aparelho, sistema, tempo, rolagem, páginas), **Respostas**, **Pontuação** (as regras que somaram) e **Timeline**.
6. **Painel.** Mostra o lead novo, o funil, a campanha e o cartão do lead 0 (cliques, convertidas, descartadas). O selo "Ao vivo" indica que o fluxo em tempo real está conectado.
7. **Vendedor.** Só quem vira MQL (quente ou morno) recebe vendedor do rodízio. Para qualquer lead, o botão "Atribuir" ou "Reatribuir" do detalhe escolhe um vendedor da escala.
8. **E-mail.** http://localhost:8025 (Mailpit) recebe a confirmação de boas-vindas em até 1 a 2 minutos (o job 4.5 roda uma vez por minuto).

Sem passar pelo link de campanha, o lead também chega: abra http://localhost:3008/, envie o formulário e ele aparece na lista, só sem o contexto do anúncio.

## 6. Testes automatizados

```powershell
cd marketing-api
.\mvnw.cmd verify         # ou, na raiz: npm run test:api
```

Em 19/11/2026: **305 testes, 0 falhas, 0 pulados**. Os testes sobem um PostgreSQL e um RabbitMQ de verdade com o Testcontainers e simulam Landing, CRM, identity e a Graph API do Meta com WireMock; por isso o Docker precisa estar ligado. **Se o resultado vier com dezenas de testes pulados, o Docker estava desligado: ligue e rode de novo.** O front confere os tipos com `npm run build` em `marketing-web`.

## 7. Testar o webhook do Meta sem o Meta

O Meta chama o módulo em `/public/marketing/{subdominio}/meta/leads`. O subdomínio do tenant de teste é `empresa-a` (tabela `identity.tenants`). Dá para exercitar o recebimento inteiro, sem conta no Meta:

1. Em **Configurações › Canais de anúncio › Meta Ads**, preencha o *Verify Token* e o *App Secret* (valores inventados) e salve. **Não** preencha o token da página: sem ele, o job 4.1 espera e não chama a Graph API de verdade.
2. Rode este script (Node 18 ou mais), trocando os dois valores pelos que você salvou:

```js
import { createHmac } from 'node:crypto';
const url = 'http://localhost:8087/public/marketing/empresa-a/meta/leads';
const VERIFY = 'seu-verify-token', SEGREDO = 'seu-app-secret';

// 1) handshake: deve responder 200 com "abc123" em texto puro
let r = await fetch(`${url}?hub.mode=subscribe&hub.verify_token=${VERIFY}&hub.challenge=abc123`);
console.log('handshake', r.status, await r.text());

// 2) lead assinado: 200; repetir não duplica
const corpo = JSON.stringify({ object: 'page', entry: [{ id: '1029384756', time: 1, changes: [
  { field: 'leadgen', value: { leadgen_id: String(Date.now()), page_id: '1029384756', form_id: '555', ad_id: '777' } }] }] });
const assinatura = 'sha256=' + createHmac('sha256', SEGREDO).update(corpo).digest('hex');
r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Hub-Signature-256': assinatura }, body: corpo });
console.log('webhook', r.status);
```

Resultados esperados (conferidos em 19/11): handshake certo `200` em `text/plain`; token errado `403`; assinatura errada `401`; sem segredo configurado, ou subdomínio desconhecido, `404`; a mesma `leadgen_id` enviada de novo continua `200`, sem linha nova. Para ver o buffer: `docker exec plataforma-postgres-1 psql -U postgres -d plataforma -c "select meta_lead_id, processado, tentativas from marketing.leads_entrada_buffer"`.

**O que esse teste deixa no seu banco local:** o *Verify Token* e o *App Secret* inventados ficam gravados (cifrados) no canal `meta_ads` do tenant, e cada execução do script acrescenta uma linha pendente no buffer (`processado = false`). Sem o token da página, o job 4.1 a deixa esperando, sem gastar tentativas, e ela fica ali. É inofensivo, mas aparece em consultas ao buffer. Para tirar as linhas de teste: `docker exec plataforma-postgres-1 psql -U postgres -d plataforma -c "delete from marketing.leads_entrada_buffer where not processado"`. Os segredos só saem ao serem substituídos por outro valor na tela (campo vazio mantém o que já está gravado) ou ao zerar o banco (seção 8). Não faça isso num banco que tenha dados que você queira guardar.

O **job 4.1** (a cada 5 minutos) é quem busca o lead na Graph API e o grava na etapa 0. Ele só foi exercitado nos testes automatizados, contra WireMock. Para testá-lo à mão, aponte `META_GRAPH_BASE_URL` para um servidor seu que imite `GET /v23.0/{leadgen_id}` e salve também o token da página. Contra a Graph API real, depende do token do Meta e da saída do container para a internet (ver [pendências](pendencias-e-dependencias.md)).

## 8. Parar e recomeçar

| Quero | Faça |
|---|---|
| Parar os apps | `.\scripts\dev-plataforma.ps1 -Parar` |
| Parar também os containers | no infra: `docker compose --profile plataforma stop` |
| Subir de novo | o mesmo comando da seção 3 |
| Zerar o banco local | no infra: `docker compose down -v`. **Apaga todos os dados** (leads, links, configurações); as migrations rodam de novo na próxima subida |

O placeholder do Landing e o stub do CRM guardam tudo **em memória**: reiniciar qualquer um apaga os envios, e os leads antigos passam a mostrar "indisponível no CRM". O banco do Marketing não perde nada.

## 9. Quando algo não funciona

| Sintoma | Causa provável e o que fazer |
|---|---|
| `Infra nao encontrado em ...` | o infra não está em `..\infra-integrador-2026`. Passe `-Infra <pasta>` ou defina a variável `INFRA_INTEGRADOR` |
| `Sem o .env do infra e sem o Git Bash` | instale o Git for Windows, ou rode `scripts/gerar-env.sh` dentro do infra pelo Git Bash |
| Demora na primeira subida | normal: imagens e compilação. O script espera até 5 minutos pela API; passou disso, veja `dev-api.log` |
| Porta ocupada | outro Postgres, RabbitMQ ou app usa a porta. Descubra com `netstat -ano \| findstr :5432` e pare o processo |
| Erro `401` nas chamadas no modo sozinho | o token de 15 minutos venceu: `.\scripts\dev-plataforma.ps1 -SoToken` e recarregue a página |
| A casca não mostra o Marketing | o usuário não tem permissão do módulo. Entre com `marketing@empresa-a.dev` |
| Painel preso em "Conectando…" | o gateway devolve `504` se o primeiro byte demorar mais de uns 3 s (o back já manda `: conectado` ao abrir). Se persistir, a API caiu: veja `dev-api.log` |
| Lead novo não aparece | o evento do Landing não chegou ou o Marketing não leu o envio. Veja `dev-landing.log` e `dev-api.log` e o RabbitMQ em http://localhost:15672 |
| Lead sem vendedor | só MQL entra no rodízio; fora do horário de atendimento (padrão: segunda a sexta, 8h às 18h) ele espera o job 4.4, a cada minuto |
| Sem e-mail de boas-vindas | espere até 2 minutos (job 4.5). Confira o Mailpit (8025) e se o SMTP é `localhost:1025` |
| A API não sobe e o Flyway reclama de *checksum* da `V3` | o banco local foi criado por uma branch antiga (`feat/iot-ingestao-leads`) que usava a `V3` para outra coisa. Zere o banco (seção 8). Num banco que nunca teve essa branch, a `V3` entra sozinha (o perfil `dev` aceita migration fora de ordem) |
| Testes passam rápido, com muitos pulados | Docker desligado (`docker info`) |

## 10. Onde ler mais

| Para | Leia |
|---|---|
| Entender o produto, as tabelas, as rotas e as regras | [documento-norteador.md](documento-norteador.md) (fonte da verdade) |
| O lead 0 e a demonstração | [demo-iot.md](demo-iot.md) |
| Conferir a jornada por script | [roteiro-ponta-a-ponta.md](roteiro-ponta-a-ponta.md) |
| O que falta e o que depende de outros grupos | [pendencias-e-dependencias.md](pendencias-e-dependencias.md) |
| Tarefas e ordem de trabalho | [../PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md) |
| Variáveis de ambiente da API | [../marketing-api/README.md](../marketing-api/README.md#variáveis-de-ambiente) |
| Regras do front e do Design System | [../marketing-web/README.md](../marketing-web/README.md) e [DesignSystem/](DesignSystem/) |
