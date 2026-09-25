import type { ButtonHTMLAttributes, ReactNode } from 'react';

/*
 * Componentes base, com os mesmos nomes e a mesma API do Design System Centinela
 * (Docs/DesignSystem/design-systemfinal.md, seção 3). Antes de criar um componente novo,
 * confira se ele já existe na lista do Design System e mantenha o mesmo nome.
 */

type VarianteBotao = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';

const estiloBotao: Record<VarianteBotao, string> = {
  primary: 'bg-brand-800 text-white hover:bg-brand-700',
  secondary: 'bg-gray-100 text-gray-800 hover:bg-gray-200',
  outline: 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50',
  ghost: 'text-gray-600 hover:bg-gray-100',
  danger: 'bg-red-600 text-white hover:bg-red-700',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: VarianteBotao;
  size?: 'sm' | 'md';
}

export function Button({ variant = 'primary', size = 'md', className = '', ...props }: ButtonProps) {
  const tamanho = size === 'sm' ? 'px-3 py-1.5 text-xs' : 'px-4 py-2 text-sm';
  return (
    <button
      className={`inline-flex items-center gap-1.5 rounded-lg font-medium transition-colors disabled:opacity-50 ${tamanho} ${estiloBotao[variant]} ${className}`}
      {...props}
    />
  );
}

type VarianteBadge = 'green' | 'red' | 'blue' | 'indigo' | 'yellow' | 'gray' | 'purple';

const estiloBadge: Record<VarianteBadge, string> = {
  green: 'bg-brand-100 text-brand-800',
  red: 'bg-red-50 text-red-700',
  blue: 'bg-blue-50 text-blue-700',
  indigo: 'bg-indigo-50 text-indigo-700',
  yellow: 'bg-yellow-50 text-yellow-800',
  gray: 'bg-gray-100 text-gray-600',
  purple: 'bg-purple-50 text-purple-700',
};

export function Badge({ variant = 'gray', children }: { variant?: VarianteBadge; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${estiloBadge[variant]}`}>
      {children}
    </span>
  );
}

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

/** Cabeçalho obrigatório de toda página (Design System, 12.3). */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="flex shrink-0 items-center justify-between bg-brand-950 px-5 py-3.5 text-white">
      <div>
        <h1 className="text-lg font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-0.5 text-xs text-brand-300">{subtitle}</p>}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  );
}
