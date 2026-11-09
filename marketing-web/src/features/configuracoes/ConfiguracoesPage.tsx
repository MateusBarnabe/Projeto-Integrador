import { useSearchParams } from 'react-router';
import { Award, Clock, Mail, Megaphone, Radar } from 'lucide-react';
import { EmConstrucao } from '@/components/EmConstrucao';
import { Tabs, type Aba } from '@/components/ui';
import { RastreamentoAba } from './rastreamento/RastreamentoAba';

const ABAS: Aba[] = [
  { valor: 'rastreamento', rotulo: 'Rastreamento', icone: Radar },
  { valor: 'canais', rotulo: 'Canais de anúncio', icone: Megaphone },
  { valor: 'modelos', rotulo: 'Modelos de mensagem', icone: Mail },
  { valor: 'ciclo-vida', rotulo: 'Ciclo de vida', icone: Clock },
  { valor: 'qualificacao', rotulo: 'Qualificação e distribuição', icone: Award },
];

const EM_CONSTRUCAO: Record<string, string> = {
  canais: 'Canais de anúncio: orçamento, público e objetivo por canal, com os segredos do Meta só de escrita e a URL do webhook (norteador 9.4, W4).',
  modelos: 'Modelos de mensagem: boas-vindas e a cadência de aquecimento, com pré-visualização e o rodapé de descadastro (W4).',
  'ciclo-vida': 'Ciclo de vida: máximo de tentativas e retenção depois do esgotamento e do opt-out (W4).',
  qualificacao: 'Qualificação e distribuição: regras de pontuação (inclusive as de comportamento do lead 0), faixas e rodízio (W4).',
};

/**
 * Tela Configurações (norteador 9.4), uma tela com abas internas; a aba fica na URL (`?aba=`).
 * Rastreamento (R1) é a primeira e a única pronta; as outras chegam com a W4.
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
        {aba === 'rastreamento' ? <RastreamentoAba /> : <EmConstrucao descricao={EM_CONSTRUCAO[aba]} />}
      </div>
    </div>
  );
}
