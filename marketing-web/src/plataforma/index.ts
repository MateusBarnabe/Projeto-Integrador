import { criarPlataformaEmbutida } from './embutida';
import { criarPlataformaSozinha } from './sozinha';
import type { Plataforma } from './tipos';

export type { ModoPlataforma, Plataforma } from './tipos';

const estaEmbutido = window.self !== window.top;

/** Instância única, fora dos componentes (Contrato §12.2). */
export const plataforma: Plataforma = estaEmbutido ? criarPlataformaEmbutida() : criarPlataformaSozinha();
