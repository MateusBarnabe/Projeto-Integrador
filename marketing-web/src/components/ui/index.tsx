import {
  createContext,
  useContext,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  type ButtonHTMLAttributes,
  type ComponentProps,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { Check, ChevronLeft, ChevronRight, LayoutGrid, List, Search, X } from 'lucide-react';

/*
 * Componentes base, com os mesmos nomes e a mesma API do Design System Centinela
 * (Docs/DesignSystem/design-systemfinal.md, seção 3). Antes de criar um componente novo,
 * confira se ele já existe na lista do Design System e mantenha o mesmo nome.
 *
 * A implementação segue a da casca (plataforma-integrador-2026-2, casca/src/components/ui), para o
 * código da casca e o do módulo se lerem do mesmo jeito. Cores: brand-* para a marca e os neutros
 * do tema (bg-superficie, text-texto, border-borda...) no lugar de branco e cinza, para os temas
 * claro e escuro funcionarem (ver src/index.css).
 */

function cx(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(' ');
}

// ------------------------------------------------------------------ Button

type VarianteBotao = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';

const estiloBotao: Record<VarianteBotao, string> = {
  primary: 'bg-brand-800 text-white hover:bg-brand-700',
  secondary:
    'bg-gray-100 text-gray-800 hover:bg-gray-200 dark:bg-superficie-2 dark:text-texto dark:hover:bg-borda',
  outline: 'border border-borda-forte bg-superficie text-texto-2 hover:bg-superficie-2 hover:text-texto',
  ghost: 'text-texto-2 hover:bg-superficie-2 hover:text-texto',
  danger: 'bg-red-600 text-white hover:bg-red-700',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: VarianteBotao;
  size?: 'sm' | 'md';
}

export function Button({ variant = 'primary', size = 'md', className = '', ...props }: ButtonProps) {
  const tamanho = size === 'sm' ? 'px-3 py-1.5 text-xs' : 'px-4 py-2 text-sm';
  return (
    <button
      className={`inline-flex items-center gap-1.5 rounded-lg font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 ${tamanho} ${estiloBotao[variant]} ${className}`}
      {...props}
    />
  );
}

// ------------------------------------------------------------------ Badge e statusBadge

type VarianteBadge = 'green' | 'red' | 'blue' | 'indigo' | 'yellow' | 'gray' | 'purple' | 'orange';

const estiloBadge: Record<VarianteBadge, string> = {
  green: 'bg-brand-100 text-brand-800 dark:bg-brand-800/30 dark:text-brand-300',
  red: 'bg-red-50 text-red-700 dark:bg-red-500/15 dark:text-red-300',
  blue: 'bg-blue-50 text-blue-700 dark:bg-blue-500/15 dark:text-blue-300',
  indigo: 'bg-indigo-50 text-indigo-700 dark:bg-indigo-500/15 dark:text-indigo-300',
  yellow: 'bg-yellow-50 text-yellow-800 dark:bg-yellow-500/15 dark:text-yellow-300',
  gray: 'bg-gray-100 text-gray-600 dark:bg-borda dark:text-texto-2',
  purple: 'bg-purple-50 text-purple-700 dark:bg-purple-500/15 dark:text-purple-300',
  orange: 'bg-orange-50 text-orange-700 dark:bg-orange-500/15 dark:text-orange-300',
};

export function Badge({
  variant = 'gray',
  className,
  children,
}: {
  variant?: VarianteBadge;
  className?: string;
  children: ReactNode;
}) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap',
        estiloBadge[variant],
        className,
      )}
    >
      {children}
    </span>
  );
}

/**
 * Cor de cada status (Design System, seção 7). As chaves estão normalizadas: minúsculas, sem acento,
 * com espaço e hífen trocados por "_". Os status do Marketing estendem o mapa do CRM sem criar cor
 * nova. Vermelho só para erro e para o que não volta (cancelado, falhou).
 */
const varianteDoStatus: Record<string, VarianteBadge> = {
  // Design System / CRM
  ativo: 'green',
  cliente_ativo: 'green',
  adimplente: 'green',
  concluido: 'green',
  em_andamento: 'blue',
  lead: 'blue',
  prospect: 'indigo',
  pendente: 'indigo',
  rascunho: 'yellow',
  em_elaboracao: 'yellow',
  inativo: 'red',
  cancelado: 'red',
  inadimplente: 'red',
  suspenso: 'orange',
  atencao: 'purple',
  // Marketing: faixa de qualificação e temperatura
  mql_quente: 'orange',
  mql_morno: 'yellow',
  nutricao: 'blue',
  quente: 'orange',
  medio: 'yellow',
  frio: 'blue',
  // Marketing: e-mails e boas-vindas
  enviado: 'blue',
  enviada: 'green',
  entregue: 'green',
  aberto: 'indigo',
  clicado: 'green',
  falhou: 'red',
  // Marketing: estados do lead
  aguardando_vendedor: 'yellow',
  aguardando_landing: 'yellow',
  no_crm: 'green',
  descadastrado: 'gray',
  anonimizado: 'gray',
};

function normalizarStatus(status: string) {
  return status
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[\s-]+/g, '_');
}

/** `<Badge>` com a cor do status. Sem `rotulo`, mostra o próprio status com "_" virando espaço. */
export function statusBadge(status: string, rotulo: ReactNode = status.replace(/_/g, ' ')) {
  return <Badge variant={varianteDoStatus[normalizarStatus(status)] ?? 'gray'}>{rotulo}</Badge>;
}

// ------------------------------------------------------------------ PageHeader

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

/**
 * Faixa escura de cabeçalho (Design System, 12.3). No módulo, só o LayoutModulo a usa, para a
 * faixa fixa "Marketing"; as telas das abas não desenham cabeçalho próprio.
 */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="flex shrink-0 items-center justify-between bg-brand-950 px-5 py-3.5 text-white">
      <div>
        <h1 className="text-lg font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-0.5 text-xs text-brand-300">{subtitle}</p>}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}

// ------------------------------------------------------------------ Modal

const larguraDoModal = {
  sm: 'max-w-md', // 448px: confirmação
  md: 'max-w-xl', // 576px
  lg: 'max-w-2xl', // 672px
  xl: 'max-w-4xl', // 896px: padrão para formulários e detalhe
  full: 'max-w-6xl', // 1152px
};

/** Modais abertos, do mais antigo ao mais novo: o Esc fecha só o de cima. */
const pilhaDeModais: string[] = [];

const FOCAVEIS =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  /** No modal noPad o título não aparece, mas nomeia o diálogo para o leitor de tela. */
  title?: string;
  subtitle?: string;
  size?: keyof typeof larguraDoModal;
  footer?: ReactNode;
  /** Painel de detalhe com cabeçalho próprio (Design System, 5.2). Clicar fora fecha. */
  noPad?: boolean;
  children: ReactNode;
}

/**
 * Todo overlay do módulo (Design System, seção 5).
 * - Formulário e confirmação: com `title`. Clicar fora não fecha, para ninguém perder o que digitou.
 * - Detalhe de entidade: `noPad`, com o cabeçalho brand-950 desenhado pelo painel. Clicar fora fecha.
 * Esc fecha o modal de cima; o foco fica preso no modal e volta para quem o abriu ao fechar.
 * O primeiro foco vai para o elemento com `data-autofocus` (ou `autoFocus`) ou para o primeiro
 * campo do conteúdo.
 */
export function Modal({ open, onClose, title, subtitle, size = 'xl', footer, noPad = false, children }: ModalProps) {
  const id = useId();
  const painel = useRef<HTMLDivElement>(null);
  const conteudo = useRef<HTMLDivElement>(null);
  const fechar = useRef(onClose);

  useLayoutEffect(() => {
    fechar.current = onClose;
  });

  useEffect(() => {
    if (!open) return;
    const anterior = document.activeElement as HTMLElement | null;
    pilhaDeModais.push(id);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    // O autoFocus do React já focou antes deste efeito: nesse caso, não mexe
    if (!painel.current?.contains(document.activeElement)) {
      const inicial =
        painel.current?.querySelector<HTMLElement>('[data-autofocus]') ??
        conteudo.current?.querySelector<HTMLElement>(FOCAVEIS) ??
        painel.current?.querySelector<HTMLElement>(FOCAVEIS);
      (inicial ?? painel.current)?.focus();
    }

    function teclado(evento: KeyboardEvent) {
      if (pilhaDeModais[pilhaDeModais.length - 1] !== id) return;
      if (evento.key === 'Escape') {
        evento.stopPropagation();
        fechar.current();
        return;
      }
      if (evento.key === 'Tab' && painel.current) {
        const focaveis = [...painel.current.querySelectorAll<HTMLElement>(FOCAVEIS)].filter(
          (elemento) => elemento.offsetParent !== null,
        );
        if (focaveis.length === 0) {
          evento.preventDefault();
          return;
        }
        const primeiro = focaveis[0];
        const ultimo = focaveis[focaveis.length - 1];
        const ativo = document.activeElement;
        if (evento.shiftKey && (ativo === primeiro || ativo === painel.current)) {
          evento.preventDefault();
          ultimo.focus();
        } else if (!evento.shiftKey && ativo === ultimo) {
          evento.preventDefault();
          primeiro.focus();
        }
      }
    }

    window.addEventListener('keydown', teclado);
    return () => {
      window.removeEventListener('keydown', teclado);
      pilhaDeModais.splice(pilhaDeModais.indexOf(id), 1);
      if (pilhaDeModais.length === 0) document.body.style.overflow = overflow;
      anterior?.focus?.();
    };
  }, [open, id]);

  if (!open) return null;

  const comCabecalho = !noPad && title;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-3 sm:items-center sm:p-6">
      <div aria-hidden className="fixed inset-0 bg-brand-950/60" onClick={noPad ? onClose : undefined} />
      <div
        ref={painel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={comCabecalho ? `${id}-titulo` : undefined}
        aria-describedby={comCabecalho && subtitle ? `${id}-subtitulo` : undefined}
        aria-label={noPad ? title : undefined}
        tabIndex={-1}
        className={cx(
          'relative flex max-h-full w-full flex-col overflow-hidden rounded-xl bg-superficie text-texto shadow-2xl outline-none',
          larguraDoModal[size],
        )}
      >
        {comCabecalho && (
          <div className="flex shrink-0 items-start justify-between gap-4 border-b border-borda px-6 pt-5 pb-4">
            <div className="min-w-0">
              <h2 id={`${id}-titulo`} className="text-lg font-bold text-titulo">
                {title}
              </h2>
              {subtitle && (
                <p id={`${id}-subtitulo`} className="mt-0.5 text-sm text-texto-3">
                  {subtitle}
                </p>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              aria-label="Fechar"
              className="-mr-1.5 shrink-0 rounded-lg p-1.5 text-texto-3 hover:bg-superficie-2 hover:text-texto"
            >
              <X aria-hidden className="h-4 w-4" />
            </button>
          </div>
        )}
        <div
          ref={conteudo}
          className={noPad ? 'flex min-h-0 flex-1 flex-col' : 'min-h-0 flex-1 overflow-y-auto px-6 py-5'}
        >
          {children}
        </div>
        {footer && (
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-2 border-t border-borda bg-superficie-2 px-6 py-3.5">
            {footer}
          </div>
        )}
      </div>
    </div>,
    document.body,
  );
}

// ------------------------------------------------------------------ campos: Input, Select, Textarea

/** O FormField entrega ao campo de dentro o id do rótulo, a mensagem de erro/dica e o "obrigatório". */
interface ContextoCampo {
  id: string;
  descricaoId?: string;
  invalido: boolean;
  obrigatorio: boolean;
}

const ContextoDoCampo = createContext<ContextoCampo | null>(null);

interface PropsDeAcessibilidade {
  id?: string;
  invalid?: boolean;
  'aria-describedby'?: string;
  'aria-required'?: boolean | 'true' | 'false';
}

function useAtributosDoCampo({ id, invalid, ...aria }: PropsDeAcessibilidade) {
  const campo = useContext(ContextoDoCampo);
  const invalido = invalid ?? campo?.invalido ?? false;
  const descricao = [aria['aria-describedby'], campo?.descricaoId].filter(Boolean).join(' ');
  return {
    id: id ?? campo?.id,
    'aria-invalid': invalido || undefined,
    'aria-describedby': descricao || undefined,
    'aria-required': aria['aria-required'] ?? (campo?.obrigatorio || undefined),
  };
}

const estiloCampo =
  'rounded-lg border border-borda-forte bg-superficie px-3 text-sm text-texto placeholder:text-texto-3 outline-none transition-colors ' +
  'focus:border-brand-700 focus:ring-2 focus:ring-brand-700/20 disabled:cursor-not-allowed disabled:bg-superficie-2 disabled:text-texto-3 ' +
  'aria-invalid:border-red-500 aria-invalid:focus:ring-red-500/20';

/** Largura total por padrão; uma classe de largura passada pela tela (w-*, min-w-*, flex-*) substitui. */
function largura(className?: string) {
  return /(^|\s)(w-|min-w-|flex-)/.test(className ?? '') ? null : 'w-full';
}

type InputProps = ComponentProps<'input'> & { invalid?: boolean };

/** Campo de texto de uma linha. Dentro de um FormField, recebe o rótulo, o erro e o "obrigatório". */
export function Input({ className, invalid, id, ...props }: InputProps) {
  const atributos = useAtributosDoCampo({ id, invalid, ...props });
  return <input {...props} {...atributos} className={cx(estiloCampo, 'h-9', largura(className), className)} />;
}

type SelectProps = ComponentProps<'select'> & { invalid?: boolean };

/** Seleção nativa estilizada. As opções vêm como `<option>` filhos. */
export function Select({ className, invalid, id, children, ...props }: SelectProps) {
  const atributos = useAtributosDoCampo({ id, invalid, ...props });
  return (
    <select {...props} {...atributos} className={cx(estiloCampo, 'h-9 pr-8', largura(className), className)}>
      {children}
    </select>
  );
}

type TextareaProps = ComponentProps<'textarea'> & { invalid?: boolean };

/** Campo de texto de várias linhas. */
export function Textarea({ className, invalid, id, ...props }: TextareaProps) {
  const atributos = useAtributosDoCampo({ id, invalid, ...props });
  return (
    <textarea {...props} {...atributos} className={cx(estiloCampo, 'min-h-20 py-2', largura(className), className)} />
  );
}

/**
 * Campo de busca com a lupa embutida. `className` vai no contêiner (ex.: `flex-1`, `w-72`).
 * Fora de um FormField, sem `aria-label`, o leitor de tela anuncia "Buscar".
 */
export function SearchBar({ className, ...props }: ComponentProps<'input'>) {
  const campo = useContext(ContextoDoCampo);
  const semRotulo = !campo && !props['aria-label'] && !props['aria-labelledby'];
  return (
    <div className={cx('relative', className)}>
      <Search
        aria-hidden
        className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-texto-3"
      />
      <Input type="search" aria-label={semRotulo ? 'Buscar' : undefined} {...props} className="pl-9" />
    </div>
  );
}

// ------------------------------------------------------------------ formulário: FormSection, FormField

function NumeroDaSecao({ number }: { number: number }) {
  return (
    <span
      aria-hidden
      className="grid h-5 w-5 shrink-0 place-items-center rounded-full bg-brand-100 text-xs font-bold text-brand-800 dark:bg-brand-800/40 dark:text-brand-300"
    >
      {number}
    </span>
  );
}

function TituloDaSecao({ id, number, title }: { id: string; number?: number; title: string }) {
  return (
    <div className="mb-3 flex items-center gap-2">
      {number !== undefined && <NumeroDaSecao number={number} />}
      <h3 id={id} className="text-sm font-semibold text-titulo">
        {title}
      </h3>
      <span aria-hidden className="h-px flex-1 bg-borda" />
    </div>
  );
}

/**
 * Agrupa campos com número e título, numa grade de duas colunas (uma no celular), Design System 6.
 * Campos condicionais vão por último, para não abrir buraco na grade.
 */
export function FormSection({
  number,
  title,
  children,
  className,
}: {
  number?: number;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={cx('mb-6 last:mb-0', className)}>
      <TituloDaSecao id={id} number={number} title={title} />
      <div className="grid grid-cols-1 gap-x-4 gap-y-3.5 md:grid-cols-2">{children}</div>
    </section>
  );
}

interface FormFieldProps {
  label: string;
  required?: boolean;
  /** 2 ocupa a linha inteira da FormSection. */
  colSpan?: 1 | 2;
  /** Mensagem do servidor (errors[] do envelope) ou da validação local. Marca o campo como inválido. */
  error?: string | null;
  hint?: ReactNode;
  /** id do controle, quando ele precisa de um id próprio; o Input/Select/Textarea de dentro o recebe. */
  htmlFor?: string;
  children: ReactNode;
}

/**
 * Rótulo + campo + erro ou dica. O Input, Select, Textarea ou SearchBar de dentro recebe
 * automaticamente o id do rótulo, `aria-describedby`, `aria-invalid` e `aria-required`.
 * Um controle por FormField.
 */
export function FormField({ label, required = false, colSpan = 1, error, hint, htmlFor, children }: FormFieldProps) {
  const gerado = useId();
  const id = htmlFor ?? gerado;
  const descricaoId = error ? `${id}-erro` : hint ? `${id}-dica` : undefined;
  return (
    <ContextoDoCampo.Provider value={{ id, descricaoId, invalido: !!error, obrigatorio: required }}>
      <div className={cx('flex min-w-0 flex-col gap-1.5', colSpan === 2 && 'md:col-span-2')}>
        <label htmlFor={id} className="text-xs font-medium text-texto-2">
          {label}
          {required && (
            <span aria-hidden className="ml-0.5 text-red-600 dark:text-red-400">
              *
            </span>
          )}
        </label>
        {children}
        {error ? (
          <p id={`${id}-erro`} role="alert" className="text-xs text-red-600 dark:text-red-400">
            {error}
          </p>
        ) : (
          hint && (
            <p id={`${id}-dica`} className="text-xs text-texto-3">
              {hint}
            </p>
          )
        )}
      </div>
    </ContextoDoCampo.Provider>
  );
}

// ------------------------------------------------------------------ listas: FilterBar, ViewToggle, Pagination

/** Faixa horizontal de filtros, entre a barra da tela e a lista (Design System 12.1). */
export function FilterBar({
  children,
  label = 'Filtros',
  className,
}: {
  children: ReactNode;
  /** Nome do grupo para o leitor de tela. */
  label?: string;
  className?: string;
}) {
  return (
    <div
      role="group"
      aria-label={label}
      className={cx('flex flex-wrap items-center gap-2 border-b border-borda bg-superficie px-5 py-3', className)}
    >
      {children}
    </div>
  );
}

export type Visualizacao = 'list' | 'cards';

const opcoesDeVisualizacao = [
  { valor: 'list', rotulo: 'Lista', Icone: List },
  { valor: 'cards', rotulo: 'Cards', Icone: LayoutGrid },
] as const;

/** Alternador lista ↔ cards, em botões segmentados; o ativo fica em brand-950. */
export function ViewToggle({
  value,
  onChange,
  className,
}: {
  value: Visualizacao;
  onChange: (valor: Visualizacao) => void;
  className?: string;
}) {
  return (
    <div
      role="group"
      aria-label="Visualização"
      className={cx(
        'inline-flex shrink-0 divide-x divide-borda-forte overflow-hidden rounded-lg border border-borda-forte bg-superficie',
        className,
      )}
    >
      {opcoesDeVisualizacao.map(({ valor, rotulo, Icone }) => {
        const ativo = value === valor;
        return (
          <button
            key={valor}
            type="button"
            aria-pressed={ativo}
            onClick={() => onChange(valor)}
            className={cx(
              'inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium transition-colors focus-visible:-outline-offset-2',
              ativo ? 'bg-brand-950 text-white dark:bg-brand-800' : 'text-texto-2 hover:bg-superficie-2 hover:text-texto',
            )}
          >
            <Icone aria-hidden className="h-3 w-3 shrink-0" />
            {rotulo}
          </button>
        );
      })}
    </div>
  );
}

/**
 * Paginação no formato da plataforma (Contrato §8.3): `pagina` começa em 0, como no
 * `{itens, pagina, tamanho, total}` da API. Mostra no máximo cinco números em volta da página atual.
 */
export function Pagination({
  pagina,
  tamanho,
  total,
  onChange,
}: {
  pagina: number;
  tamanho: number;
  total: number;
  onChange: (pagina: number) => void;
}) {
  if (total <= 0) return null;
  const paginas = Math.max(1, Math.ceil(total / tamanho));
  if (paginas === 1 && pagina === 0) {
    return <p className="px-1 py-3 text-xs text-texto-3">{total === 1 ? '1 registro' : `${total} registros`}</p>;
  }
  const inicio = Math.max(0, Math.min(pagina - 2, paginas - 5));
  const visiveis = Array.from({ length: Math.min(5, paginas) }, (_, indice) => inicio + indice);
  return (
    <nav
      aria-label="Paginação"
      className="flex flex-wrap items-center justify-between gap-2 px-1 py-3 text-xs text-texto-3"
    >
      <p aria-live="polite">
        Mostrando {pagina * tamanho + 1}–{Math.min(total, (pagina + 1) * tamanho)} de {total}
      </p>
      <div className="flex items-center gap-1">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={pagina === 0}
          onClick={() => onChange(pagina - 1)}
          aria-label="Página anterior"
        >
          <ChevronLeft aria-hidden className="h-3 w-3 shrink-0" />
        </Button>
        {visiveis.map((numero) => (
          <Button
            key={numero}
            type="button"
            variant={numero === pagina ? 'primary' : 'ghost'}
            size="sm"
            aria-current={numero === pagina ? 'page' : undefined}
            aria-label={`Página ${numero + 1}`}
            onClick={() => onChange(numero)}
            className="min-w-8 justify-center"
          >
            {numero + 1}
          </Button>
        ))}
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={pagina >= paginas - 1}
          onClick={() => onChange(pagina + 1)}
          aria-label="Próxima página"
        >
          <ChevronRight aria-hidden className="h-3 w-3 shrink-0" />
        </Button>
      </div>
    </nav>
  );
}

// ------------------------------------------------------------------ detalhe: DetailSection, DetailField

/** Agrupa DetailField com número e título, em lista de definição de duas colunas (uma no celular). */
export function DetailSection({
  number,
  title,
  children,
  className,
}: {
  number?: number;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  const id = useId();
  return (
    <section aria-labelledby={id} className={cx('mb-6 last:mb-0', className)}>
      <TituloDaSecao id={id} number={number} title={title} />
      <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">{children}</dl>
    </section>
  );
}

/** Par rótulo + valor de um painel de detalhe. Valor vazio aparece como "—". */
export function DetailField({
  label,
  children,
  colSpan = 1,
}: {
  label: string;
  children?: ReactNode;
  colSpan?: 1 | 2;
}) {
  const vazio = children === null || children === undefined || children === '';
  return (
    <div className={cx('min-w-0', colSpan === 2 && 'sm:col-span-2')}>
      <dt className="text-xs text-texto-3">{label}</dt>
      <dd className="mt-0.5 text-sm break-words text-texto">
        {vazio ? (
          <>
            <span aria-hidden className="text-texto-3">
              —
            </span>
            <span className="sr-only">não informado</span>
          </>
        ) : (
          children
        )}
      </dd>
    </div>
  );
}

// ------------------------------------------------------------------ Stepper

/**
 * Progresso de um assistente em vários passos. `current` começa em 0; os anteriores aparecem como
 * concluídos. No celular, só o rótulo do passo atual aparece (os outros continuam no leitor de tela).
 */
export function Stepper({ steps, current, className }: { steps: string[]; current: number; className?: string }) {
  return (
    <ol aria-label="Passos" className={cx('flex items-center gap-2', className)}>
      {steps.map((passo, indice) => {
        const concluido = indice < current;
        const atual = indice === current;
        return (
          <li
            key={indice}
            aria-current={atual ? 'step' : undefined}
            className={cx('flex min-w-0 items-center gap-2', indice < steps.length - 1 && 'flex-1')}
          >
            <span
              aria-hidden
              className={cx(
                'grid h-6 w-6 shrink-0 place-items-center rounded-full text-xs font-bold',
                concluido && 'bg-brand-700 text-white',
                atual && 'bg-brand-800 text-white ring-4 ring-brand-100 dark:ring-brand-800/40',
                !concluido && !atual && 'border border-borda-forte bg-superficie text-texto-3',
              )}
            >
              {concluido ? <Check className="h-3 w-3" /> : indice + 1}
            </span>
            <span
              className={cx(
                'truncate text-xs',
                atual ? 'font-semibold text-titulo' : 'sr-only text-texto-3 sm:not-sr-only',
                concluido && 'text-texto-2',
              )}
            >
              {passo}
              <span className="sr-only">
                {concluido ? ' (concluído)' : atual ? ' (atual)' : ''}, passo {indice + 1} de {steps.length}
              </span>
            </span>
            {indice < steps.length - 1 && (
              <span aria-hidden className={cx('h-px min-w-4 flex-1', concluido ? 'bg-brand-700' : 'bg-borda')} />
            )}
          </li>
        );
      })}
    </ol>
  );
}
