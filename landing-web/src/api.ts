/* Chamadas da página à superfície pública do placeholder (mesmas rotas do landing.yaml). */

export type TipoCampo =
  | 'TEXTO'
  | 'TEXTO_LONGO'
  | 'EMAIL'
  | 'TELEFONE'
  | 'CNPJ'
  | 'CPF'
  | 'NUMERO'
  | 'DATA'
  | 'LISTA_OPCOES'
  | 'MULTIPLA_ESCOLHA'
  | 'CAIXA_MARCACAO';

export interface CampoPublico {
  id: string;
  rotulo: string;
  tipo: TipoCampo;
  obrigatorio: boolean;
  placeholder: string | null;
  opcoes: string[] | null;
  ordem: number;
  textoPergunta: string | null;
}

export interface FormularioPublico {
  chavePublica: string;
  nome: string;
  modoExibicao: string;
  textoConsentimento: string;
  campos: CampoPublico[];
}

export interface Resposta<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  errors: { campo: string | null; codigo: string; detalhe: string }[];
}

/** Chave pública do formulário de exemplo (definido em server/formulario.ts). */
export const CHAVE_FORMULARIO = 'fk_empresaA01';
/** Slug da landing page de teste: o envio daqui é do canal `landing_page`. */
export const SLUG_PAGINA = 'empresa-a-consultoria';

export async function chamar<T>(metodo: 'GET' | 'POST', caminho: string, corpo?: unknown): Promise<Resposta<T>> {
  try {
    const resposta = await fetch(caminho, {
      method: metodo,
      headers: corpo === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
    });
    return (await resposta.json()) as Resposta<T>;
  } catch {
    return { success: false, data: null, message: 'O servidor do placeholder não respondeu.', errors: [] };
  }
}
