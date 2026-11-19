import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { AlertCircle, Eye, Mail, Pencil, Plus, Sparkles, Trash2 } from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import {
  Badge,
  Button,
  EmptyState,
  FormField,
  FormSection,
  Input,
  Modal,
  Select,
  Spinner,
  Textarea,
  statusBadge,
} from '@/components/ui';
import {
  alterarModeloMensagem,
  criarModeloMensagem,
  excluirModeloMensagem,
  listarModelosMensagem,
  type ModeloMensagem,
  type PedidoModeloMensagem,
} from '../api';
import { usarPermissao } from '@/plataforma';

/**
 * Aba "Modelos de mensagem" (W4, norteador §9.4): modelo de boas-vindas no topo (um por tenant,
 * sempre ativo e sem exclusão) e a cadência de modelos de aquecimento das etapas frias (0 e 1), com
 * pré-visualização das variáveis `{{nome}}`, `{{origem}}` e do rodapé de descadastro.
 */
export function ModelosAba() {
  const podeCriar = usarPermissao('marketing.template.criar');
  const podeEditar = usarPermissao('marketing.template.editar');
  const podeExcluir = usarPermissao('marketing.template.excluir');
  const [modelos, setModelos] = useState<ModeloMensagem[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [emEdicao, setEmEdicao] = useState<{ modelo: ModeloMensagem | null; tipo: 'boas_vindas' | 'aquecimento' } | null>(null);
  const [aExcluir, setAExcluir] = useState<ModeloMensagem | null>(null);

  const carregar = useCallback(() => {
    listarModelosMensagem()
      .then((pagina) => setModelos(pagina.itens))
      .catch((falha) =>
        setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar os modelos de mensagem.'),
      );
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  if (erro) {
    return <EmptyState icone={AlertCircle} erro titulo="Modelos indisponíveis" descricao={erro} />;
  }
  if (!modelos) return <Spinner />;

  const boasVindas = modelos.find((m) => m.tipo === 'boas_vindas') ?? null;
  const aquecimento = modelos.filter((m) => m.tipo === 'aquecimento');

  return (
    <div className="space-y-5">
      {/* Modelo de boas-vindas no topo */}
      <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-5 py-3.5">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
              <Sparkles aria-hidden className="h-4 w-4 text-brand-700" />
              Confirmação de boas-vindas (Job 4.5)
              <Badge variant="green">sempre ativo</Badge>
            </h2>
            <p className="mt-0.5 text-xs text-texto-3">
              Enviado uma única vez nos primeiros minutos para quem envia o formulário (etapa 3).
            </p>
          </div>
          {boasVindas && podeEditar && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setEmEdicao({ modelo: boasVindas, tipo: 'boas_vindas' })}
            >
              <Pencil aria-hidden className="h-3.5 w-3.5" />
              Editar boas-vindas
            </Button>
          )}
        </header>
        {boasVindas && (
          <div className="space-y-2 p-5 text-sm">
            <p className="font-medium text-titulo">Assunto: {boasVindas.assunto}</p>
            <pre className="whitespace-pre-wrap rounded-lg bg-superficie-2 p-3 font-sans text-xs text-texto-2">
              {boasVindas.corpo}
            </pre>
          </div>
        )}
      </section>

      {/* Cadência de aquecimento */}
      <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-5 py-3.5">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
              <Mail aria-hidden className="h-4 w-4 text-brand-700" />
              Cadência de aquecimento (Job 4.2)
            </h2>
            <p className="mt-0.5 text-xs text-texto-3">
              Sequência de e-mails para leads nas etapas frias (0 · Meta Ads e 1 · captura do chatbot) com consentimento.
            </p>
          </div>
          {podeCriar && (
            <Button
              size="sm"
              onClick={() => setEmEdicao({ modelo: null, tipo: 'aquecimento' })}
            >
              <Plus aria-hidden className="h-3.5 w-3.5" />
              Novo modelo de aquecimento
            </Button>
          )}
        </header>

        {aquecimento.length === 0 ? (
          <EmptyState
            icone={Mail}
            titulo="Nenhum modelo na cadência de aquecimento"
            descricao="Adicione os toques da sequência (1º toque, 2º toque…) para reengajar leads frios."
          />
        ) : (
          <ul className="divide-y divide-borda">
            {aquecimento.map((modelo) => (
              <li key={modelo.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="indigo">{modelo.ordemSequencia}º toque</Badge>
                    <p className="text-sm font-medium text-texto">{modelo.nome}</p>
                    <Badge variant="blue">
                      {modelo.etapaAlvo === null ? 'Etapas 0 e 1' : `Etapa ${modelo.etapaAlvo}`}
                    </Badge>
                    <Badge variant="gray">
                      {modelo.diasAposAnterior === 0
                        ? 'no mesmo dia'
                        : `${modelo.diasAposAnterior} dia(s) após o anterior`}
                    </Badge>
                    {statusBadge(modelo.ativo ? 'ativo' : 'inativo')}
                  </div>
                  <p className="mt-1 truncate text-xs text-texto-3">Assunto: {modelo.assunto}</p>
                </div>
                <div className="flex items-center gap-1">
                  {podeEditar && (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Editar ${modelo.nome}`}
                      onClick={() => setEmEdicao({ modelo, tipo: 'aquecimento' })}
                    >
                      <Pencil aria-hidden className="h-3.5 w-3.5" />
                    </Button>
                  )}
                  {podeExcluir && (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Excluir ${modelo.nome}`}
                      onClick={() => setAExcluir(modelo)}
                    >
                      <Trash2 aria-hidden className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {emEdicao && (
        <ModeloModal
          aberto={!!emEdicao}
          modelo={emEdicao.modelo}
          tipo={emEdicao.tipo}
          proximaOrdem={aquecimento.length + 1}
          onClose={() => setEmEdicao(null)}
          onSalvo={() => {
            setEmEdicao(null);
            carregar();
          }}
        />
      )}

      <Modal
        open={!!aExcluir}
        onClose={() => setAExcluir(null)}
        size="sm"
        title="Excluir modelo de aquecimento?"
        footer={
          <>
            <Button variant="outline" onClick={() => setAExcluir(null)}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={() =>
                aExcluir &&
                excluirModeloMensagem(aExcluir.id).then(() => {
                  setAExcluir(null);
                  carregar();
                })
              }
            >
              Excluir
            </Button>
          </>
        }
      >
        <p className="text-sm text-texto-2">
          O modelo <strong>{aExcluir?.nome}</strong> deixará de ser enviado na cadência de aquecimento.
        </p>
      </Modal>
    </div>
  );
}

function renderizarPrevia(texto: string) {
  return texto
    .replaceAll('{{nome}}', 'Maria Souza')
    .replaceAll('{{origem}}', 'google');
}

function ModeloModal({
  aberto,
  modelo,
  tipo,
  proximaOrdem,
  onClose,
  onSalvo,
}: {
  aberto: boolean;
  modelo: ModeloMensagem | null;
  tipo: 'boas_vindas' | 'aquecimento';
  proximaOrdem: number;
  onClose: () => void;
  onSalvo: () => void;
}) {
  const isBoasVindas = tipo === 'boas_vindas';
  const [nome, setNome] = useState(modelo?.nome ?? '');
  const [etapaAlvo, setEtapaAlvo] = useState<string>(
    modelo?.etapaAlvo !== null && modelo?.etapaAlvo !== undefined ? String(modelo.etapaAlvo) : '',
  );
  const [ordemSequencia, setOrdemSequencia] = useState(String(modelo?.ordemSequencia ?? proximaOrdem));
  const [diasAposAnterior, setDiasAposAnterior] = useState(String(modelo?.diasAposAnterior ?? 3));
  const [assunto, setAssunto] = useState(modelo?.assunto ?? '');
  const [corpo, setCorpo] = useState(modelo?.corpo ?? '');
  const [ativo, setAtivo] = useState(modelo?.ativo ?? true);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);
    try {
      const pedido: PedidoModeloMensagem = {
        tipo,
        nome,
        etapaAlvo: isBoasVindas || etapaAlvo === '' ? null : Number(etapaAlvo),
        ordemSequencia: isBoasVindas ? null : Number(ordemSequencia),
        diasAposAnterior: isBoasVindas ? null : Number(diasAposAnterior),
        assunto,
        corpo,
        ativo: isBoasVindas ? true : ativo,
      };
      if (modelo) {
        await alterarModeloMensagem(modelo.id, pedido);
      } else {
        await criarModeloMensagem(pedido);
      }
      onSalvo();
    } catch (falha) {
      setErro(falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <Modal
      open={aberto}
      onClose={onClose}
      title={
        isBoasVindas
          ? 'Editar modelo de boas-vindas'
          : modelo
            ? 'Editar modelo de aquecimento'
            : 'Novo modelo de aquecimento'
      }
      subtitle="Use as variáveis {{nome}} e {{origem}}. O link de descadastro é incluído automaticamente em todo envio."
      footer={
        <>
          <Button variant="outline" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="form-modelo" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar modelo'}
          </Button>
        </>
      }
    >
      <form id="form-modelo" onSubmit={salvar} className="space-y-5" noValidate>
        {erro && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <FormSection number={1} title="Identificação e cadência">
          <FormField label="Nome do modelo" required colSpan={isBoasVindas ? 2 : 1}>
            <Input
              data-autofocus
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              placeholder="Reengajamento 3 dias"
            />
          </FormField>

          {!isBoasVindas && (
            <>
              <FormField label="Etapa fria alvo">
                <Select value={etapaAlvo} onChange={(e) => setEtapaAlvo(e.target.value)}>
                  <option value="">Qualquer etapa fria (0 e 1)</option>
                  <option value="0">Etapa 0 (Meta Ads)</option>
                  <option value="1">Etapa 1 (Captura do chatbot)</option>
                </Select>
              </FormField>
              <FormField label="Posição na sequência (º toque)" required>
                <Input
                  type="number"
                  min={1}
                  value={ordemSequencia}
                  onChange={(e) => setOrdemSequencia(e.target.value)}
                />
              </FormField>
              <FormField label="Dias após o toque anterior" required>
                <Input
                  type="number"
                  min={0}
                  value={diasAposAnterior}
                  onChange={(e) => setDiasAposAnterior(e.target.value)}
                />
              </FormField>
              <FormField label="Status na cadência" colSpan={2}>
                <label className="flex items-center gap-2 text-sm text-texto">
                  <input
                    type="checkbox"
                    checked={ativo}
                    onChange={(e) => setAtivo(e.target.checked)}
                    className="h-4 w-4 rounded border-borda-forte"
                  />
                  Modelo ativo (inativos não entram no job 4.2)
                </label>
              </FormField>
            </>
          )}
        </FormSection>

        <FormSection number={2} title="Conteúdo do e-mail">
          <FormField label="Assunto" required colSpan={2} hint="Aceita {{nome}} e {{origem}}.">
            <Input
              value={assunto}
              onChange={(e) => setAssunto(e.target.value)}
              placeholder="Olá {{nome}}, recebemos o seu contato!"
            />
          </FormField>
          <FormField label="Corpo da mensagem" required colSpan={2}>
            <Textarea
              rows={5}
              value={corpo}
              onChange={(e) => setCorpo(e.target.value)}
              placeholder="Olá, {{nome}}! Vimos seu interesse vindo de {{origem}}..."
            />
          </FormField>
        </FormSection>

        <div className="rounded-xl border border-borda bg-superficie-2 p-4">
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-titulo">
            <Eye aria-hidden className="h-3.5 w-3.5 text-brand-700" />
            Pré-visualização (exemplo: Maria Souza · google)
          </p>
          <p className="text-xs font-medium text-texto">
            Assunto: {renderizarPrevia(assunto || '(sem assunto)')}
          </p>
          <pre className="mt-2 whitespace-pre-wrap rounded-lg border border-borda bg-superficie p-3 font-sans text-xs text-texto-2">
            {`${renderizarPrevia(corpo || '(sem corpo)')}\n\n---\nPara não receber novas comunicações, acesse: http://localhost:8087/public/marketing/descadastro/exemplo-token`}
          </pre>
        </div>
      </form>
    </Modal>
  );
}
