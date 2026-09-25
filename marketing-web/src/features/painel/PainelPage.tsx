import { EmConstrucao } from '@/components/EmConstrucao';
import { StatusApi } from '@/components/StatusApi';

export function PainelPage() {
  return (
    <div className="flex flex-col gap-4 p-5">
      <StatusApi />
      <EmConstrucao descricao="Funil por etapa, qualificação, campanhas, custo por lead e distribuição entre vendedores, com atualização em tempo real (norteador 9.1)." />
    </div>
  );
}
