import { useEffect, useState, type FormEvent } from 'react';
import { AlertCircle, Check, Clock, ShieldAlert } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import { Button, EmptyState, FormField, FormSection, Input, Spinner } from '@/components/ui';
import { obterCicloVida, salvarCicloVida, type ConfiguracaoCicloVida } from '../api';

/**
 * Aba "Ciclo de vida do lead" (W4, norteador §9.4): máximo de tentativas de aquecimento antes de
 * marcar o lead como esgotado (job 4.2) e meses de retenção após esgotamento e após opt-out antes
 * da anonimização automática LGPD (job 4.3).
 */
export function CicloVidaAba() {
  const [configuracao, setConfiguracao] = useState<ConfiguracaoCicloVida | null>(null);
  const [maxTentativas, setMaxTentativas] = useState('3');
  const [mesesEsgotamento, setMesesEsgotamento] = useState('6');
  const [mesesOptout, setMesesOptout] = useState('12');
  const [erro, setErro] = useState<string | null>(null);
  const [erroSalvar, setErroSalvar] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);

  useEffect(() => {
    obterCicloVida()
      .then((dados) => {
        setConfiguracao(dados);
        setMaxTentativas(String(dados.maxTentativasAquecimento));
        setMesesEsgotamento(String(dados.mesesRetencaoPosEsgotamento));
        setMesesOptout(String(dados.mesesRetencaoPosOptout));
      })
      .catch((falha) =>
        setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar o ciclo de vida.'),
      );
  }, []);

  if (erro) {
    return <EmptyState icone={AlertCircle} erro titulo="Ciclo de vida indisponível" descricao={erro} />;
  }
  if (!configuracao) return <Spinner />;

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErroSalvar(null);
    try {
      const atualizado = await salvarCicloVida(
        Number(maxTentativas),
        Number(mesesEsgotamento),
        Number(mesesOptout),
      );
      setConfiguracao(atualizado);
      setSalvo(true);
      window.setTimeout(() => setSalvo(false), 2000);
    } catch (falha) {
      setErroSalvar(
        falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar.',
      );
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-5">
      <div className="flex items-start gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm text-brand-900 dark:border-brand-800/40 dark:bg-brand-800/15 dark:text-brand-200">
        <ShieldAlert aria-hidden className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
        <div>
          <p className="font-semibold">Retenção e minimização de dados (LGPD)</p>
          <p className="mt-0.5 text-brand-800 dark:text-brand-300">
            Ao vencer o prazo de retenção, o job 4.3 zera nome, e-mail, telefone, WhatsApp, respostas de formulário e
            IP/User-Agent do consentimento, preservando apenas a linha anonimizada para as métricas estatísticas do funil.
          </p>
        </div>
      </div>

      <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
        <header className="flex items-center gap-2 border-b border-borda px-5 py-3.5">
          <Clock aria-hidden className="h-4 w-4 text-brand-700" />
          <h2 className="text-sm font-semibold text-titulo">Limites de aquecimento e retenção</h2>
        </header>

        <form onSubmit={salvar} className="space-y-5 p-5" noValidate>
          {erroSalvar && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
              {erroSalvar}
            </p>
          )}

          <FormSection number={1} title="Cadência de aquecimento (Job 4.2)">
            <FormField
              label="Máximo de tentativas de aquecimento"
              required
              colSpan={2}
              hint="Depois desse número de e-mails enviados nas etapas frias (0 e 1) sem avanço, o lead é marcado como esgotado."
            >
              <Input
                type="number"
                min={1}
                max={50}
                value={maxTentativas}
                onChange={(e) => setMaxTentativas(e.target.value)}
              />
            </FormField>
          </FormSection>

          <FormSection number={2} title="Prazos para anonimização LGPD (Job 4.3)">
            <FormField
              label="Retenção após esgotamento (meses)"
              required
              hint="Meses contados de esgotado_em até a anonimização dos dados pessoais."
            >
              <Input
                type="number"
                min={1}
                max={120}
                value={mesesEsgotamento}
                onChange={(e) => setMesesEsgotamento(e.target.value)}
              />
            </FormField>
            <FormField
              label="Retenção após descadastro / opt-out (meses)"
              required
              hint="Meses contados de opt_out_em até a anonimização dos dados pessoais."
            >
              <Input
                type="number"
                min={1}
                max={120}
                value={mesesOptout}
                onChange={(e) => setMesesOptout(e.target.value)}
              />
            </FormField>
          </FormSection>

          <div className="flex justify-end border-t border-borda pt-4">
            <Button type="submit" size="sm" disabled={salvando}>
              {salvo && <Check aria-hidden className="h-3.5 w-3.5" />}
              {salvando ? 'Salvando…' : salvo ? 'Salvo' : 'Salvar configurações'}
            </Button>
          </div>
        </form>
      </section>
    </div>
  );
}
