import { useState } from 'react';
import { BarChart3, MessageCircle, ShieldCheck, Target } from 'lucide-react';
import { AvisoPlaceholder } from './AvisoPlaceholder';
import { ChatbotExemplo } from './ChatbotExemplo';
import { FormularioExemplo } from './FormularioExemplo';
import { esquecerOrigem, registrarVisita, type Origem, type ToqueOrigem } from './origem';

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
          <div>
            <button
              onClick={() => setFormularioAberto(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-brand-700 px-5 py-3 font-semibold text-white hover:bg-brand-800"
            >
              <MessageCircle className="h-5 w-5" />
              Fale conosco
            </button>
          </div>
        </div>
      </header>

      <section className="mx-auto grid max-w-5xl gap-6 px-6 py-16 md:grid-cols-3">
        {BENEFICIOS.map(({ icone: Icone, titulo, texto }) => (
          <div key={titulo} className="rounded-xl border border-gray-100 p-6">
            <Icone className="mb-3 h-6 w-6 text-brand-700" />
            <h2 className="mb-1 font-semibold">{titulo}</h2>
            <p className="text-sm text-gray-600">{texto}</p>
          </div>
        ))}
      </section>

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

/** Rodapé de teste: mostra a origem que vai no envio e permite recomeçar a jornada. */
function PainelOrigem({ origem }: { origem: Origem }) {
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
