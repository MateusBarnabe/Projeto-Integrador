import type { ReactNode } from 'react';
import { numero } from '../formatos';

export interface ItemDeBarra {
  chave: string;
  rotulo: ReactNode;
  valor: number;
  /** Classe de cor de fundo da barra (token do Design System). Padrão: verde da marca. */
  cor?: string;
  /** Texto extra à direita do número (ex.: "15%"). */
  complemento?: ReactNode;
}

/**
 * Barras horizontais com o rótulo e o número escritos ao lado (leitura direta, sem legenda). A largura
 * é proporcional ao maior valor, ou ao `maximo` quando ele é dado.
 */
export function BarrasHorizontais({ itens, maximo }: { itens: ItemDeBarra[]; maximo?: number }) {
  const teto = Math.max(maximo ?? 0, ...itens.map((item) => item.valor), 1);
  return (
    <ul className="flex flex-col gap-3">
      {itens.map((item) => (
        <li key={item.chave}>
          <div className="mb-1 flex items-baseline justify-between gap-3 text-sm">
            <span className="min-w-0 truncate text-texto-2">{item.rotulo}</span>
            <span className="shrink-0 tabular-nums font-semibold text-titulo">
              {numero(item.valor)}
              {item.complemento && <span className="ml-1.5 text-xs font-normal text-texto-3">{item.complemento}</span>}
            </span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-superficie-2" aria-hidden>
            <div
              className={`h-full rounded-full ${item.cor ?? 'bg-brand-700'}`}
              style={{ width: `${(item.valor / teto) * 100}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  );
}
