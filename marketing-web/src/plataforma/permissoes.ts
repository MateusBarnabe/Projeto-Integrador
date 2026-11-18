/*
 * Leitura das permissões do token, só para o front esconder o que o usuário não pode usar.
 * A assinatura NÃO é validada aqui: quem decide é o back (403). Nunca use isto como segurança.
 */

let ultimoToken: string | null = null;
let ultimasPermissoes: ReadonlySet<string> = new Set();

function decodificarPayload(token: string): unknown {
  const partes = token.split('.');
  if (partes.length < 2) return null;
  try {
    const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
    const binario = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, '='));
    const bytes = Uint8Array.from(binario, (letra) => letra.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

/** Claim `perms` do token (Contrato §4.2). Token ausente ou ilegível: conjunto vazio. */
export function permissoesDoToken(token: string | null): ReadonlySet<string> {
  if (!token) return new Set();
  // Um token de 15 KB seria decodificado a cada render sem este cache
  if (token === ultimoToken) return ultimasPermissoes;
  const payload = decodificarPayload(token) as { perms?: unknown } | null;
  const perms = Array.isArray(payload?.perms) ? payload.perms.filter((p): p is string => typeof p === 'string') : [];
  ultimoToken = token;
  ultimasPermissoes = new Set(perms);
  return ultimasPermissoes;
}

export function tokenTemPermissao(token: string | null, codigo: string): boolean {
  return permissoesDoToken(token).has(codigo);
}
