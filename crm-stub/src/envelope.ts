/** Envelope padrão das respostas HTTP da plataforma (Contrato §8.2). */
export interface ErroCampo {
  campo: string;
  codigo: string;
  detalhe: string;
}

export interface Resposta<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  errors: ErroCampo[];
}

export function ok<T>(data: T): Resposta<T> {
  return { success: true, data, message: null, errors: [] };
}

export function erro<T = null>(message: string, errors: ErroCampo[] = [], data: T | null = null): Resposta<T> {
  return { success: false, data, message, errors };
}
