/*
 * Simula a tag do script de rastreamento do Marketing que pedimos ao Landing (R1,
 * Docs/contratos/landing-requisitos.md): no Landing real, ela é uma linha fixa no HTML da landing page,
 *
 *   <script src="https://<plataforma>/public/marketing/rastreamento.js" async></script>
 *
 * O script só age quando a URL tem o mkt_visita, que o link de campanha do Marketing acrescenta.
 * Endereço do Marketing: VITE_MARKETING_URL (padrão http://localhost:8087, a API local).
 */
export function carregarRastreamentoDoMarketing() {
  const base = (import.meta.env.VITE_MARKETING_URL as string | undefined) || 'http://localhost:8087';
  const script = document.createElement('script');
  script.src = `${base.replace(/\/$/, '')}/public/marketing/rastreamento.js`;
  script.async = true;
  document.head.appendChild(script);
}

/** Id da visita do anúncio na URL de chegada, para o painel de teste mostrar. */
export function visitaDoAnuncio(paginaEntrada: string | null) {
  if (!paginaEntrada) return null;
  try {
    return new URL(paginaEntrada).searchParams.get('mkt_visita');
  } catch {
    return null;
  }
}
