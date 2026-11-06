/*
 * Primeira e última origem do visitante, guardadas no navegador como faz o form.js do Landing.
 * A primeira nunca muda; a última só é trocada quando a visita traz campanha (UTM, fbclid,
 * gclid) ou vem de outro site, para uma volta direta não apagar o anúncio que trouxe a pessoa.
 */

export interface ToqueOrigem {
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  utmTerm: string | null;
  utmContent: string | null;
  /** Clique do Meta Ads; não tem campo no contrato, vai também dentro da `paginaEntrada` */
  fbclid: string | null;
  /** Clique do Google Ads; idem */
  gclid: string | null;
  referenciador: string | null;
  paginaEntrada: string | null;
  ocorridoEm: string;
}

export interface Origem {
  primeira: ToqueOrigem;
  ultima: ToqueOrigem;
}

const CHAVE = 'landing-placeholder:origem';

function lerGuardada(): Origem | null {
  try {
    const bruto = localStorage.getItem(CHAVE);
    return bruto ? (JSON.parse(bruto) as Origem) : null;
  } catch {
    return null;
  }
}

function guardar(origem: Origem) {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(origem));
  } catch {
    // sem armazenamento (aba anônima, bloqueio): a origem vale só para esta visita
  }
}

function toqueAtual(): { toque: ToqueOrigem; temCampanha: boolean } {
  const url = new URL(window.location.href);
  const p = (nome: string) => url.searchParams.get(nome) || null;
  const referenciador = document.referrer || null;
  const deOutroSite = referenciador !== null && new URL(referenciador).host !== url.host;

  const toque: ToqueOrigem = {
    utmSource: p('utm_source'),
    utmMedium: p('utm_medium'),
    utmCampaign: p('utm_campaign'),
    utmTerm: p('utm_term'),
    utmContent: p('utm_content'),
    fbclid: p('fbclid'),
    gclid: p('gclid'),
    referenciador,
    paginaEntrada: url.href,
    ocorridoEm: new Date().toISOString(),
  };
  const temCampanha = [toque.utmSource, toque.utmMedium, toque.utmCampaign, toque.fbclid, toque.gclid]
    .some((valor) => valor !== null);
  return { toque, temCampanha: temCampanha || deOutroSite };
}

/** Registra a visita atual e devolve a origem a mandar no envio. Chamar uma vez por carga. */
export function registrarVisita(): Origem {
  const { toque, temCampanha } = toqueAtual();
  const guardada = lerGuardada();
  const origem: Origem = guardada
    ? { primeira: guardada.primeira, ultima: temCampanha ? toque : guardada.ultima }
    : { primeira: toque, ultima: toque };
  guardar(origem);
  return origem;
}

/** Apaga a origem guardada, para testar outra jornada do zero. */
export function esquecerOrigem() {
  try {
    localStorage.removeItem(CHAVE);
  } catch {
    // nada guardado
  }
}
