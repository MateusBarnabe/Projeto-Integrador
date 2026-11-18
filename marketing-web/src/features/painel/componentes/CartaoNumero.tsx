import type { ComponentType, ReactNode } from 'react';

/** Número grande com rótulo e uma dica opcional (cartões do topo do Painel e do lead 0). */
export function CartaoNumero({
  rotulo,
  valor,
  dica,
  icone: Icone,
  destaque = false,
}: {
  rotulo: string;
  valor: ReactNode;
  dica?: ReactNode;
  icone?: ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;
  /** Cartão de ênfase: o número em verde da marca. */
  destaque?: boolean;
}) {
  return (
    <div className="rounded-xl border border-borda bg-superficie p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-texto-3">{rotulo}</p>
        {Icone && <Icone aria-hidden className="h-4 w-4 shrink-0 text-texto-3" />}
      </div>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${destaque ? 'text-brand-700' : 'text-titulo'}`}>{valor}</p>
      {dica && <p className="mt-0.5 text-xs text-texto-3">{dica}</p>}
    </div>
  );
}
