import { Eye, MousePointerClick, Timer, Trash2, UserCheck } from 'lucide-react';
import type { LeadZero } from './api';
import { BarrasHorizontais } from './componentes/BarrasHorizontais';
import { Bloco, SemDados } from './componentes/Bloco';
import { CartaoNumero } from './componentes/CartaoNumero';
import { NOME_DISPOSITIVO, numero, percentual, rotulo } from './formatos';

const CABECALHO = 'px-4 py-2.5 font-medium';

/**
 * R6: o "lead 0" (ponto de IoT). Cliques em anúncios que ainda não viraram lead: ficam em andamento até
 * o visitante enviar o formulário (convertida) ou o prazo passar (descartada). Não segue o filtro de origem.
 */
export function LeadZeroBloco({ leadZero }: { leadZero: LeadZero }) {
  const { cliques, ativas, convertidas, descartadas, taxaConversao, porCampanha, porDispositivo } = leadZero;

  return (
    <Bloco
      titulo="Lead 0 · cliques em anúncios"
      descricao="Visitas vindas de anúncio, antes de virarem lead. Seguem o período e a campanha do filtro, não a origem."
    >
      {cliques === 0 ? (
        <SemDados>Nenhum clique em anúncio no período.</SemDados>
      ) : (
        <div className="flex flex-col gap-5">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
            <CartaoNumero rotulo="Cliques" valor={numero(cliques)} icone={MousePointerClick} />
            <CartaoNumero rotulo="Em andamento" valor={numero(ativas)} dica="Ainda dentro do prazo" icone={Timer} />
            <CartaoNumero rotulo="Convertidas" valor={numero(convertidas)} dica="Viraram lead" icone={UserCheck} destaque />
            <CartaoNumero rotulo="Descartadas" valor={numero(descartadas)} dica="Prazo sem formulário" icone={Trash2} />
            <CartaoNumero rotulo="Taxa de conversão" valor={percentual(taxaConversao)} dica="Convertidas por clique" icone={Eye} destaque />
          </div>

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
            <div>
              <h3 className="mb-2 text-xs font-semibold tracking-wide text-texto-3 uppercase">Por campanha</h3>
              {porCampanha.length === 0 ? (
                <SemDados>Sem campanha identificada.</SemDados>
              ) : (
                <div className="overflow-x-auto rounded-lg border border-borda">
                  <table className="w-full text-left text-sm">
                    <thead className="border-b border-borda bg-superficie-2 text-xs text-texto-3">
                      <tr>
                        <th scope="col" className={CABECALHO}>Campanha</th>
                        <th scope="col" className={`${CABECALHO} text-right`}>Cliques</th>
                        <th scope="col" className={`${CABECALHO} text-right`}>Convertidas</th>
                        <th scope="col" className={`${CABECALHO} text-right`}>Descartadas</th>
                        <th scope="col" className={`${CABECALHO} text-right`}>Taxa</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-borda">
                      {porCampanha.map((campanha) => (
                        <tr key={campanha.utmCampaign}>
                          <td className="px-4 py-2.5 font-medium text-titulo">{campanha.utmCampaign}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.cliques)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.convertidas)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">{numero(campanha.descartadas)}</td>
                          <td className="px-4 py-2.5 text-right tabular-nums">
                            {campanha.cliques > 0 ? percentual((campanha.convertidas / campanha.cliques) * 100) : '—'}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            <div>
              <h3 className="mb-2 text-xs font-semibold tracking-wide text-texto-3 uppercase">Por dispositivo</h3>
              {porDispositivo.length === 0 ? (
                <SemDados>Sem dispositivo identificado.</SemDados>
              ) : (
                <BarrasHorizontais
                  itens={porDispositivo.map((dispositivo) => ({
                    chave: dispositivo.tipoDispositivo,
                    rotulo: rotulo(NOME_DISPOSITIVO, dispositivo.tipoDispositivo),
                    valor: dispositivo.cliques,
                    complemento: `${numero(dispositivo.convertidas)} convertidas`,
                  }))}
                />
              )}
            </div>
          </div>
        </div>
      )}
    </Bloco>
  );
}
