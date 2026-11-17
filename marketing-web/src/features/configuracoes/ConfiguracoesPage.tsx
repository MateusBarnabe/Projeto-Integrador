import { useSearchParams } from 'react-router';
import { Award, Clock, Mail, Megaphone, Radar } from 'lucide-react';
import { Tabs, type Aba } from '@/components/ui';
import { CanaisAba } from './canais/CanaisAba';
import { CicloVidaAba } from './ciclo-vida/CicloVidaAba';
import { ModelosAba } from './modelos/ModelosAba';
import { QualificacaoAba } from './qualificacao/QualificacaoAba';
import { RastreamentoAba } from './rastreamento/RastreamentoAba';

const ABAS: Aba[] = [
  { valor: 'rastreamento', rotulo: 'Rastreamento', icone: Radar },
  { valor: 'canais', rotulo: 'Canais de anúncio', icone: Megaphone },
  { valor: 'modelos', rotulo: 'Modelos de mensagem', icone: Mail },
  { valor: 'ciclo-vida', rotulo: 'Ciclo de vida', icone: Clock },
  { valor: 'qualificacao', rotulo: 'Qualificação e distribuição', icone: Award },
];

/**
 * Tela Configurações (norteador §9.4, R2 e W4), uma tela com cinco abas internas; a aba fica na URL (`?aba=`).
 */
export function ConfiguracoesPage() {
  const [parametros, setParametros] = useSearchParams();
  const aba = ABAS.some((item) => item.valor === parametros.get('aba')) ? parametros.get('aba')! : 'rastreamento';

  return (
    <div className="flex flex-1 flex-col">
      <div className="border-b border-borda bg-superficie px-5">
        <Tabs
          abas={ABAS}
          ativa={aba}
          onChange={(valor) => setParametros(valor === 'rastreamento' ? {} : { aba: valor }, { replace: true })}
          label="Seções das configurações"
        />
      </div>
      <div role="tabpanel" className="flex-1 p-5">
        {aba === 'rastreamento' && <RastreamentoAba />}
        {aba === 'canais' && <CanaisAba />}
        {aba === 'modelos' && <ModelosAba />}
        {aba === 'ciclo-vida' && <CicloVidaAba />}
        {aba === 'qualificacao' && <QualificacaoAba />}
      </div>
    </div>
  );
}
