import { useEffect, useState } from 'react';

/** Faixa no topo lembrando que esta página é o placeholder do Landing, com o estado do servidor. */
export function AvisoPlaceholder() {
  const [servidorNoAr, setServidorNoAr] = useState<boolean | null>(null);

  useEffect(() => {
    fetch('/api/landing/health')
      .then((resposta) => setServidorNoAr(resposta.ok))
      .catch(() => setServidorNoAr(false));
  }, []);

  const estado = servidorNoAr === null ? 'verificando' : servidorNoAr ? 'no ar' : 'fora do ar';

  return (
    <div className="bg-yellow-50 px-4 py-2 text-center text-xs text-yellow-800">
      Placeholder do Landing (Grupo 7) para testes locais do Grupo 4 · servidor: {estado}
    </div>
  );
}
