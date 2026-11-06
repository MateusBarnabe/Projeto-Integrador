/** Leitura e validação do corpo, acumulando os erros no formato {campo, codigo, detalhe} (§8.2). */
import type { ErroCampo } from './envelope.ts';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DATA = /^\d{4}-\d{2}-\d{2}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function ehUuid(valor: unknown): valor is string {
  return typeof valor === 'string' && UUID.test(valor);
}

export class Leitor {
  readonly erros: ErroCampo[] = [];
  private readonly corpo: Record<string, unknown>;

  constructor(corpo: unknown) {
    this.corpo = corpo && typeof corpo === 'object' && !Array.isArray(corpo) ? (corpo as Record<string, unknown>) : {};
  }

  erro(campo: string, codigo: string, detalhe: string): void {
    this.erros.push({ campo, codigo, detalhe });
  }

  private valor(campo: string): unknown {
    const v = this.corpo[campo];
    return v === undefined ? null : v;
  }

  texto(campo: string, opcoes: { obrigatorio?: boolean; max?: number; padrao?: RegExp } = {}): string | null {
    const v = this.valor(campo);
    if (v === null || (typeof v === 'string' && v.trim() === '')) {
      if (opcoes.obrigatorio) this.erro(campo, 'CAMPO_OBRIGATORIO', `Informe ${campo}.`);
      return null;
    }
    if (typeof v !== 'string') {
      this.erro(campo, 'FORMATO_INVALIDO', `${campo} precisa ser texto.`);
      return null;
    }
    if (opcoes.max !== undefined && v.length > opcoes.max) {
      this.erro(campo, 'TAMANHO_EXCEDIDO', `${campo} aceita até ${opcoes.max} caracteres.`);
    }
    if (opcoes.padrao && !opcoes.padrao.test(v)) {
      this.erro(campo, 'FORMATO_INVALIDO', `${campo} fora do formato esperado.`);
    }
    return v;
  }

  uuid(campo: string, obrigatorio = false): string | null {
    const v = this.texto(campo, { obrigatorio });
    if (v !== null && !UUID.test(v)) {
      this.erro(campo, 'FORMATO_INVALIDO', `${campo} precisa ser um UUID.`);
      return null;
    }
    return v;
  }

  email(campo: string): string | null {
    return this.texto(campo, { padrao: EMAIL });
  }

  data(campo: string): string | null {
    return this.texto(campo, { padrao: DATA });
  }

  opcao<T extends string>(campo: string, opcoes: readonly T[]): T | null {
    const v = this.texto(campo);
    if (v !== null && !opcoes.includes(v as T)) {
      this.erro(campo, 'VALOR_INVALIDO', `${campo} aceita: ${opcoes.join(', ')}.`);
      return null;
    }
    return v as T | null;
  }

  /** Dinheiro em reais, até 2 casas (§8.7); mais casas responde PRECISAO_EXCEDIDA. */
  dinheiro(campo: string): number | null {
    const v = this.valor(campo);
    if (v === null) return null;
    if (typeof v !== 'number' || !Number.isFinite(v) || v < 0) {
      this.erro(campo, 'VALOR_INVALIDO', `${campo} precisa ser um número maior ou igual a zero.`);
      return null;
    }
    if (Math.abs(v * 100 - Math.round(v * 100)) > 1e-6) {
      this.erro(campo, 'PRECISAO_EXCEDIDA', `${campo} aceita até 2 casas decimais.`);
      return null;
    }
    return v;
  }

  inteiro(campo: string, min: number, max: number): number | null {
    const v = this.valor(campo);
    if (v === null) return null;
    if (typeof v !== 'number' || !Number.isInteger(v) || v < min || v > max) {
      this.erro(campo, 'VALOR_INVALIDO', `${campo} precisa ser um inteiro de ${min} a ${max}.`);
      return null;
    }
    return v;
  }
}
