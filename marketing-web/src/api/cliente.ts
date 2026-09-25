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

/**
 * Chama a API do módulo com o token da plataforma e devolve o `data` do envelope.
 * Erros viram ErroApi, com a mensagem do envelope para a tela mostrar.
 */
export async function requisitar<T>(
  caminho: string,
  opcoes: RequestInit = {},
  { autenticar = true }: { autenticar?: boolean } = {},
): Promise<T> {
  const token = autenticar ? (plataforma.obterToken() ?? (await plataforma.aguardarToken())) : null;
  const cabecalhos = new Headers(opcoes.headers);
  cabecalhos.set('Accept', 'application/json');
  if (opcoes.body && !cabecalhos.has('Content-Type')) cabecalhos.set('Content-Type', 'application/json');
  if (token) cabecalhos.set('Authorization', `Bearer ${token}`);

  const resposta = await fetch(`${BASE}${caminho}`, { ...opcoes, headers: cabecalhos });

  if (resposta.status === 401) plataforma.informarTokenExpirado();

  const corpo = (await resposta.json().catch(() => null)) as Resposta<T> | null;
  if (!resposta.ok || !corpo?.success) {
    throw new ErroApi(resposta.status, corpo?.message ?? 'Não foi possível falar com o servidor.', corpo?.errors);
  }
  return corpo.data;
}
