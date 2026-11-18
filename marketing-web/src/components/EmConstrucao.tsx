import { Construction } from 'lucide-react';

/** Estado vazio das áreas que ainda não têm funcionalidade. */
export function EmConstrucao({ descricao }: { descricao: string }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-borda-forte bg-superficie p-10 text-center">
      <Construction className="h-8 w-8 text-texto-3" />
      <p className="max-w-md text-sm text-texto-2">{descricao}</p>
    </div>
  );
}
