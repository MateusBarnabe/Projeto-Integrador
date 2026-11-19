import type { ReactNode } from 'react';

/** Cartão de um bloco do Painel: título, nota opcional à direita e conteúdo. */
export function Bloco({
  titulo,
  descricao,
  nota,
  className = '',
  children,
}: {
  titulo: string;
  descricao?: ReactNode;
  nota?: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  return (
    <section className={`flex flex-col rounded-xl border border-borda bg-superficie ${className}`}>
      <header className="flex flex-wrap items-start justify-between gap-2 border-b border-borda px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold text-titulo">{titulo}</h2>
          {descricao && <p className="mt-0.5 text-xs text-texto-3">{descricao}</p>}
        </div>
        {nota}
      </header>
      <div className="flex-1 p-4">{children}</div>
    </section>
  );
}

/** Texto de bloco sem dados no período. */
export function SemDados({ children = 'Sem dados no período.' }: { children?: ReactNode }) {
  return <p className="py-6 text-center text-sm text-texto-3">{children}</p>;
}
