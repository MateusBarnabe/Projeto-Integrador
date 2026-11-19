import { useMemo } from 'react';
import type { PontoDaSerie } from '../api';
import { NOME_ORIGEM, dataLocal, diaEMes, numero, paraIso, rotulo } from '../formatos';

/** Cores por origem, todas do Design System. Classes completas para o Tailwind enxergá-las. */
const COR_DA_ORIGEM: Record<string, { linha: string; ponto: string; legenda: string }> = {
  meta_ads: { linha: 'stroke-blue-500', ponto: 'fill-blue-500', legenda: 'bg-blue-500' },
  landing_page: { linha: 'stroke-brand-700', ponto: 'fill-brand-700', legenda: 'bg-brand-700' },
  manual: { linha: 'stroke-orange-500', ponto: 'fill-orange-500', legenda: 'bg-orange-500' },
};
const COR_PADRAO = { linha: 'stroke-purple-500', ponto: 'fill-purple-500', legenda: 'bg-purple-500' };

const LARGURA = 640;
const ALTURA = 220;
const MARGEM = { esquerda: 36, direita: 12, topo: 12, base: 26 };

/** Dias do período, de `AAAA-MM-DD` a `AAAA-MM-DD`, para preencher com zero os dias sem lead. */
function diasDoPeriodo(periodo: string, pontos: PontoDaSerie[]): string[] {
  let inicio: Date | null = null;
  let fim: Date | null = null;

  const relativo = /^(\d+)d$/.exec(periodo);
  if (relativo) {
    fim = new Date();
    fim = new Date(fim.getFullYear(), fim.getMonth(), fim.getDate());
    inicio = new Date(fim);
    inicio.setDate(inicio.getDate() - (Number(relativo[1]) - 1));
  } else if (/^\d{4}-\d{2}-\d{2},\d{4}-\d{2}-\d{2}$/.test(periodo)) {
    const [de, ate] = periodo.split(',');
    inicio = dataLocal(de);
    fim = dataLocal(ate);
  }
  if (!inicio || !fim) {
    const datas = pontos.map((ponto) => ponto.data).sort();
    if (datas.length === 0) return [];
    inicio = dataLocal(datas[0]);
    fim = dataLocal(datas[datas.length - 1]);
  }

  const dias: string[] = [];
  // Limite de segurança: um intervalo enorme não pode travar a tela
  for (let dia = new Date(inicio); dia <= fim && dias.length < 400; dia.setDate(dia.getDate() + 1)) {
    dias.push(paraIso(dia));
  }
  return dias;
}

function tetoDoEixo(maior: number) {
  if (maior <= 4) return 4;
  const passo = Math.pow(10, Math.floor(Math.log10(maior / 4)));
  const base = Math.ceil(maior / 4 / passo);
  const bonito = base <= 1 ? 1 : base <= 2 ? 2 : base <= 5 ? 5 : 10;
  return bonito * passo * 4;
}

/**
 * Novos leads por dia, uma linha por origem (SVG, sem biblioteca). Cada dia tem uma faixa invisível com
 * `<title>` (dica ao passar o mouse). A tabela escondida ao lado dá o mesmo dado ao leitor de tela.
 */
export function SerieTemporal({ pontos, periodo }: { pontos: PontoDaSerie[]; periodo: string }) {
  const grafico = useMemo(() => {
    const dias = diasDoPeriodo(periodo, pontos);
    const ordem = ['meta_ads', 'landing_page'];
    const origens = [...new Set(pontos.map((ponto) => ponto.origem))].sort(
      (a, b) => (ordem.indexOf(a) === -1 ? 99 : ordem.indexOf(a)) - (ordem.indexOf(b) === -1 ? 99 : ordem.indexOf(b)) || a.localeCompare(b),
    );
    const valores = new Map<string, number>();
    pontos.forEach((ponto) => {
      const chave = `${ponto.origem}|${ponto.data}`;
      valores.set(chave, (valores.get(chave) ?? 0) + ponto.novosLeads);
    });
    const valor = (origem: string, dia: string) => valores.get(`${origem}|${dia}`) ?? 0;
    const maior = Math.max(0, ...origens.flatMap((origem) => dias.map((dia) => valor(origem, dia))));
    const total = pontos.reduce((soma, ponto) => soma + ponto.novosLeads, 0);
    return { dias, origens, valor, teto: tetoDoEixo(maior), total };
  }, [pontos, periodo]);

  const { dias, origens, valor, teto, total } = grafico;
  if (dias.length === 0 || origens.length === 0 || total === 0) return null;

  const areaLarg = LARGURA - MARGEM.esquerda - MARGEM.direita;
  const areaAlt = ALTURA - MARGEM.topo - MARGEM.base;
  const x = (indice: number) => MARGEM.esquerda + (dias.length === 1 ? areaLarg / 2 : (indice / (dias.length - 1)) * areaLarg);
  const y = (v: number) => MARGEM.topo + areaAlt - (v / teto) * areaAlt;
  const faixa = dias.length === 1 ? areaLarg : areaLarg / (dias.length - 1);
  const comPontos = dias.length <= 31;

  const passoRotulo = Math.max(1, Math.ceil(dias.length / 7));
  const grades = [0, 1, 2, 3, 4].map((i) => (teto / 4) * i);

  return (
    <div>
      <div className="mb-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-texto-2">
        {origens.map((origem) => (
          <span key={origem} className="inline-flex items-center gap-1.5">
            <span aria-hidden className={`h-0.5 w-4 rounded-full ${(COR_DA_ORIGEM[origem] ?? COR_PADRAO).legenda}`} />
            {rotulo(NOME_ORIGEM, origem)}
          </span>
        ))}
      </div>

      <svg viewBox={`0 0 ${LARGURA} ${ALTURA}`} className="h-auto w-full" role="img" aria-label="Novos leads por dia, por origem">
        {grades.map((grade) => (
          <g key={grade}>
            <line x1={MARGEM.esquerda} x2={LARGURA - MARGEM.direita} y1={y(grade)} y2={y(grade)} className="stroke-borda" strokeWidth={1} />
            <text x={MARGEM.esquerda - 6} y={y(grade)} dy="0.32em" textAnchor="end" className="fill-texto-3 text-[10px]">
              {numero(grade)}
            </text>
          </g>
        ))}

        {dias.map((dia, indice) =>
          indice % passoRotulo === 0 ? (
            <text key={dia} x={x(indice)} y={ALTURA - 8} textAnchor="middle" className="fill-texto-3 text-[10px]">
              {diaEMes(dia)}
            </text>
          ) : null,
        )}

        {origens.map((origem) => {
          const cor = COR_DA_ORIGEM[origem] ?? COR_PADRAO;
          return (
            <g key={origem}>
              <polyline
                fill="none"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                className={cor.linha}
                points={dias.map((dia, indice) => `${x(indice)},${y(valor(origem, dia))}`).join(' ')}
              />
              {comPontos &&
                dias.map((dia, indice) =>
                  valor(origem, dia) > 0 ? <circle key={dia} cx={x(indice)} cy={y(valor(origem, dia))} r={2.5} className={cor.ponto} /> : null,
                )}
            </g>
          );
        })}

        {dias.map((dia, indice) => (
          <rect key={dia} x={x(indice) - faixa / 2} y={MARGEM.topo} width={faixa} height={areaAlt} fill="transparent">
            <title>
              {diaEMes(dia)}: {origens.map((origem) => `${rotulo(NOME_ORIGEM, origem)} ${numero(valor(origem, dia))}`).join(' · ')}
            </title>
          </rect>
        ))}
      </svg>

      <table className="sr-only">
        <caption>Novos leads por dia e origem</caption>
        <thead>
          <tr>
            <th scope="col">Dia</th>
            {origens.map((origem) => (
              <th key={origem} scope="col">
                {rotulo(NOME_ORIGEM, origem)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {dias.map((dia) => (
            <tr key={dia}>
              <th scope="row">{diaEMes(dia)}</th>
              {origens.map((origem) => (
                <td key={origem}>{valor(origem, dia)}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
