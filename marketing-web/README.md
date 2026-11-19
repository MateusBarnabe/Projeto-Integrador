# marketing-web

Front do módulo de Marketing (Etapa 2 do projeto). É a área autenticada que a pessoa gestora de marketing usa dentro da plataforma: painel, leads e configurações.

> **Estado atual (18/11/2026): as três áreas prontas.** O layout, a camada de integração com a casca real (W2), o cliente HTTP, os componentes do Design System (W1, com tema escuro) e as três telas estão prontos: **Painel** (W6 e R6, com atualização em tempo real por SSE), **Leads** (W5 completa) e **Configurações** (W4 e R2). O que falta está em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

| | |
|---|---|
| Stack | React 19, TypeScript 5.9, Vite 8, Tailwind CSS v4, react-router 8, ícones `lucide-react` |
| Porta | `3007` |
| Caminho base | `/modulos/marketing/` (configurável) |
| Nome na plataforma | serviço e imagem `marketing-front` |
| Design | [Design System Centinela](../Docs/DesignSystem/) (tokens `brand-*`, fonte Inter) |

**O que não fica aqui:** a landing page, o formulário que o visitante preenche e o editor de formulários e campos. Tudo isso é do módulo Landing (Grupo 7): a página pública e o editor, no back-office do Landing. Localmente, quem simula o Landing é o [landing-web](../landing-web/). Aqui as respostas do formulário aparecem só no detalhe do lead, lidas da API do Landing.

## Como rodar

```bash
npm install
npm run dev          # http://localhost:3007/modulos/marketing/
```

Da raiz do repositório também funciona: `npm run dev:web`, ou `npm run dev` para subir a API, este front, o placeholder do Landing e o stub do CRM juntos (sem o identity: para ter login, use o `.\scripts\dev-plataforma.ps1`, ver [Docs/como-rodar.md](../Docs/como-rodar.md)).

Em desenvolvimento, o Vite repassa `/api/marketing` para a API em `http://localhost:8087` e `/api/crm` para o stub do CRM em `http://localhost:8082` (os resumos de empresa e contato do detalhe do lead). Então a API precisa estar rodando para o selo "API do módulo" ficar verde.

Para ver o módulo dentro da casca real (iframe, menu lateral, tema), use `.\scripts\dev-plataforma.ps1 -ComCasca` na raiz e abra http://localhost:8080/. Sozinho, `?tema=escuro` liga o tema escuro.

| Script | O que faz |
|---|---|
| `npm run dev` | servidor de desenvolvimento com recarga automática |
| `npm run build` | confere os tipos (`tsc`) e gera o build em `dist/` |
| `npm run typecheck` | só a conferência de tipos |
| `npm run preview` | serve o build de `dist/` localmente |

### Pelo Docker

```bash
docker compose up -d --build marketing-front    # na raiz
```

O [Dockerfile](Dockerfile) gera o build com Node 24 e serve o resultado com nginx. O nginx ([nginx/default.conf.template](nginx/default.conf.template)):

- entrega o build em `/modulos/marketing/` e manda as rotas internas (ex.: `/modulos/marketing/leads`) para o `index.html`, porque quem resolve a rota é o react-router;
- redireciona `/` para `/modulos/marketing/`;
- repassa `/api/marketing/` para a API (`API_UPSTREAM`, padrão `http://marketing:8087`), sem buffer e com conexão longa, já pensando no SSE do painel;
- manda `Content-Security-Policy: frame-ancestors 'self'`, para a casca poder abrir o módulo num `<iframe>` (Contrato §12.8).

Depois de mudar o código, é preciso reconstruir a imagem. Se o navegador continuar mostrando a versão antiga, recarregue com **Ctrl+Shift+R**.

## Variáveis de ambiente

Copie o [.env.example](.env.example) para `.env.local` se quiser mudar algum padrão. Nada é obrigatório para rodar local.

| Variável | Padrão | Para quê |
|---|---|---|
| `VITE_BASE_PATH` | `/modulos/marketing/` | caminho onde o módulo é servido; vale para o Vite, o roteador e o nginx |
| `VITE_API_URL` | `/api/marketing` | endereço da API; relativo, passando pelo proxy do Vite ou pelo gateway |
| `API_PROXY_ALVO` | `http://localhost:8087` | para onde o Vite repassa `/api/marketing` em desenvolvimento |
| `VITE_CRM_URL` | `/api/crm` | endereço da API do CRM, para os resumos de empresa e contato do lead; relativo, passando pelo gateway da plataforma |
| `CRM_PROXY_ALVO` | `http://localhost:8082` | para onde o Vite repassa `/api/crm` em desenvolvimento (o `crm-stub`) |
| `VITE_TOKEN_DEV` | vazio | token real de um usuário de teste do identity, usado no modo sozinho |

As variáveis `VITE_*` entram no build. Para mudá-las numa imagem Docker, é preciso gerar a imagem de novo.

## Estrutura de pastas

```
marketing-web/
├── Dockerfile, nginx/            imagem de produção (build + nginx)
├── index.html                    página base (div #raiz, fonte Inter)
├── vite.config.ts                caminho base, alias @/, proxy da API, porta 3007
├── tsconfig.json                 TypeScript estrito; @/ aponta para src/
└── src/
    ├── main.tsx                  inicia a camada plataforma e renderiza o roteador
    ├── rotas.tsx                 roteador: layout + rotas de cada área
    ├── index.css                 Tailwind e tokens do Design System (@theme)
    ├── vite-env.d.ts             tipos das variáveis VITE_*
    ├── plataforma/               ÚNICA fronteira com a casca da plataforma
    │   ├── tipos.ts              interface Plataforma
    │   ├── embutida.ts           implementação para o iframe (postMessage)
    │   ├── altura.ts             altura do iframe e área visível (modal centralizado)
    │   ├── permissoes.ts         lê o claim perms do token (só para esconder o que não pode usar)
    │   ├── usarPermissao.ts      hooks usarPermissao e usarTemSessao
    │   ├── sozinha.ts            implementação para abrir direto no navegador
    │   └── index.ts              escolhe a implementação e exporta `plataforma`
    ├── api/cliente.ts            ÚNICO ponto de chamada HTTP (a API do módulo e, para os resumos, a do CRM)
    ├── api/eventos.ts            fluxo SSE com fetch + ReadableStream (painel em tempo real)
    ├── layout/LayoutModulo.tsx   faixa "Marketing", abas das áreas e sidebar do modo sozinho
    ├── components/
    │   ├── ui/index.tsx          componentes base com os nomes do Design System
    │   ├── InteracaoVisitante.tsx contexto técnico e comportamento da visita do anúncio (R1)
    │   ├── StatusApi.tsx         selo "API do módulo: no ar / fora do ar"
    │   └── EmConstrucao.tsx      estado vazio das áreas sem funcionalidade
    └── features/                 uma pasta por área do módulo
        ├── painel/               PainelPage, LeadZeroBloco, usePainel, tempoReal, api.ts, componentes/ (gráficos em SVG e CSS), rotas.tsx (W6, R6)
        ├── leads/                LeadsPage (lista), DetalheLeadModal, SecoesDoDetalhe, ReatribuirVendedorModal, api.ts, formatos.ts, rotas.tsx (W5)
        └── configuracoes/        ConfiguracoesPage (abas) e rastreamento/ (aba Rastreamento, R2)
```

## Como a tela é montada

```
┌───────────────┬───────────────────────────────────────────────┐
│ sidebar       │ Marketing                     (faixa brand-950)│
│ (da casca;    ├───────────────────────────────────────────────┤
│  no modo      │ Painel · Leads · Configurações                 │
│  sozinho, uma │ (abas)                                         │
│  imitação só  ├───────────────────────────────────────────────┤
│  com o item   │                                               │
│  "Marketing") │ tela da aba (features/<area>/...Page.tsx)      │
└───────────────┴───────────────────────────────────────────────┘
```

- Sozinho (desenvolvimento), o Marketing é um item só na sidebar de mentira e as áreas são **abas**, uma por permissão (`marketing.dashboard.ver`, `marketing.lead.ver` e `marketing.configuracao.ver`); sem nenhuma, a tela diz "sem acesso".
- **Embutido na casca, não há abas internas:** a casca desenha os subitens do `itensSubmenu` (Painel, Leads, Configurações) no menu lateral e, a cada clique, recarrega o iframe em `/modulos/marketing/<rota>`. O módulo desenha só a faixa "Marketing" e a tela.
- A faixa "Marketing" é fixa e fica no layout. As telas **não** desenham cabeçalho próprio: título, contador e botões de ação de cada tela ficam numa barra no topo do conteúdo.
- Embutido na casca, o módulo não desenha a sidebar nem o header global, porque são da casca.

## Arquivos e responsabilidades

### `plataforma/`: fronteira com a casca

Hoje a casca do Grupo 2 abre o módulo num `<iframe>` e conversa por `postMessage`. Mas essa decisão é de outro grupo e pode mudar para micro-frontend ou front único. Por isso **tudo o que depende da casca passa por esta pasta**, e as telas nunca usam `window.parent`, `postMessage` ou armazenamento de token diretamente. Se o modelo mudar, basta escrever outra implementação da interface.

- **`tipos.ts`**: interface `Plataforma`:
  - `modo`: `'embutida'` ou `'sozinha'`;
  - `iniciar()`: prepara a comunicação e é chamado uma vez, antes de renderizar;
  - `obterToken()`, `aguardarToken()`, `aoTrocarToken(ouvinte)`: token só em memória, nunca em `localStorage`;
  - `temPermissao(codigo)`: o claim `perms` do token tem a permissão? (nas telas, o hook `usarPermissao`, que reage à troca do token; `usarTemSessao` diz se já há token);
  - `notificar(nivel, texto)`: pede à casca um aviso (`'sucesso'`, `'erro'` ou `'info'`); sozinho, vai para o console;
  - `informarNavegacao(rota)`: avisa a casca da rota atual;
  - `informarTokenExpirado()`: avisa a casca que a API respondeu `401`.
- **`embutida.ts`**: implementação para o iframe (Contrato §12). Registra o ouvinte de `message` **antes** de enviar `modulo:pronto`. Aceita mensagens só da mesma origem e vindas da janela-mãe. Recebe `plataforma:sessao` (token, `tenantId`, usuário e tema), `plataforma:token` e `plataforma:tema`, que aplica em `data-tema`. Envia `modulo:altura` sempre que a altura muda, além de `modulo:navegar`, `modulo:token-expirado` e `modulo:notificar`.
- **`altura.ts`**: acompanha a altura do conteúdo e mede a parte visível do iframe, publicada nas variáveis CSS `--area-topo` e `--area-altura` que o `Modal` usa para ficar centralizado onde o usuário está olhando; com um modal aberto, a altura informada inclui o modal inteiro.
- **`permissoes.ts` e `usarPermissao.ts`**: leem o claim `perms` do token (sem validar a assinatura) para esconder o que o usuário não pode usar. É só usabilidade: a API continua respondendo `403`.
- **`sozinha.ts`**: implementação para abrir o front direto no navegador, em desenvolvimento. O token vem de `VITE_TOKEN_DEV`. Sem ele, só as rotas públicas da API funcionam.
- **`index.ts`**: se a página está dentro de um iframe (`window.self !== window.top`), usa a embutida; senão, a sozinha. Exporta a instância única `plataforma`.

### `api/cliente.ts`: chamadas à API

- `requisitar<T>(caminho, opcoes?, { autenticar? })` chama `VITE_API_URL + caminho`. Coloca o `Authorization: Bearer` com o token da plataforma, esperando o primeiro token se ainda não houver. Devolve só o `data` do envelope.
- Se a API responder `401`, avisa a casca (`informarTokenExpirado`).
- Qualquer falha vira `ErroApi` (`status`, `message` e `erros[]` do envelope), para a tela mostrar a mensagem certa.
- `requisitarModulo<T>('crm', caminho)` faz o mesmo para a API de outro módulo, com o token do usuário (base `VITE_CRM_URL`, padrão `/api/crm`). Só o detalhe do lead usa, para os resumos de empresa e contato.
- Também exporta o tipo `Resposta<T>`, espelho do envelope da API.

### `api/eventos.ts`: fluxo SSE

`abrirFluxo` abre `GET /api/marketing/painel/eventos` com `fetch` + `ReadableStream` (o `EventSource` não aceita o cabeçalho `Authorization`), com reconexão de espera crescente e `Last-Event-ID`, e reabre quando o token muda. `InterpretadorSse` lê as mensagens (`id`, `event`, `data` e os comentários `: ping`).
Toda chamada HTTP do front passa por aqui. Não use `fetch` direto nas telas.

### `layout/LayoutModulo.tsx`

Layout de todas as rotas: a faixa fixa "Marketing" (`PageHeader`), as abas (só no modo sozinho) e o `<Outlet />`, onde a tela da aba aparece. A cada troca de rota, avisa a casca (`informarNavegacao`). Esconde as abas sem permissão, manda a rota de uma área sem permissão para a primeira liberada e mostra "sem acesso" se não houver nenhuma. No modo sozinho, desenha uma sidebar que imita a da casca, com o item "Marketing". A lista de abas é a constante `ABAS`.

### `rotas.tsx` e `features/<area>/rotas.tsx`

O roteador (`createBrowserRouter`, com `basename` igual ao caminho base) monta o `LayoutModulo` e junta as rotas que cada área exporta (`rotasPainel`, `rotasLeads`, `rotasConfiguracoes`). A rota `/` redireciona para `/painel`.

Cada área exporta as próprias rotas, com caminhos relativos. Assim, se um dia a plataforma virar um front único, as áreas podem ser montadas nele sem reescrever as telas.

### `components/ui/index.tsx`

Componentes base com **os mesmos nomes e a mesma API do Design System** (`Docs/DesignSystem/design-systemfinal.md`, seção 3). A implementação segue a da casca (`plataforma-integrador-2026-2`, `casca/src/components/ui/index.tsx`). Todos usam os tokens do tema e funcionam no claro e no escuro.

| Componente | Uso |
|---|---|
| `Button` | variantes `primary`, `secondary`, `outline`, `ghost`, `danger`; tamanhos `sm` e `md` |
| `Badge` | selo arredondado; cores `green`, `red`, `blue`, `indigo`, `yellow`, `gray`, `purple`, `orange` |
| `statusBadge(status, rotulo?)` | `Badge` com a cor do status (mapa do CRM + status do Marketing: faixas, temperatura, e-mails, boas-vindas) |
| `PageHeader` | faixa `brand-950` com título, subtítulo e ações; hoje usada pelo layout para a faixa "Marketing" |
| `Modal` | `open`, `onClose`, `title`, `subtitle`, `size` (`sm` 448 · `md` 576 · `lg` 672 · `xl` 896 · `full` 1152), `footer`, `noPad` (detalhe; clicar fora fecha). Esc fecha, foco preso e devolvido, `data-autofocus` escolhe o primeiro foco |
| `Input`, `Select`, `Textarea` | campos nativos estilizados; `invalid` marca erro. Dentro de um `FormField`, recebem rótulo, erro/dica e "obrigatório" sozinhos |
| `FormSection` | `number`, `title`; grade de 2 colunas (1 no celular) |
| `FormField` | `label`, `required`, `colSpan` (1 ou 2), `error`, `hint`, `htmlFor`; um controle por campo |
| `SearchBar` | `Input` de busca com lupa; `className` no contêiner (ex.: `flex-1`) |
| `FilterBar` | faixa de filtros; `label` nomeia o grupo |
| `ViewToggle` | `value` (`list` ou `cards`) e `onChange` |
| `Pagination` | `pagina` (começa em 0, como a API), `tamanho`, `total`, `onChange` |
| `DetailSection` / `DetailField` | detalhe em lista de definição; `DetailField` com `label`, `colSpan`, valor vazio vira "—" |
| `Stepper` | `steps` (rótulos) e `current` (começa em 0) |
| `Tabs` | `abas` (`{valor, rotulo, icone?, contador?}`), `ativa`, `onChange`, `escura` (sobre o cabeçalho `brand-950` do detalhe); setas trocam de aba |
| `Spinner` | carregando dentro do container; `rotulo` |
| `EmptyState` | lista vazia ou erro: `icone`, `titulo`, `descricao`, `acao`, `erro` |

Antes de criar um componente, veja se ele já existe no Design System e use o mesmo nome.

### Telas prontas

- **Painel** (`features/painel`, W6 e R6): filtros de período, origem e campanha, e os blocos funil, qualificação, série temporal por origem, campanhas, canais (custo "estimado"), distribuição, leads recentes (cada um leva a `/leads/{id}`), conversão 1 → 3 e o cartão do lead 0, com as quebras por campanha e por dispositivo. `usePainel` busca o resumo uma vez e aplica os eventos do fluxo SSE localmente; relê o resumo a cada 60 s com o fluxo no ar e a cada 30 s sem ele (mostra "Atualização em tempo real indisponível"). Os gráficos são SVG e CSS, sem biblioteca.
- **Leads** (`features/leads`, W5): lista com busca, filtros de etapa, faixa, origem, canal, campanha, vendedor e período (todos na URL), paginação e o aparelho da visita do anúncio; o detalhe abre em `Modal noPad` na rota `/leads/{id}` (a da busca global), com as abas Dados (incluindo boas-vindas, e-mails enviados e o histórico de consentimentos, com IP e *User-Agent* só aqui), Origem, Interação, Respostas, Pontuação e Timeline. Os resumos da empresa e do contato são buscados no CRM com o token do usuário: se a chamada falhar, só aquele campo diz "indisponível no CRM". A reatribuição do vendedor (`PUT /leads/{id}/vendedor`) fica escondida sem `marketing.qualificacao.editar`, mostra a mensagem do envelope em `422 VENDEDOR_FORA_DA_ESCALA` e `403`, e avisa a casca do sucesso. O filtro de vendedor usa `GET /rodizio/vendedores` (exige `marketing.qualificacao.ver`). O nome do vendedor fica indisponível até a X7 (mostra o id encurtado).
- **Configurações** (`features/configuracoes`, R2 e W4): as cinco abas completas (`?aba=`): **Rastreamento** (lead 0, links e visitas ao vivo), **Canais de anúncio** (`google_ads` e `meta_ads` com segredos só de escrita e URL do webhook), **Modelos de mensagem** (boas-vindas e cadência de aquecimento com pré-visualização), **Ciclo de vida** (tentativas e retenção LGPD) e **Qualificação e distribuição** (faixas com prévia, regras de pontuação por campo do Landing, Meta, UTM e comportamento, e escala de vendedores do rodízio). Roteiro da demo em [Docs/demo-iot.md](../Docs/demo-iot.md).

### `components/StatusApi.tsx` e `components/EmConstrucao.tsx`

- `StatusApi` chama `GET /health` sem token e mostra "verificando", "no ar" ou "fora do ar".
- `EmConstrucao` é o estado vazio de uma área sem funcionalidade, com a descrição do que ela vai ter. Hoje nenhuma área o usa.

### `index.css`

Importa o Tailwind v4 e define os tokens do Design System no `@theme` (`--color-brand-950` a `--color-brand-50` e a fonte Inter). Assim as classes `bg-brand-950`, `text-brand-700` etc. existem. **Nunca use cor em hex nos componentes**: use os tokens.

Também define os neutros que mudam com o tema (`bg-fundo`, `bg-superficie`, `bg-superficie-2`, `text-texto`, `text-texto-2`, `text-texto-3`, `text-titulo`, `border-borda`, `border-borda-forte`), com os mesmos valores do `ui/plataforma.css` do `infra-integrador-2026`, e a variante `dark:`, que segue o `data-tema="escuro"` aplicado pela camada `plataforma/`. Use os neutros no lugar de `bg-white`, `text-gray-700` e `border-gray-200`.

## Como adicionar uma tela nova

1. Crie ou use a pasta da área em `src/features/<area>/`, com a página (`XxxPage.tsx`) e a rota em `rotas.tsx`.
2. Se for uma área nova do menu, adicione as rotas em `src/rotas.tsx` e a aba na constante `ABAS` do `LayoutModulo`.
3. Busque dados só por `requisitar()` de `src/api/cliente.ts`.
4. Monte a tela com os componentes de `src/components/ui` e os padrões de tela do Design System. Se a ação cabe no contexto atual, use modal ou edição na própria tela em vez de rota nova.
5. Rode `npm run typecheck` antes do PR.

## Pontos em aberto

- O SSE do painel (`/painel/eventos`) passando pelo gateway da plataforma, que devolve `504` quando o módulo não responde em 3 s (X8): falta confirmar com o Grupo 2. Sem o fluxo, o painel segue funcionando por releitura a cada 30 s.
- O nome do vendedor no painel e na lista depende de o identity listar os usuários com perfil VENDEDOR (X7); até lá aparece o id encurtado.
- A aba Rastreamento de Configurações ainda relê as visitas a cada 5 s, em vez de usar o fluxo SSE.
