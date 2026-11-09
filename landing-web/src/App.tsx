import { useState } from 'react';
import { BarChart3, MessageCircle, ShieldCheck, Target } from 'lucide-react';
import { AvisoPlaceholder } from './AvisoPlaceholder';
import { ChatbotExemplo } from './ChatbotExemplo';
import { FormularioExemplo } from './FormularioExemplo';
import { esquecerOrigem, registrarVisita, type Origem, type ToqueOrigem } from './origem';
import { visitaDoAnuncio } from './rastreamentoMarketing';

const BENEFICIOS = [
  { icone: Target, titulo: 'Diagnóstico', texto: 'Entendemos o momento da sua empresa antes de propor qualquer coisa.' },
  { icone: BarChart3, titulo: 'Resultado medido', texto: 'Metas claras e acompanhamento mensal dos indicadores.' },
  { icone: ShieldCheck, titulo: 'Sem surpresa', texto: 'Proposta fechada, com escopo e prazo definidos.' },
];

/**
 * Landing page de teste de um tenant (Empresa A). É onde o visitante chega pelo anúncio,
 * clica em "Fale conosco" e preenche o formulário de exemplo, que é do Landing (nada vem do Marketing).
 */
export function App() {
  const [formularioAberto, setFormularioAberto] = useState(false);
  // guarda a primeira e a última origem uma vez, na chegada
  const [origem] = useState(registrarVisita);

  return (
    <div className="flex min-h-screen flex-col">
      <AvisoPlaceholder />

      <header className="bg-brand-950 text-white">
        <div className="mx-auto flex max-w-5xl flex-col gap-6 px-6 py-20">
          <p className="text-sm font-semibold uppercase tracking-wider text-brand-300">Empresa A · Consultoria</p>
          <h1 className="max-w-2xl text-4xl font-bold tracking-tight">Organize a operação e venda mais em 90 dias</h1>
          <p className="max-w-xl text-lg text-gray-300">
            Consultoria para pequenas e médias empresas que querem crescer com processo, e não com improviso.
          </p>
          <div className="flex flex-wrap gap-3">
            <button
              data-rastreio="fale-conosco"
              onClick={() => setFormularioAberto(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-700 px-5 py-3 font-semibold text-white hover:bg-brand-800"
            >
              <MessageCircle className="h-5 w-5" />
              Fale conosco
            </button>
            <a
              href="#precos"
              data-rastreio="ver-planos"
              className="inline-flex items-center gap-2 rounded-lg border border-white/30 px-5 py-3 font-semibold text-white hover:bg-white/10"
            >
              Ver planos
            </a>
          </div>
        </div>
      </header>

      <section data-rastreio-secao="beneficios" className="mx-auto grid max-w-5xl gap-6 px-6 py-16 md:grid-cols-3">
        {BENEFICIOS.map(({ icone: Icone, titulo, texto }) => (
          <div key={titulo} className="rounded-xl border border-gray-100 p-6">
            <Icone className="mb-3 h-6 w-6 text-brand-700" />
            <h2 className="mb-1 font-semibold">{titulo}</h2>
            <p className="text-sm text-gray-600">{texto}</p>
          </div>
        ))}
      </section>

      <Planos onContratar={() => setFormularioAberto(true)} />

      {formularioAberto && (
        <section className="mx-auto w-full max-w-xl px-6 pb-16">
          <FormularioExemplo origem={origem} />
        </section>
      )}

      <PainelOrigem origem={origem} />
      <ChatbotExemplo origem={origem} />
    </div>
  );
}

const PLANOS = [
  { nome: 'Diagnóstico', preco: 'R$ 4.900', texto: 'Duas semanas de imersão e um plano de ação.' },
  { nome: 'Acompanhamento', preco: 'R$ 9.800/mês', texto: 'Diagnóstico mais três meses de execução junto com o time.' },
];

/** Seção de preços: o script do Marketing registra a visita a ela e o clique no simulador. */
function Planos({ onContratar }: { onContratar: () => void }) {
  return (
    <section id="precos" data-rastreio-secao="precos" className="bg-gray-50 py-16">
      <div className="mx-auto max-w-5xl px-6">
        <h2 className="mb-6 text-2xl font-bold">Planos e preços</h2>
        <div className="grid gap-6 md:grid-cols-2">
          {PLANOS.map((plano) => (
            <div key={plano.nome} className="rounded-xl border border-gray-200 bg-white p-6">
              <p className="font-semibold">{plano.nome}</p>
              <p className="my-2 text-3xl font-bold text-brand-800">{plano.preco}</p>
              <p className="mb-4 text-sm text-gray-600">{plano.texto}</p>
              <button
                data-rastreio={`contratar-${plano.nome.toLowerCase()}`}
                onClick={onContratar}
                className="rounded-lg bg-brand-700 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-800"
              >
                Quero este
              </button>
            </div>
          ))}
        </div>
        <button data-rastreio="simulador-preco" className="mt-6 text-sm font-medium text-brand-800 underline">
          Simular o investimento para a minha empresa
        </button>
      </div>
    </section>
  );
}

/** Rodapé de teste: mostra a origem que vai no envio e permite recomeçar a jornada. */
function PainelOrigem({ origem }: { origem: Origem }) {
  const visita = visitaDoAnuncio(origem.ultima.paginaEntrada) ?? visitaDoAnuncio(origem.primeira.paginaEntrada);
  const resumo = (t: ToqueOrigem) =>
    [
      t.utmSource && `utm_source=${t.utmSource}`,
      t.utmMedium && `utm_medium=${t.utmMedium}`,
      t.utmCampaign && `utm_campaign=${t.utmCampaign}`,
      t.utmTerm && `utm_term=${t.utmTerm}`,
      t.utmContent && `utm_content=${t.utmContent}`,
      t.fbclid && `fbclid=${t.fbclid}`,
      t.gclid && `gclid=${t.gclid}`,
      t.referenciador && `referenciador=${t.referenciador}`,
    ]
      .filter(Boolean)
      .join(' · ') || 'acesso direto';

  return (
    <footer className="mt-auto border-t border-gray-100 bg-gray-50 px-6 py-4 text-xs text-gray-500">
      <div className="mx-auto flex max-w-5xl flex-col gap-1">
        <span>Primeira origem: {resumo(origem.primeira)}</span>
        <span>Última origem: {resumo(origem.ultima)}</span>
        <span>
          Visita de anúncio rastreada pelo Marketing (mkt_visita): {visita ?? 'nenhuma, a página não veio de um link de campanha'}
        </span>
        <button
          onClick={() => {
            esquecerOrigem();
            window.location.reload();
          }}
          className="self-start text-brand-800 underline"
        >
          Esquecer a origem guardada
        </button>
      </div>
    </footer>
  );
}
