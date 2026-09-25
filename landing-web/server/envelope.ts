/** Envelope padrão das respostas HTTP da plataforma (Contrato §8.2). */
export interface Resposta<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  errors: { campo: string; codigo: string; detalhe: string }[];
}

export function ok<T>(data: T): Resposta<T> {
  return { success: true, data, message: null, errors: [] };
}

export function erro(message: string, errors: Resposta<unknown>['errors'] = []): Resposta<null> {
  return { success: false, data: null, message, errors };
}
