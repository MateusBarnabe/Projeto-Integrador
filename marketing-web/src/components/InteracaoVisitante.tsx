import type { ReactNode } from 'react';
import {
  ArrowDownToLine,
  Clock,
  Globe,
  MapPin,
  Monitor,
  MousePointerClick,
  Route,
  Smartphone,
  Tablet,
  type LucideIcon,
} from 'lucide-react';
import { Badge } from '@/components/ui';

/*
 * Contexto técnico do aparelho e comportamento na landing page de quem clicou no anúncio (R1).
 * Aparece no detalhe do lead (visita que converteu) e na lista de visitas de Configurações ›
 * Rastreamento ("lead 0"). Formato de leads.contexto_tecnico e leads.comportamento.
 */

export interface Localizacao {
  cidade?: string;
  uf?: string;
  pais?: string;
  simulada?: boolean;
}

export interface ContextoTecnico {
  tipoDispositivo?: string;
  sistema?: string;
  navegador?: string;
  localizacao?: Localizacao;
  idioma?: string;
  fuso?: string;
  tela?: string;
}

export interface Comportamento {
  tempoNaPaginaSegundos?: number;
  profundidadeRolagem?: number;
  elementosClicados?: string[];
  paginasVisitadas?: string[];
}

const ICONE_DISPOSITIVO: Record<string, LucideIcon> = {
  smartphone: Smartphone,
  tablet: Tablet,
  desktop: Monitor,
};

const NOME_DISPOSITIVO: Record<string, string> = {
  smartphone: 'Smartphone',
  tablet: 'Tablet',
  desktop: 'Computador',
  outro: 'Outro',
};

/** Ícone e nome do tipo de dispositivo, para listas. */
export function Dispositivo({ tipo, compacto = false }: { tipo?: string | null; compacto?: boolean }) {
  if (!tipo) return null;
  const Icone = ICONE_DISPOSITIVO[tipo] ?? Monitor;
  const nome = NOME_DISPOSITIVO[tipo] ?? tipo;
  return (
    <span className="inline-flex items-center gap-1.5 text-texto-2" title={nome}>
      <Icone aria-hidden className="h-4 w-4 shrink-0 text-brand-700" />
      {compacto ? <span className="sr-only">{nome}</span> : nome}
    </span>
  );
}

export function formatarDuracao(segundos?: number | null) {
  if (segundos === undefined || segundos === null) return null;
  if (segundos < 60) return `${segundos} s`;
  const minutos = Math.floor(segundos / 60);
  const resto = segundos % 60;
  return resto ? `${minutos} min ${resto} s` : `${minutos} min`;
}

export function formatarLocal(local?: Localizacao | null) {
  if (!local?.cidade) return null;
  return [local.cidade, local.uf, local.pais].filter(Boolean).join(', ');
}

function Metrica({ icone: Icone, rotulo, children }: { icone: LucideIcon; rotulo: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 items-start gap-3 rounded-xl border border-borda bg-superficie p-3">
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-brand-100 text-brand-800 dark:bg-brand-800/30 dark:text-brand-300">
        <Icone aria-hidden className="h-4 w-4" />
      </span>
      <div className="min-w-0">
        <p className="text-xs text-texto-3">{rotulo}</p>
        <div className="mt-0.5 text-sm font-medium break-words text-texto">{children ?? '—'}</div>
      </div>
    </div>
  );
}

function Lista({ itens, vazio }: { itens?: string[]; vazio: string }) {
  if (!itens?.length) return <p className="text-sm text-texto-3">{vazio}</p>;
  return (
    <ul className="flex flex-wrap gap-1.5">
      {itens.map((item) => (
        <li key={item}>
          <Badge variant="gray" className="font-mono">
            {item}
          </Badge>
        </li>
      ))}
    </ul>
  );
}

/** Os dois blocos, técnico e comportamental, lado a lado no desktop. */
export function InteracaoVisitante({
  contexto,
  comportamento,
}: {
  contexto?: ContextoTecnico | null;
  comportamento?: Comportamento | null;
}) {
  const local = formatarLocal(contexto?.localizacao);
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section aria-labelledby="interacao-tecnico">
        <h3 id="interacao-tecnico" className="mb-3 text-sm font-semibold text-titulo">
          Contexto técnico do aparelho
        </h3>
        <div className="grid gap-2 sm:grid-cols-2">
          <Metrica icone={ICONE_DISPOSITIVO[contexto?.tipoDispositivo ?? ''] ?? Monitor} rotulo="Dispositivo">
            {contexto?.tipoDispositivo ? NOME_DISPOSITIVO[contexto.tipoDispositivo] ?? contexto.tipoDispositivo : null}
          </Metrica>
          <Metrica icone={Globe} rotulo="Sistema e navegador">
            {[contexto?.sistema, contexto?.navegador].filter(Boolean).join(' · ') || null}
          </Metrica>
          <Metrica icone={MapPin} rotulo="Localização aproximada">
            {local && (
              <>
                {local}
                {contexto?.localizacao?.simulada && (
                  <Badge variant="yellow" className="ml-1.5">
                    simulada
                  </Badge>
                )}
              </>
            )}
          </Metrica>
          <Metrica icone={Monitor} rotulo="Idioma, fuso e tela">
            {[contexto?.idioma, contexto?.fuso, contexto?.tela].filter(Boolean).join(' · ') || null}
          </Metrica>
        </div>
      </section>

      <section aria-labelledby="interacao-comportamento">
        <h3 id="interacao-comportamento" className="mb-3 text-sm font-semibold text-titulo">
          Comportamento na landing page
        </h3>
        <div className="grid gap-2 sm:grid-cols-2">
          <Metrica icone={Clock} rotulo="Tempo com a página aberta">
            {formatarDuracao(comportamento?.tempoNaPaginaSegundos)}
          </Metrica>
          <Metrica icone={ArrowDownToLine} rotulo="Rolagem máxima">
            {comportamento?.profundidadeRolagem !== undefined ? (
              <span className="flex items-center gap-2">
                <span className="h-1.5 w-20 overflow-hidden rounded-full bg-borda">
                  <span
                    className="block h-full rounded-full bg-brand-600"
                    style={{ width: `${comportamento.profundidadeRolagem}%` }}
                  />
                </span>
                {comportamento.profundidadeRolagem}%
              </span>
            ) : null}
          </Metrica>
        </div>
        <div className="mt-3 space-y-3">
          <div>
            <p className="mb-1.5 flex items-center gap-1.5 text-xs text-texto-3">
              <MousePointerClick aria-hidden className="h-3.5 w-3.5" /> Botões e links clicados
            </p>
            <Lista itens={comportamento?.elementosClicados} vazio="Nenhum clique registrado." />
          </div>
          <div>
            <p className="mb-1.5 flex items-center gap-1.5 text-xs text-texto-3">
              <Route aria-hidden className="h-3.5 w-3.5" /> Páginas e seções vistas
            </p>
            <Lista itens={comportamento?.paginasVisitadas} vazio="Nenhuma página registrada." />
          </div>
        </div>
      </section>
    </div>
  );
}
