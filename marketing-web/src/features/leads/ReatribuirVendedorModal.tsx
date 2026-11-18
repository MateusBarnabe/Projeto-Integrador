import { useEffect, useState } from 'react';
import { AlertCircle } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import { Button, FormField, Modal, Select } from '@/components/ui';
import { listarVendedoresDaEscala, reatribuirVendedor, type VendedorDaEscala } from './api';
import { vendedorEncurtado } from './formatos';

interface Props {
  aberto: boolean;
  leadId: string;
  vendedorAtual: string | null;
  onClose: () => void;
  /** Chamado depois do `200`, para o detalhe recarregar. */
  onReatribuido: () => void;
}

/**
 * Reatribuição manual do vendedor (norteador §6 e §9.3): `PUT /api/marketing/leads/{id}/vendedor`.
 * A escala vem de `GET /api/marketing/rodizio/vendedores`. Quem manda nas permissões é a API: sem
 * `marketing.qualificacao.editar` ela responde `403`, e a tela só mostra a mensagem.
 */
export function ReatribuirVendedorModal({ aberto, leadId, vendedorAtual, onClose, onReatribuido }: Props) {
  const [escala, setEscala] = useState<VendedorDaEscala[] | null>(null);
  const [escolhido, setEscolhido] = useState('');
  const [erroDaEscala, setErroDaEscala] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function carregarEscala() {
    setErroDaEscala(null);
    return listarVendedoresDaEscala()
      .then(setEscala)
      .catch((falha) =>
        setErroDaEscala(
          falha instanceof ErroApi ? falha.message : 'Não foi possível carregar a escala do rodízio.',
        ),
      );
  }

  useEffect(() => {
    if (!aberto) return;
    setEscala(null);
    setEscolhido('');
    setErro(null);
    void carregarEscala();
  }, [aberto]);

  async function confirmar() {
    if (!escolhido) return;
    setEnviando(true);
    setErro(null);
    try {
      await reatribuirVendedor(leadId, escolhido);
      onReatribuido();
      onClose();
    } catch (falha) {
      setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível reatribuir o vendedor.');
      // 422: a escala mudou desde que a lista foi carregada
      if (falha instanceof ErroApi && falha.erros.some((e) => e.codigo === 'VENDEDOR_FORA_DA_ESCALA')) {
        setEscolhido('');
        void carregarEscala();
      }
    } finally {
      setEnviando(false);
    }
  }

  const opcoes = (escala ?? []).filter((vendedor) => vendedor.vendedorId !== vendedorAtual);

  return (
    <Modal
      open={aberto}
      onClose={onClose}
      size="sm"
      title="Reatribuir vendedor"
      subtitle="O lead passa para o vendedor escolhido, entre os da escala do rodízio."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={enviando}>
            Cancelar
          </Button>
          <Button onClick={confirmar} disabled={!escolhido || enviando}>
            {enviando ? 'Reatribuindo…' : 'Reatribuir'}
          </Button>
        </>
      }
    >
      <FormField
        label="Vendedor"
        required
        hint={vendedorAtual ? `Atual: ${vendedorEncurtado(vendedorAtual)}. Nomes ficam indisponíveis até a X7.` : 'Nomes ficam indisponíveis até a X7.'}
      >
        <Select
          value={escolhido}
          onChange={(evento) => setEscolhido(evento.target.value)}
          disabled={!escala || enviando}
        >
          <option value="">{escala ? 'Escolha um vendedor' : 'Carregando…'}</option>
          {opcoes.map((vendedor) => (
            <option key={vendedor.vendedorId} value={vendedor.vendedorId}>
              {vendedorEncurtado(vendedor.vendedorId)}
              {!vendedor.ativo && ' (inativo)'}
            </option>
          ))}
        </Select>
      </FormField>
      {escala && opcoes.length === 0 && !erroDaEscala && (
        <p className="mt-3 text-xs text-texto-3">Não há outro vendedor na escala. Cadastre em Configurações › Qualificação e distribuição.</p>
      )}
      {(erroDaEscala || erro) && (
        <p
          role="alert"
          className="mt-3 flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300"
        >
          <AlertCircle aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
          {erro ?? erroDaEscala}
        </p>
      )}
    </Modal>
  );
}
