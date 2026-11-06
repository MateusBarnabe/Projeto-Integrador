/* Validação das respostas e normalização da origem, com as regras do formulário do Landing. */
import type { Campo, Formulario } from './formulario.ts';
import type { Resposta } from './envelope.ts';

export type Erro = Resposta<unknown>['errors'][number];
export type Valor = string | number | boolean | string[] | null;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function vazio(valor: unknown): boolean {
  return valor === undefined || valor === null || valor === '' || (Array.isArray(valor) && valor.length === 0);
}

/** 14 dígitos com os dois dígitos verificadores certos (ex. de teste: 11222333000181). */
function cnpjValido(digitos: string): boolean {
  if (!/^\d{14}$/.test(digitos) || /^(\d)\1{13}$/.test(digitos)) return false;
  const verificador = (base: string) => {
    const soma = [...base].reduce((total, d, i) => total + Number(d) * (((base.length - i - 1) % 8) + 2), 0);
    const resto = soma % 11;
    return resto < 2 ? 0 : 11 - resto;
  };
  const primeiro = verificador(digitos.slice(0, 12));
  const segundo = verificador(digitos.slice(0, 12) + primeiro);
  return digitos.endsWith(`${primeiro}${segundo}`);
}

/** Valida um valor para o tipo do campo. Devolve o valor normalizado ou o erro. */
export function validarValor(campo: Campo, valor: unknown): { valor: Valor } | { erro: Erro } {
  const falha = (codigo: string, detalhe: string) => ({ erro: { campo: campo.id, codigo, detalhe } });

  if (vazio(valor)) {
    return campo.obrigatorio ? falha('CAMPO_OBRIGATORIO', `Preencha "${campo.rotulo}".`) : { valor: null };
  }

  switch (campo.tipo) {
    case 'EMAIL': {
      const email = String(valor).trim().toLowerCase();
      return EMAIL.test(email) ? { valor: email } : falha('EMAIL_INVALIDO', 'Informe um e-mail válido.');
    }
    case 'TELEFONE': {
      // Só dígitos, com DDD (formato do contrato: 62999998888)
      const digitos = String(valor).replace(/\D/g, '').replace(/^55(?=\d{10,11}$)/, '');
      return /^\d{10,11}$/.test(digitos) ? { valor: digitos } : falha('TELEFONE_INVALIDO', 'Informe o telefone com DDD.');
    }
    case 'CNPJ': {
      // Só dígitos, como o CRM espera (crm.yaml: ^[0-9]{14}$)
      const digitos = String(valor).replace(/\D/g, '');
      return cnpjValido(digitos) ? { valor: digitos } : falha('CNPJ_INVALIDO', 'Informe um CNPJ válido.');
    }
    case 'LISTA_OPCOES':
      return campo.opcoes?.includes(String(valor))
        ? { valor: String(valor) }
        : falha('OPCAO_INVALIDA', 'Escolha uma das opções.');
    case 'MULTIPLA_ESCOLHA': {
      const lista = Array.isArray(valor) ? valor.map(String) : [String(valor)];
      return lista.every((item) => campo.opcoes?.includes(item))
        ? { valor: lista }
        : falha('OPCAO_INVALIDA', 'Escolha entre as opções.');
    }
    case 'NUMERO':
      return Number.isFinite(Number(valor)) ? { valor: Number(valor) } : falha('NUMERO_INVALIDO', 'Informe um número.');
    case 'CAIXA_MARCACAO':
      return { valor: valor === true || valor === 'true' };
    default:
      return { valor: String(valor).trim().slice(0, 5000) };
  }
}

/** Valida o mapa `campoId → valor` do envio. Campos que o formulário não tem são ignorados. */
export function validarRespostas(formulario: Formulario, respostas: unknown) {
  const entrada = (respostas && typeof respostas === 'object' ? respostas : {}) as Record<string, unknown>;
  const valores: Record<string, Valor> = {};
  const erros: Erro[] = [];

  for (const campo of formulario.campos) {
    const resultado = validarValor(campo, entrada[campo.id]);
    if ('erro' in resultado) erros.push(resultado.erro);
    else valores[campo.id] = resultado.valor;
  }
  return { valores, erros };
}

/** Schema `ToqueOrigem` do landing.yaml. */
export interface ToqueOrigem {
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  utmTerm: string | null;
  utmContent: string | null;
  referenciador: string | null;
  paginaEntrada: string | null;
  ocorridoEm: string;
}

export interface Origem {
  primeira: ToqueOrigem;
  ultima: ToqueOrigem;
}

function texto(valor: unknown, limite = 300): string | null {
  return typeof valor === 'string' && valor.trim() !== '' ? valor.trim().slice(0, limite) : null;
}

function toque(entrada: unknown, agora: string): ToqueOrigem {
  const t = (entrada && typeof entrada === 'object' ? entrada : {}) as Record<string, unknown>;
  const ocorridoEm = typeof t.ocorridoEm === 'string' && !Number.isNaN(Date.parse(t.ocorridoEm))
    ? new Date(t.ocorridoEm).toISOString()
    : agora;
  return {
    utmSource: texto(t.utmSource),
    utmMedium: texto(t.utmMedium),
    utmCampaign: texto(t.utmCampaign),
    utmTerm: texto(t.utmTerm),
    utmContent: texto(t.utmContent),
    referenciador: texto(t.referenciador, 2048),
    // a URL de entrada inteira, com fbclid e gclid quando vieram no anúncio
    paginaEntrada: texto(t.paginaEntrada, 2048),
    ocorridoEm,
  };
}

/**
 * Guarda só os campos do contrato. O `fbclid` e o `gclid` que o navegador guardou não têm campo
 * próprio no `ToqueOrigem` do Landing: chegam dentro da `paginaEntrada`.
 */
export function normalizarOrigem(entrada: unknown): Origem {
  const agora = new Date().toISOString();
  const o = (entrada && typeof entrada === 'object' ? entrada : {}) as Record<string, unknown>;
  const primeira = toque(o.primeira ?? o.ultima, agora);
  const ultima = toque(o.ultima ?? o.primeira, agora);
  return { primeira, ultima };
}
