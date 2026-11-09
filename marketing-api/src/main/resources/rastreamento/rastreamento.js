/*
 * Script de rastreamento do Marketing (R1), servido em /public/marketing/rastreamento.js e carregado pela
 * landing page:
 *
 *   <script src="https://<plataforma>/public/marketing/rastreamento.js" async></script>
 *
 * Só age quando a visita veio de um link de campanha do Marketing: o clique no anúncio acrescenta
 * mkt_visita à URL. Sem ele, não faz nada. Manda, por HTTP, os totais da visita: tempo com a página
 * visível, maior rolagem, botões e links clicados e páginas e seções vistas, mais idioma, fuso e tela.
 *
 * Não lê campos de formulário, não usa cookie e não guarda nada além da sessão da aba
 * (sessionStorage). Os sinais saem por navigator.sendBeacon em text/plain, que não exige CORS.
 * Marque elementos com data-rastreio="nome" (cliques) e seções com data-rastreio-secao="nome".
 */
(function () {
  'use strict';

  var PARAMETRO = 'mkt_visita';
  var CHAVE = 'marketing-rastreamento';
  var INTERVALO_MS = 15000;
  var MAXIMO_ITENS = 30;
  var UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  var script = document.currentScript;
  var base = script && script.src ? new URL(script.src).origin : '';

  function ler() {
    try {
      return JSON.parse(sessionStorage.getItem(CHAVE)) || null;
    } catch (e) {
      return null;
    }
  }

  function guardar(estado) {
    try {
      sessionStorage.setItem(CHAVE, JSON.stringify(estado));
    } catch (e) {
      // sem armazenamento: os totais valem só para esta página
    }
  }

  var daUrl = new URLSearchParams(window.location.search).get(PARAMETRO);
  var estado = ler();
  if (daUrl && UUID.test(daUrl) && (!estado || estado.visita !== daUrl)) {
    estado = { visita: daUrl, tempo: 0, rolagem: 0, cliques: [], paginas: [] };
  }
  if (!estado || !estado.visita) {
    return; // não veio de um anúncio rastreado
  }

  function acrescentar(lista, valor) {
    if (valor && lista.indexOf(valor) < 0 && lista.length < MAXIMO_ITENS) {
      lista.push(valor);
    }
  }

  // --- tempo com a página visível -----------------------------------------------------------------
  var visivelDesde = document.visibilityState === 'visible' ? Date.now() : null;

  function acumularTempo() {
    if (visivelDesde !== null) {
      estado.tempo += (Date.now() - visivelDesde) / 1000;
      visivelDesde = document.visibilityState === 'visible' ? Date.now() : null;
    }
  }

  // --- rolagem --------------------------------------------------------------------------------------
  function medirRolagem() {
    var altura = Math.max(document.documentElement.scrollHeight, document.body ? document.body.scrollHeight : 0);
    var vista = window.scrollY + window.innerHeight;
    var percentual = altura > 0 ? Math.min(100, Math.round((vista / altura) * 100)) : 100;
    if (percentual > estado.rolagem) {
      estado.rolagem = percentual;
    }
  }

  var rolagemAgendada = false;
  window.addEventListener('scroll', function () {
    if (!rolagemAgendada) {
      rolagemAgendada = true;
      window.requestAnimationFrame(function () {
        rolagemAgendada = false;
        medirRolagem();
      });
    }
  }, { passive: true });

  // --- cliques em botões e links (nunca o que foi digitado) -------------------------------------------
  document.addEventListener('click', function (evento) {
    var alvo = evento.target && evento.target.closest
      ? evento.target.closest('[data-rastreio], a, button')
      : null;
    if (!alvo) {
      return;
    }
    var nome = alvo.getAttribute('data-rastreio') || alvo.id
      || alvo.tagName.toLowerCase() + ':' + (alvo.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 40);
    acrescentar(estado.cliques, nome);
    guardar(estado);
  }, true);

  // --- páginas e seções vistas (caminho e âncora, sem a consulta) -------------------------------------
  function registrarPagina() {
    acrescentar(estado.paginas, window.location.pathname + window.location.hash);
  }

  function observarSecoes() {
    if (!('IntersectionObserver' in window)) {
      return;
    }
    var observador = new IntersectionObserver(function (entradas) {
      entradas.forEach(function (entrada) {
        if (entrada.isIntersecting) {
          acrescentar(estado.paginas, window.location.pathname + '#' + entrada.target.getAttribute('data-rastreio-secao'));
        }
      });
    }, { threshold: 0.5 });
    document.querySelectorAll('[data-rastreio-secao]').forEach(function (secao) {
      observador.observe(secao);
    });
    // Seções que aparecem depois (SPA): observa as novas
    new MutationObserver(function () {
      document.querySelectorAll('[data-rastreio-secao]:not([data-rastreio-observada])').forEach(function (secao) {
        secao.setAttribute('data-rastreio-observada', '');
        observador.observe(secao);
      });
    }).observe(document.body, { childList: true, subtree: true });
  }

  ['pushState', 'replaceState'].forEach(function (metodo) {
    var original = history[metodo];
    history[metodo] = function () {
      var resultado = original.apply(this, arguments);
      registrarPagina();
      return resultado;
    };
  });
  window.addEventListener('popstate', registrarPagina);
  window.addEventListener('hashchange', registrarPagina);

  // --- envio ----------------------------------------------------------------------------------------
  function enviar() {
    acumularTempo();
    medirRolagem();
    guardar(estado);
    var fuso = null;
    try {
      fuso = Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch (e) {
      // sem Intl
    }
    var corpo = JSON.stringify({
      tempoNaPaginaSegundos: Math.round(estado.tempo),
      profundidadeRolagem: estado.rolagem,
      elementosClicados: estado.cliques,
      paginasVisitadas: estado.paginas,
      idioma: navigator.language || null,
      fuso: fuso,
      tela: window.screen ? window.screen.width + 'x' + window.screen.height : null
    });
    var url = base + '/public/marketing/visitas/' + estado.visita + '/sinais';
    if (navigator.sendBeacon && navigator.sendBeacon(url, new Blob([corpo], { type: 'text/plain;charset=UTF-8' }))) {
      return;
    }
    if (window.fetch) {
      fetch(url, { method: 'POST', body: corpo, keepalive: true, mode: 'no-cors',
        headers: { 'Content-Type': 'text/plain;charset=UTF-8' } }).catch(function () {});
    }
  }

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'hidden') {
      enviar();
    } else if (visivelDesde === null) {
      visivelDesde = Date.now();
    }
  });
  window.addEventListener('pagehide', enviar);
  window.setInterval(function () {
    if (document.visibilityState === 'visible') {
      enviar();
    }
  }, INTERVALO_MS);

  function iniciar() {
    registrarPagina();
    medirRolagem();
    observarSecoes();
    guardar(estado);
    window.setTimeout(enviar, 3000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', iniciar);
  } else {
    iniciar();
  }
})();
