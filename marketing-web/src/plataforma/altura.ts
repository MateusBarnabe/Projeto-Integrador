/*
 * Altura do iframe e área visível (só modo embutido).
 *
 * A casca ajusta a altura do iframe ao que o módulo informa (modulo:altura), então o iframe é tão
 * alto quanto o conteúdo e quem rola é a página da casca. Duas consequências:
 *
 * 1. Um modal "fixed inset-0" cobriria o iframe inteiro. Centralizado nele, apareceria em qualquer
 *    ponto da página, talvez fora da tela. Por isso medimos a parte do iframe que o usuário está
 *    vendo e a publicamos em duas variáveis CSS no <html>, que o Modal usa:
 *      --area-topo     distância do topo do iframe até o topo da janela do usuário
 *      --area-altura   altura da janela do usuário a partir dali
 *    A medida usa window.frameElement e window.parent, possíveis porque a casca e os módulos têm a
 *    mesma origem (Contrato §12.8). Sem acesso (outra origem), as variáveis ficam nos padrões do
 *    index.css e o Modal se comporta como fora do iframe.
 * 2. Elementos fixos não contam na altura do conteúdo. Com um modal aberto (marcado com
 *    data-sobreposicao), a altura informada é pelo menos --area-topo + --area-altura, para o modal
 *    inteiro caber no iframe mesmo numa tela curta.
 */

const MARGEM_MINIMA = 240;

/**
 * Altura do cabeçalho fixo da casca (3rem, h-12), que fica por cima do topo da página quando ela
 * rola. A parte do iframe sob o cabeçalho não conta como visível.
 */
const ALTURA_CABECALHO_DA_CASCA = 48;

interface Area {
  topo: number;
  altura: number;
}

function medirArea(): Area | null {
  try {
    const quadro = window.frameElement;
    if (!quadro) return null;
    const retangulo = quadro.getBoundingClientRect();
    // Onde, na janela da casca, começa a parte visível do iframe: abaixo do cabeçalho
    const inicioVisivel = Math.max(retangulo.top, ALTURA_CABECALHO_DA_CASCA);
    return {
      topo: Math.max(0, Math.round(inicioVisivel - retangulo.top)),
      altura: Math.max(MARGEM_MINIMA, Math.round(window.parent.innerHeight - inicioVisivel)),
    };
  } catch {
    return null;
  }
}

function alturaDoConteudo() {
  // O layout embutido não tem min-h-screen: o corpo tem a altura do conteúdo, e o iframe pode encolher
  return Math.ceil(document.body.getBoundingClientRect().height);
}

/**
 * Informa a altura do conteúdo a cada mudança (tamanho do corpo, modal aberto ou fechado, rolagem ou
 * redimensionamento da casca) e mantém as variáveis de área visível em dia.
 */
export function acompanharAlturaEArea(informar: (altura: number) => void) {
  let ultimaAltura = -1;
  let agendado = false;

  function atualizar() {
    agendado = false;
    const area = medirArea();
    const raiz = document.documentElement.style;
    if (area) {
      raiz.setProperty('--area-topo', `${area.topo}px`);
      raiz.setProperty('--area-altura', `${area.altura}px`);
    }

    let altura = alturaDoConteudo();
    if (area && document.querySelector('[data-sobreposicao]')) {
      altura = Math.max(altura, area.topo + area.altura);
    }
    if (altura !== ultimaAltura) {
      ultimaAltura = altura;
      informar(altura);
    }
  }

  // Várias mudanças no mesmo quadro (rolagem, render) viram uma medida só
  function agendar() {
    if (agendado) return;
    agendado = true;
    requestAnimationFrame(atualizar);
  }

  new ResizeObserver(agendar).observe(document.body);
  // O modal entra no <body> por portal: a abertura e o fechamento mudam os filhos do corpo
  new MutationObserver(agendar).observe(document.body, { childList: true });

  try {
    // Captura: a rolagem da casca pode estar num contêiner interno, e scroll não borbulha
    window.parent.addEventListener('scroll', agendar, { capture: true, passive: true });
    window.parent.addEventListener('resize', agendar, { passive: true });
  } catch {
    // Outra origem: sem acompanhar a rolagem da casca
  }

  agendar();
}
