import { useEffect, useState } from 'react';
import { Activity } from 'lucide-react';
import { requisitar } from '@/api/cliente';
import { Badge } from '@/components/ui';

type Estado = 'carregando' | 'no-ar' | 'fora-do-ar';

/** Mostra se a API do módulo está de pé e conectada ao banco (GET /api/marketing/health). */
export function StatusApi() {
  const [estado, setEstado] = useState<Estado>('carregando');

  useEffect(() => {
    requisitar<{ status: string }>('/health', {}, { autenticar: false })
      .then(() => setEstado('no-ar'))
      .catch(() => setEstado('fora-do-ar'));
  }, []);

  return (
    <div className="flex items-center gap-2 text-sm text-gray-600">
      <Activity className="h-4 w-4 shrink-0" />
      API do módulo:
      {estado === 'carregando' && <Badge>verificando</Badge>}
      {estado === 'no-ar' && <Badge variant="green">no ar</Badge>}
      {estado === 'fora-do-ar' && <Badge variant="red">fora do ar</Badge>}
    </div>
  );
}
