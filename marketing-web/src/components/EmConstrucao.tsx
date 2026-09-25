import { Construction } from 'lucide-react';

/** Estado vazio das áreas que ainda não têm funcionalidade. */
export function EmConstrucao({ descricao }: { descricao: string }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 bg-white p-10 text-center">
      <Construction className="h-8 w-8 text-gray-400" />
      <p className="max-w-md text-sm text-gray-500">{descricao}</p>
    </div>
  );
}
