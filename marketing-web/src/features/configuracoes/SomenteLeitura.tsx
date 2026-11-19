import { Lock } from 'lucide-react';

/**
 * Aviso das abas de Configurações para quem só pode ver (sem a permissão de edição). Esconder ou
 * desabilitar os controles é só usabilidade: o back segue respondendo 403 (ver `usarPermissao`).
 */
export function SomenteLeitura({ className = '' }: { className?: string }) {
  return (
    <p className={`flex items-center gap-1.5 text-xs text-texto-3 ${className}`}>
      <Lock aria-hidden className="h-3 w-3 shrink-0" />
      Somente leitura: o seu perfil não tem permissão para alterar esta configuração.
    </p>
  );
}
