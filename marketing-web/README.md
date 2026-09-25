# marketing-web

Front do módulo de Marketing (Etapa 2 do projeto). É a área autenticada que a pessoa gestora de marketing usa dentro da plataforma: painel, leads, construtor de formulário e configurações.

> **Estado atual (25/09/2026): alicerce.** O layout, a navegação por abas, a camada de integração com a casca e o cliente HTTP estão prontos. As quatro áreas mostram só um aviso de "em construção". O Painel já consulta o `/health` da API. O que falta está em [PROXIMOS-PASSOS.md](../PROXIMOS-PASSOS.md).

| | |
|---|---|
| Stack | React 19, TypeScript 5.9, Vite 8, Tailwind CSS v4, react-router 8, ícones `lucide-react` |
| Porta | `3007` |
| Caminho base | `/modulos/marketing/` (configurável) |
| Nome na plataforma | serviço e imagem `marketing-front` |
| Design | [Design System Centinela](../Docs/DesignSystem/) (tokens `brand-*`, fonte Inter) |

**O que não fica aqui:** a landing page e o formulário que o visitante preenche. Eles são do módulo Landing (Grupo 7) e ficam na página pública. Localmente, quem os simula é o [landing-web](../landing-web/). Aqui fica só o **construtor** dos campos desse formulário.

## Como rodar

```bash
npm install
npm run dev          # http://localhost:3007/modulos/marketing/
```

Da raiz do repositório também funciona: `npm run dev:web`, ou `npm run dev` para subir a API, este front e o placeholder juntos.

Em desenvolvimento, o Vite repassa `/api/marketing` para a API em `http://localhost:8087`. Então a API precisa estar rodando para o selo "API do módulo" ficar verde.

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
    │   ├── sozinha.ts            implementação para abrir direto no navegador
    │   └── index.ts              escolhe a implementação e exporta `plataforma`
    ├── api/cliente.ts            ÚNICO ponto de chamada HTTP para a API
    ├── layout/LayoutModulo.tsx   faixa "Marketing", abas das áreas e sidebar do modo sozinho
    ├── components/
    │   ├── ui/index.tsx          componentes base com os nomes do Design System
    │   ├── StatusApi.tsx         selo "API do módulo: no ar / fora do ar"
    │   └── EmConstrucao.tsx      estado vazio das áreas sem funcionalidade
    └── features/                 uma pasta por área do módulo
        ├── painel/               PainelPage.tsx + rotas.tsx
        ├── leads/                LeadsPage.tsx + rotas.tsx
        ├── formularios/          FormulariosPage.tsx + rotas.tsx
        └── configuracoes/        ConfiguracoesPage.tsx + rotas.tsx
```

## Como a tela é montada

```
┌───────────────┬───────────────────────────────────────────────┐
│ sidebar       │ Marketing                     (faixa brand-950)│
│ (da casca;    ├───────────────────────────────────────────────┤
│  no modo      │ Painel · Leads · Formulários · Configurações   │
│  sozinho, uma │ (abas)                                         │
│  imitação só  ├───────────────────────────────────────────────┤
│  com o item   │                                               │
│  "Marketing") │ tela da aba (features/<area>/...Page.tsx)      │
└───────────────┴───────────────────────────────────────────────┘
```

- Na sidebar da casca, o Marketing é **um item só**. As áreas do módulo são **abas**, desenhadas pelo próprio módulo.
- A faixa "Marketing" é fixa e fica no layout. As telas **não** desenham cabeçalho próprio: título, contador e botões de ação de cada tela ficam numa barra no topo do conteúdo.
- Embutido na casca, o módulo não desenha a sidebar nem o header global, porque são da casca.

## Arquivos e responsabilidades

### `plataforma/`: fronteira com a casca

Hoje a casca do Grupo 2 abre o módulo num `<iframe>` e conversa por `postMessage`. Mas essa decisão é de outro grupo e pode mudar para micro-frontend ou front único. Por isso **tudo o que depende da casca passa por esta pasta**, e as telas nunca usam `window.parent`, `postMessage` ou armazenamento de token diretamente. Se o modelo mudar, basta escrever outra implementação da interface.

- **`tipos.ts`**: interface `Plataforma`:
  - `modo`: `'embutida'` ou `'sozinha'`;
  - `iniciar()`: prepara a comunicação e é chamado uma vez, antes de renderizar;
  - `obterToken()`, `aguardarToken()`, `aoTrocarToken(ouvinte)`: token só em memória, nunca em `localStorage`;
  - `informarNavegacao(rota)`: avisa a casca da rota atual;
  - `informarTokenExpirado()`: avisa a casca que a API respondeu `401`.
- **`embutida.ts`**: implementação para o iframe (Contrato §12). Registra o ouvinte de `message` **antes** de enviar `modulo:pronto`. Aceita mensagens só da mesma origem e vindas da janela-mãe. Recebe `plataforma:token` e `plataforma:tema`, que aplica em `data-tema`. Envia `modulo:altura` sempre que a altura muda, além de `modulo:navegar` e `modulo:token-expirado`. O formato exato das mensagens ainda precisa ser conferido com o `exemplo-modulo` do `infra-integrador-2026`.
- **`sozinha.ts`**: implementação para abrir o front direto no navegador, em desenvolvimento. O token vem de `VITE_TOKEN_DEV`. Sem ele, só as rotas públicas da API funcionam.
- **`index.ts`**: se a página está dentro de um iframe (`window.self !== window.top`), usa a embutida; senão, a sozinha. Exporta a instância única `plataforma`.

### `api/cliente.ts`: chamadas à API

- `requisitar<T>(caminho, opcoes?, { autenticar? })` chama `VITE_API_URL + caminho`. Coloca o `Authorization: Bearer` com o token da plataforma, esperando o primeiro token se ainda não houver. Devolve só o `data` do envelope.
- Se a API responder `401`, avisa a casca (`informarTokenExpirado`).
- Qualquer falha vira `ErroApi` (`status`, `message` e `erros[]` do envelope), para a tela mostrar a mensagem certa.
- Também exporta o tipo `Resposta<T>`, espelho do envelope da API.

Toda chamada HTTP do front passa por aqui. Não use `fetch` direto nas telas.

### `layout/LayoutModulo.tsx`

Layout de todas as rotas: a faixa fixa "Marketing" (`PageHeader`), as abas (`AbasModulo`) e o `<Outlet />`, onde a tela da aba aparece. A cada troca de rota, avisa a casca (`informarNavegacao`). No modo sozinho, desenha uma sidebar que imita a da casca, com o item "Marketing". A lista de abas é a constante `ABAS`.

### `rotas.tsx` e `features/<area>/rotas.tsx`

O roteador (`createBrowserRouter`, com `basename` igual ao caminho base) monta o `LayoutModulo` e junta as rotas que cada área exporta (`rotasPainel`, `rotasLeads`, `rotasFormularios`, `rotasConfiguracoes`). A rota `/` redireciona para `/painel`.

Cada área exporta as próprias rotas, com caminhos relativos. Assim, se um dia a plataforma virar um front único, as áreas podem ser montadas nele sem reescrever as telas.

### `components/ui/index.tsx`

Componentes base com **os mesmos nomes e a mesma API do Design System** (`Docs/DesignSystem/design-systemfinal.md`). Por enquanto existem três:

| Componente | Uso |
|---|---|
| `Button` | variantes `primary`, `secondary`, `outline`, `ghost`, `danger`; tamanhos `sm` e `md` |
| `Badge` | selo arredondado; cores `green`, `red`, `blue`, `indigo`, `yellow`, `gray`, `purple` |
| `PageHeader` | faixa `brand-950` com título, subtítulo e ações; hoje usada pelo layout para a faixa "Marketing" |

Os outros componentes do Design System (`Modal`, `Input`, `Select`, `Textarea`, `FormSection`, `FormField`, `SearchBar`, `FilterBar`, `Pagination`, `ViewToggle`, `DetailField`, `DetailSection`, `Stepper`, `statusBadge`) são a tarefa W1. Antes de criar um componente, veja se ele já existe no Design System e use o mesmo nome.

### `components/StatusApi.tsx` e `components/EmConstrucao.tsx`

- `StatusApi` chama `GET /health` sem token e mostra "verificando", "no ar" ou "fora do ar".
- `EmConstrucao` é o estado vazio das áreas que ainda não têm funcionalidade, com a descrição do que a área vai ter.

### `index.css`

Importa o Tailwind v4 e define os tokens do Design System no `@theme` (`--color-brand-950` a `--color-brand-50` e a fonte Inter). Assim as classes `bg-brand-950`, `text-brand-700` etc. existem. **Nunca use cor em hex nos componentes**: use os tokens.

## Como adicionar uma tela nova

1. Crie ou use a pasta da área em `src/features/<area>/`, com a página (`XxxPage.tsx`) e a rota em `rotas.tsx`.
2. Se for uma área nova do menu, adicione as rotas em `src/rotas.tsx` e a aba na constante `ABAS` do `LayoutModulo`.
3. Busque dados só por `requisitar()` de `src/api/cliente.ts`.
4. Monte a tela com os componentes de `src/components/ui` e os padrões de tela do Design System. Se a ação cabe no contexto atual, use modal ou edição na própria tela em vez de rota nova.
5. Rode `npm run typecheck` antes do PR.

## Pontos em aberto

- Formato exato das mensagens da casca e aplicação do tema: conferir com o `exemplo-modulo` do `infra-integrador-2026` (tarefa W2).
- Esconder as abas que o usuário não tem permissão de ver, a partir das permissões do token. É só usabilidade, porque a API continua respondendo `403`.
- O manifesto [Docs/modulos/marketing.json](../Docs/modulos/marketing.json) declara `itensSubmenu`. Se a casca desenhar esses subitens na sidebar, as áreas aparecem duas vezes: na sidebar e nas abas. Isso precisa ser combinado com o Grupo 2.
