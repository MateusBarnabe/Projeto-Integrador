import { useEffect, useState, type FormEvent } from 'react';
import { AlertCircle, Check, Copy, KeyRound, Megaphone, ShieldCheck } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import { Badge, Button, EmptyState, FormField, FormSection, Input, Spinner, statusBadge } from '@/components/ui';
import { usarPermissao } from '@/plataforma';
import { listarCanaisAnuncio, salvarCanalAnuncio, type CanalAnuncio } from '../api';
import { SomenteLeitura } from '../SomenteLeitura';

const TITULOS: Record<string, { nome: string; descricao: string }> = {
  google_ads: {
    nome: 'Google Ads',
    descricao: 'Pesquisa paga e Rede de Display. O orçamento mensal alimenta o custo estimado por lead no painel.',
  },
  meta_ads: {
    nome: 'Meta Ads (Facebook e Instagram)',
    descricao: 'Campanhas de anúncios e formulários instantâneos (Lead Ads) recebidos via webhook.',
  },
};

/**
 * Aba "Canais de anúncio" (W4, norteador §9.4): um card por canal (`google_ads` e `meta_ads`), com
 * orçamento mensal, público-alvo, objetivo e, no Meta Ads, credenciais só de escrita e URL do webhook.
 */
export function CanaisAba() {
  const [canais, setCanais] = useState<CanalAnuncio[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    listarCanaisAnuncio()
      .then(setCanais)
      .catch((falha) =>
        setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar os canais de anúncio.'),
      );
  }, []);

  if (erro) {
    return <EmptyState icone={AlertCircle} erro titulo="Canais indisponíveis" descricao={erro} />;
  }
  if (!canais) return <Spinner />;

  return (
    <div className="grid gap-5 xl:grid-cols-2">
      {canais.map((canal) => (
        <CartaoCanal
          key={canal.canal}
          canal={canal}
          onSalvo={(atualizado) =>
            setCanais((atuais) => atuais?.map((c) => (c.canal === atualizado.canal ? atualizado : c)) ?? null)
          }
        />
      ))}
    </div>
  );
}

function CartaoCanal({ canal, onSalvo }: { canal: CanalAnuncio; onSalvo: (canal: CanalAnuncio) => void }) {
  const info = TITULOS[canal.canal] ?? { nome: canal.canal, descricao: '' };
  const isMeta = canal.canal === 'meta_ads';
  const somenteLeitura = !usarPermissao('marketing.configuracao.editar');

  const [ativo, setAtivo] = useState(canal.ativo);
  const [orcamento, setOrcamento] = useState(canal.orcamentoMensal !== null ? String(canal.orcamentoMensal) : '');
  const [publicoAlvo, setPublicoAlvo] = useState(canal.publicoAlvo ?? '');
  const [objetivo, setObjetivo] = useState(canal.objetivo ?? '');
  const [metaPageId, setMetaPageId] = useState(canal.metaPageId ?? '');
  const [metaAppSecret, setMetaAppSecret] = useState('');
  const [metaVerifyToken, setMetaVerifyToken] = useState('');
  const [metaPageAccessToken, setMetaPageAccessToken] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);
    try {
      const valorOrcamento = orcamento.trim() === '' ? null : Number(orcamento.replace(',', '.'));
      const atualizado = await salvarCanalAnuncio({
        canal: canal.canal,
        orcamentoMensal: valorOrcamento,
        publicoAlvo: publicoAlvo.trim() || null,
        objetivo: objetivo.trim() || null,
        ativo,
        ...(isMeta
          ? {
              metaPageId: metaPageId.trim() || null,
              metaAppSecret: metaAppSecret.trim() || null,
              metaVerifyToken: metaVerifyToken.trim() || null,
              metaPageAccessToken: metaPageAccessToken.trim() || null,
            }
          : {}),
      });
      setMetaAppSecret('');
      setMetaVerifyToken('');
      setMetaPageAccessToken('');
      onSalvo(atualizado);
      setSalvo(true);
      window.setTimeout(() => setSalvo(false), 2000);
    } catch (falha) {
      setErro(falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
      <header className="flex items-center justify-between gap-3 border-b border-borda px-5 py-4">
        <div className="min-w-0">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
            <Megaphone aria-hidden className="h-4 w-4 shrink-0 text-brand-700" />
            {info.nome}
            {statusBadge(ativo ? 'ativo' : 'inativo')}
          </h2>
          <p className="mt-0.5 text-xs text-texto-3">{info.descricao}</p>
        </div>
        <label className="flex shrink-0 items-center gap-2 text-xs text-texto-2">
          Canal ativo
          <input
            type="checkbox"
            role="switch"
            checked={ativo}
            disabled={somenteLeitura}
            onChange={(e) => setAtivo(e.target.checked)}
            className="h-5 w-9 cursor-pointer appearance-none disabled:cursor-not-allowed disabled:opacity-50 rounded-full bg-borda-forte transition-colors before:block before:h-4 before:w-4 before:translate-x-0.5 before:rounded-full before:bg-white before:transition-transform checked:bg-brand-700 checked:before:translate-x-4.5"
          />
        </label>
      </header>

      <form onSubmit={salvar} className="space-y-5 p-5" noValidate>
        {erro && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <FormSection number={1} title="Planejamento do canal">
          <FormField label="Orçamento mensal pretendido (R$)" hint="Usado para calcular o custo estimado por lead e por MQL no painel.">
            <Input
              type="number"
              step="0.01"
              min="0"
              placeholder="1500.00"
              value={orcamento}
              disabled={somenteLeitura}
              onChange={(e) => setOrcamento(e.target.value)}
            />
          </FormField>
          <FormField label="Objetivo da campanha">
            <Input
              placeholder="Geração de leads qualificados"
              value={objetivo}
              disabled={somenteLeitura}
              onChange={(e) => setObjetivo(e.target.value)}
            />
          </FormField>
          <FormField label="Público-alvo de referência" colSpan={2}>
            <Input
              placeholder="Gestores e diretores de empresas de médio porte"
              value={publicoAlvo}
              disabled={somenteLeitura}
              onChange={(e) => setPublicoAlvo(e.target.value)}
            />
          </FormField>
        </FormSection>

        {isMeta && (
          <FormSection number={2} title="Credenciais do Meta Lead Ads (somente escrita)">
            <div className="md:col-span-2 flex flex-wrap items-center gap-2 rounded-lg bg-superficie-2 px-3 py-2 text-xs text-texto-2">
              <KeyRound aria-hidden className="h-3.5 w-3.5 shrink-0 text-brand-700" />
              <span>Segredos cifrados em repouso:</span>
              <SeloSegredo rotulo="App Secret" configurado={canal.metaAppSecretConfigurado} />
              <SeloSegredo rotulo="Verify Token" configurado={canal.metaVerifyTokenConfigurado} />
              <SeloSegredo rotulo="Token da Página" configurado={canal.metaPageAccessTokenConfigurado} />
            </div>

            <FormField label="ID da página do Facebook (Page ID)">
              <Input
                placeholder="1029384756"
                value={metaPageId}
                disabled={somenteLeitura}
                onChange={(e) => setMetaPageId(e.target.value)}
              />
            </FormField>
            <FormField
              label="Verify Token do webhook"
              hint={canal.metaVerifyTokenConfigurado ? 'Já configurado. Preencha só para substituir.' : 'Token para o handshake hub.verify_token.'}
            >
              <Input
                type="password"
                autoComplete="new-password"
                placeholder={canal.metaVerifyTokenConfigurado ? '••••••••••••' : 'Digite o verify token'}
                value={metaVerifyToken}
                disabled={somenteLeitura}
                onChange={(e) => setMetaVerifyToken(e.target.value)}
              />
            </FormField>
            <FormField
              label="App Secret do Meta"
              hint={canal.metaAppSecretConfigurado ? 'Já configurado. Preencha só para substituir.' : 'Valida a assinatura X-Hub-Signature-256.'}
            >
              <Input
                type="password"
                autoComplete="new-password"
                placeholder={canal.metaAppSecretConfigurado ? '••••••••••••' : 'Digite o App Secret'}
                value={metaAppSecret}
                disabled={somenteLeitura}
                onChange={(e) => setMetaAppSecret(e.target.value)}
              />
            </FormField>
            <FormField
              label="Token de acesso da página (Graph API)"
              hint={canal.metaPageAccessTokenConfigurado ? 'Já configurado. Preencha só para substituir.' : 'Lê os campos do formulário instantâneo no job 4.1.'}
            >
              <Input
                type="password"
                autoComplete="new-password"
                placeholder={canal.metaPageAccessTokenConfigurado ? '••••••••••••' : 'EAA...'}
                value={metaPageAccessToken}
                disabled={somenteLeitura}
                onChange={(e) => setMetaPageAccessToken(e.target.value)}
              />
            </FormField>

            {canal.webhookUrl && (
              <div className="md:col-span-2 border-t border-borda pt-3">
                <p className="mb-1 text-xs font-medium text-texto-2">URL do webhook deste tenant:</p>
                <div className="flex items-center gap-1.5">
                  <code className="flex-1 truncate rounded-lg bg-brand-950 px-3 py-2 font-mono text-xs text-brand-300">
                    {canal.webhookUrl}
                  </code>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      navigator.clipboard?.writeText(canal.webhookUrl!).then(() => {
                        setCopiado(true);
                        window.setTimeout(() => setCopiado(false), 1500);
                      });
                    }}
                  >
                    {copiado ? <Check aria-hidden className="h-3.5 w-3.5 text-brand-700" /> : <Copy aria-hidden className="h-3.5 w-3.5" />}
                    {copiado ? 'Copiado' : 'Copiar'}
                  </Button>
                </div>
              </div>
            )}
          </FormSection>
        )}

        <div className="flex justify-end border-t border-borda pt-4">
          {somenteLeitura ? (
            <SomenteLeitura />
          ) : (
            <Button type="submit" size="sm" disabled={salvando}>
              {salvo && <Check aria-hidden className="h-3.5 w-3.5 shrink-0" />}
              {salvando ? 'Salvando…' : salvo ? 'Salvo' : 'Salvar canal'}
            </Button>
          )}
        </div>
      </form>
    </section>
  );
}

function SeloSegredo({ rotulo, configurado }: { rotulo: string; configurado: boolean }) {
  return (
    <Badge variant={configurado ? 'green' : 'gray'}>
      {configurado && <ShieldCheck aria-hidden className="h-3 w-3" />}
      {rotulo}: {configurado ? 'configurado' : 'não configurado'}
    </Badge>
  );
}
