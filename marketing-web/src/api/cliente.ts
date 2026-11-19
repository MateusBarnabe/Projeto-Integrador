import { plataforma } from '@/plataforma';

/** Envelope de toda resposta da API (Contrato §8.2). */
export interface Resposta<T> {
  success: boolean;
  data: T;
  message: string | null;
  errors: { campo: string; codigo: string; detalhe: string }[];
}

export class ErroApi extends Error {
  readonly status: number;
  readonly erros: Resposta<unknown>['errors'];

  constructor(status: number, mensagem: string, erros: Resposta<unknown>['errors'] = []) {
    super(mensagem);
    this.status = status;
    this.erros = erros;
  }
}

const BASE = import.meta.env.VITE_API_URL || '/api/marketing';

/** Bases das APIs dos outros módulos, a mesma origem do gateway da plataforma (Contrato §9.2). */
const BASES_DE_MODULOS = {
  crm: import.meta.env.VITE_CRM_URL || '/api/crm',
} as const;

export type ModuloExterno = keyof typeof BASES_DE_MODULOS;

async function chamar<T>(
  base: string,
  caminho: string,
  opcoes: RequestInit,
  autenticar: boolean,
): Promise<T> {
  const token = autenticar ? (plataforma.obterToken() ?? (await plataforma.aguardarToken())) : null;
  const cabecalhos = new Headers(opcoes.headers);
  cabecalhos.set('Accept', 'application/json');
  if (opcoes.body && !cabecalhos.has('Content-Type')) cabecalhos.set('Content-Type', 'application/json');
  if (token) cabecalhos.set('Authorization', `Bearer ${token}`);

  const resposta = await fetch(`${base}${caminho}`, { ...opcoes, headers: cabecalhos });

  if (resposta.status === 401) plataforma.informarTokenExpirado();

  const corpo = (await resposta.json().catch(() => null)) as Resposta<T> | null;
  if (!resposta.ok || !corpo?.success) {
    throw new ErroApi(resposta.status, corpo?.message ?? 'Não foi possível falar com o servidor.', corpo?.errors);
  }
  return corpo.data;
}

/**
 * Chama a API do módulo com o token da plataforma e devolve o `data` do envelope.
 * Erros viram ErroApi, com a mensagem do envelope para a tela mostrar.
 */
export function requisitar<T>(
  caminho: string,
  opcoes: RequestInit = {},
  { autenticar = true }: { autenticar?: boolean } = {},
): Promise<T> {
  return chamar<T>(BASE, caminho, opcoes, autenticar);
}

/**
 * Chama a API de outro módulo com o token do usuário (Contrato §9.2: com usuário na tela, repassa-se
 * o token recebido). O mesmo envelope e o mesmo tratamento de `401` do {@link requisitar}. A tela decide
 * o que fazer quando o outro módulo falha; ele nunca derruba o resto.
 */
export function requisitarModulo<T>(
  modulo: ModuloExterno,
  caminho: string,
  opcoes: RequestInit = {},
): Promise<T> {
  return chamar<T>(BASES_DE_MODULOS[modulo], caminho, opcoes, true);
}
