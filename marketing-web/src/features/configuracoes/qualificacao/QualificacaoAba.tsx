import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { AlertCircle, Award, Check, Pencil, Plus, Trash2, Users } from 'lucide-react';
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
  statusBadge,
} from '@/components/ui';
import { NOME_FONTE_REGRA, NOME_METRICA, dataHora } from '@/features/leads/formatos';
import {
  alterarRegraPontuacao,
  criarRegraPontuacao,
  excluirRegraPontuacao,
  listarFormulariosQualificacao,
  listarRegrasPontuacao,
  listarVendedoresRodizio,
  obterQualificacao,
  salvarQualificacao,
  salvarVendedoresRodizio,
  type ConfiguracaoQualificacao,
  type FonteRegra,
  type FormularioQualificacao,
  type PedidoRegraPontuacao,
  type RegraPontuacao,
  type VendedorRodizio,
} from '../api';

const DIAS_SEMANA = [
  { valor: 1, rotulo: 'Seg' },
  { valor: 2, rotulo: 'Ter' },
  { valor: 3, rotulo: 'Qua' },
  { valor: 4, rotulo: 'Qui' },
  { valor: 5, rotulo: 'Sex' },
  { valor: 6, rotulo: 'Sáb' },
  { valor: 7, rotulo: 'Dom' },
];

/**
 * Aba "Qualificação e distribuição" (W4 e R5, norteador §9.3 e §9.4): faixas com prévia de leads,
 * regras de pontuação (campo do formulário do Landing, pergunta do Meta, UTM e comportamento do
 * lead 0) e rodízio de vendedores.
 */
export function QualificacaoAba() {
  const [qualificacao, setQualificacao] = useState<ConfiguracaoQualificacao | null>(null);
  const [regras, setRegras] = useState<RegraPontuacao[] | null>(null);
  const [formularios, setFormularios] = useState<FormularioQualificacao[]>([]);
  const [vendedores, setVendedores] = useState<VendedorRodizio[] | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [regraEmEdicao, setRegraEmEdicao] = useState<RegraPontuacao | null | undefined>(undefined);
  const [regraAExcluir, setRegraAExcluir] = useState<RegraPontuacao | null>(null);

  const carregar = useCallback(() => {
    Promise.all([
      obterQualificacao().then(setQualificacao),
      listarRegrasPontuacao().then(setRegras),
      listarFormulariosQualificacao().then(setFormularios).catch(() => setFormularios([])),
      listarVendedoresRodizio().then(setVendedores),
    ]).catch((falha) =>
      setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar a qualificação.'),
    );
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  if (erro) {
    return <EmptyState icone={AlertCircle} erro titulo="Qualificação indisponível" descricao={erro} />;
  }
  if (!qualificacao || !regras || !vendedores) return <Spinner />;

  return (
    <div className="space-y-5">
      <div className="grid gap-5 xl:grid-cols-2">
        <CartaoFaixasERodizio qualificacao={qualificacao} onSalvo={setQualificacao} />
        <CartaoEscalaVendedores vendedores={vendedores} onSalvo={setVendedores} />
      </div>

      {/* Regras de pontuação */}
      <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-borda px-5 py-3.5">
          <div>
            <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
              <Award aria-hidden className="h-4 w-4 text-brand-700" />
              Regras de pontuação (Lead Scoring)
            </h2>
            <p className="mt-0.5 text-xs text-texto-3">
              Cada critério conta uma vez por lead, valendo a regra de maior pontuação. Inclui as regras de comportamento do Lead 0 (em roxo).
            </p>
          </div>
          <Button size="sm" onClick={() => setRegraEmEdicao(null)}>
            <Plus aria-hidden className="h-3.5 w-3.5" />
            Nova regra
          </Button>
        </header>

        {regras.length === 0 ? (
          <EmptyState
            icone={Award}
            titulo="Nenhuma regra de pontuação"
            descricao="Crie regras por campo de formulário do Landing, pergunta do Meta, UTM ou comportamento na página."
          />
        ) : (
          <ul className="divide-y divide-borda">
            {regras.map((regra) => (
              <li key={regra.id} className="flex flex-wrap items-center justify-between gap-3 px-5 py-3">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="text-sm font-medium text-texto">{regra.criterio}</p>
                    <Badge variant={regra.fonte === 'comportamento' ? 'purple' : 'blue'}>
                      {NOME_FONTE_REGRA[regra.fonte] ?? regra.fonte}
                    </Badge>
                    {regra.campoComportamento && (
                      <Badge variant="purple">
                        {NOME_METRICA[regra.campoComportamento] ?? regra.campoComportamento}
                      </Badge>
                    )}
                    {!regra.ativo && statusBadge('inativo')}
                  </div>
                  <p className="mt-0.5 truncate text-xs text-texto-3">
                    Condição: {resumirValoresRegra(regra, formularios)}
                  </p>
                </div>
                <Badge variant={regra.fonte === 'comportamento' ? 'purple' : 'green'}>+{regra.pontos} pts</Badge>
                <div className="flex items-center gap-1">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Editar ${regra.criterio}`}
                    onClick={() => setRegraEmEdicao(regra)}
                  >
                    <Pencil aria-hidden className="h-3.5 w-3.5" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={`Excluir ${regra.criterio}`}
                    onClick={() => setRegraAExcluir(regra)}
                  >
                    <Trash2 aria-hidden className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {regraEmEdicao !== undefined && (
        <RegraModal
          aberto
          regra={regraEmEdicao}
          formularios={formularios}
          onClose={() => setRegraEmEdicao(undefined)}
          onSalvo={() => {
            setRegraEmEdicao(undefined);
            carregar();
          }}
        />
      )}

      <Modal
        open={!!regraAExcluir}
        onClose={() => setRegraAExcluir(null)}
        size="sm"
        title="Excluir regra de pontuação?"
        footer={
          <>
            <Button variant="outline" onClick={() => setRegraAExcluir(null)}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={() =>
                regraAExcluir &&
                excluirRegraPontuacao(regraAExcluir.id).then(() => {
                  setRegraAExcluir(null);
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
          A regra <strong>{regraAExcluir?.criterio}</strong> deixará de somar pontos nos próximos cálculos.
        </p>
      </Modal>
    </div>
  );
}

function resumirValoresRegra(regra: RegraPontuacao, formularios: FormularioQualificacao[]) {
  if (regra.fonte === 'campo_formulario') {
    const form = formularios.find((f) => f.id === regra.formularioId);
    const campo = form?.campos.find((c) => c.id === regra.campoId);
    const alvo = campo ? `${form?.nome} › ${campo.rotulo}` : `Campo ${regra.campoId?.slice(0, 8)}`;
    return `${alvo} = ${regra.valores.map(String).join(', ')}`;
  }
  if (regra.fonte === 'campo_meta') {
    return `${regra.campoMeta} = ${regra.valores.map(String).join(', ')}`;
  }
  return JSON.stringify(regra.valores);
}

function CartaoFaixasERodizio({
  qualificacao,
  onSalvo,
}: {
  qualificacao: ConfiguracaoQualificacao;
  onSalvo: (q: ConfiguracaoQualificacao) => void;
}) {
  const [quente, setQuente] = useState(String(qualificacao.limiteMqlQuente));
  const [morno, setMorno] = useState(String(qualificacao.limiteMqlMorno));
  const [rodizioAtivo, setRodizioAtivo] = useState(qualificacao.rodizioAtivo);
  const [inicio, setInicio] = useState(qualificacao.horarioComercialInicio);
  const [fim, setFim] = useState(qualificacao.horarioComercialFim);
  const [dias, setDias] = useState<number[]>(qualificacao.diasAtendimento);
  const [transbordo, setTransbordo] = useState(
    qualificacao.minutosTransbordo !== null ? String(qualificacao.minutosTransbordo) : '15',
  );
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  function alternarDia(dia: number) {
    setDias((atuais) => (atuais.includes(dia) ? atuais.filter((d) => d !== dia) : [...atuais, dia].sort()));
  }

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);
    try {
      const atualizado = await salvarQualificacao({
        limiteMqlQuente: Number(quente),
        limiteMqlMorno: Number(morno),
        rodizioAtivo,
        horarioComercialInicio: inicio,
        horarioComercialFim: fim,
        diasAtendimento: dias,
        minutosTransbordo: transbordo.trim() === '' ? null : Number(transbordo),
      });
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
      <header className="flex items-center justify-between border-b border-borda px-5 py-3.5">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
          <Award aria-hidden className="h-4 w-4 text-brand-700" />
          Faixas de qualificação e horário do rodízio
        </h2>
      </header>
      <form onSubmit={salvar} className="space-y-5 p-5" noValidate>
        {erro && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <FormSection number={1} title="Limites de pontuação (MQL)">
          <FormField label="MQL quente a partir de (pontos)" required>
            <Input type="number" min={1} value={quente} onChange={(e) => setQuente(e.target.value)} />
          </FormField>
          <FormField label="MQL morno a partir de (pontos)" required>
            <Input type="number" min={0} value={morno} onChange={(e) => setMorno(e.target.value)} />
          </FormField>
          <div className="md:col-span-2 flex flex-wrap gap-2 rounded-lg bg-superficie-2 p-3 text-xs">
            <span className="font-medium text-texto-2">Prévia nos leads atuais:</span>
            <Badge variant="orange">MQL Quente: {qualificacao.previaFaixas.mqlQuente}</Badge>
            <Badge variant="yellow">MQL Morno: {qualificacao.previaFaixas.mqlMorno}</Badge>
            <Badge variant="blue">Nutrição: {qualificacao.previaFaixas.nutricao}</Badge>
          </div>
        </FormSection>

        <FormSection number={2} title="Horário comercial e rodízio">
          <FormField label="Rodízio automático" colSpan={2}>
            <label className="flex items-center gap-2 text-sm text-texto">
              <input
                type="checkbox"
                checked={rodizioAtivo}
                onChange={(e) => setRodizioAtivo(e.target.checked)}
                className="h-4 w-4 rounded border-borda-forte"
              />
              Atribuir vendedor automaticamente aos leads MQL (quente e morno)
            </label>
          </FormField>
          <FormField label="Início do expediente" required>
            <Input type="time" value={inicio} onChange={(e) => setInicio(e.target.value)} />
          </FormField>
          <FormField label="Fim do expediente" required>
            <Input type="time" value={fim} onChange={(e) => setFim(e.target.value)} />
          </FormField>
          <FormField label="Dias de atendimento" colSpan={2}>
            <div className="flex flex-wrap gap-1.5">
              {DIAS_SEMANA.map((d) => {
                const marcado = dias.includes(d.valor);
                return (
                  <button
                    key={d.valor}
                    type="button"
                    onClick={() => alternarDia(d.valor)}
                    className={`rounded-lg border px-2.5 py-1 text-xs font-medium transition-colors ${
                      marcado
                        ? 'border-brand-700 bg-brand-800 text-white'
                        : 'border-borda-forte bg-superficie text-texto-2 hover:bg-superficie-2'
                    }`}
                  >
                    {d.rotulo}
                  </button>
                );
              })}
            </div>
          </FormField>
          <FormField label="Minutos para transbordo" colSpan={2}>
            <Input type="number" min={1} value={transbordo} onChange={(e) => setTransbordo(e.target.value)} />
          </FormField>
        </FormSection>

        <div className="flex justify-end border-t border-borda pt-3">
          <Button type="submit" size="sm" disabled={salvando}>
            {salvo && <Check aria-hidden className="h-3.5 w-3.5" />}
            {salvando ? 'Salvando…' : salvo ? 'Salvo' : 'Salvar faixas e horário'}
          </Button>
        </div>
      </form>
    </section>
  );
}

function CartaoEscalaVendedores({
  vendedores,
  onSalvo,
}: {
  vendedores: VendedorRodizio[];
  onSalvo: (vendedores: VendedorRodizio[]) => void;
}) {
  const [lista, setLista] = useState<{ vendedorId: string; ativo: boolean; ultimaAtribuicaoEm: string | null }[]>(
    vendedores.map((v) => ({ vendedorId: v.vendedorId, ativo: v.ativo, ultimaAtribuicaoEm: v.ultimaAtribuicaoEm })),
  );
  const [novoUuid, setNovoUuid] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  function adicionar() {
    const id = novoUuid.trim();
    if (!id) return;
    if (lista.some((v) => v.vendedorId.toLowerCase() === id.toLowerCase())) {
      setErro('Este vendedor já está na escala.');
      return;
    }
    setErro(null);
    setLista((atual) => [...atual, { vendedorId: id, ativo: true, ultimaAtribuicaoEm: null }]);
    setNovoUuid('');
  }

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      const salvos = await salvarVendedoresRodizio(
        lista.map((v) => ({ vendedorId: v.vendedorId, ativo: v.ativo })),
      );
      onSalvo(salvos);
      setLista(salvos.map((v) => ({ vendedorId: v.vendedorId, ativo: v.ativo, ultimaAtribuicaoEm: v.ultimaAtribuicaoEm })));
      setSalvo(true);
      window.setTimeout(() => setSalvo(false), 2000);
    } catch (falha) {
      setErro(falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar a escala.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <section className="flex flex-col overflow-hidden rounded-xl border border-borda bg-superficie">
      <header className="flex items-center justify-between border-b border-borda px-5 py-3.5">
        <div>
          <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
            <Users aria-hidden className="h-4 w-4 text-brand-700" />
            Escala de vendedores do rodízio
          </h2>
          <p className="mt-0.5 text-xs text-texto-3">
            O rodízio escolhe o vendedor ativo com a atribuição mais antiga.
          </p>
        </div>
      </header>

      <div className="flex-1 space-y-4 p-5">
        {erro && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <div className="flex items-end gap-2">
          <div className="flex-1">
            <FormField label="Adicionar vendedor (UUID do identity)">
              <Input
                placeholder="2d4f6a8c-0000-4000-8000-000000000001"
                value={novoUuid}
                onChange={(e) => setNovoUuid(e.target.value)}
              />
            </FormField>
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={adicionar} className="h-9">
            <Plus aria-hidden className="h-3.5 w-3.5" />
            Adicionar
          </Button>
        </div>

        {lista.length === 0 ? (
          <EmptyState
            icone={Users}
            titulo="Nenhum vendedor na escala"
            descricao="Adicione o UUID dos vendedores do identity para ativar a distribuição automática."
          />
        ) : (
          <ul className="divide-y divide-borda rounded-lg border border-borda">
            {lista.map((item) => (
              <li key={item.vendedorId} className="flex items-center justify-between gap-2 px-3.5 py-2.5 text-xs">
                <div className="min-w-0">
                  <p className="truncate font-mono text-texto">{item.vendedorId}</p>
                  <p className="text-texto-3">
                    Última atribuição: {item.ultimaAtribuicaoEm ? dataHora(item.ultimaAtribuicaoEm) : 'nunca recebeu'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <label className="flex items-center gap-1.5 text-texto-2">
                    <input
                      type="checkbox"
                      checked={item.ativo}
                      onChange={(e) =>
                        setLista((atual) =>
                          atual.map((v) =>
                            v.vendedorId === item.vendedorId ? { ...v, ativo: e.target.checked } : v,
                          ),
                        )
                      }
                    />
                    Ativo
                  </label>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    aria-label={`Remover ${item.vendedorId}`}
                    onClick={() => setLista((atual) => atual.filter((v) => v.vendedorId !== item.vendedorId))}
                  >
                    <Trash2 aria-hidden className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex justify-end border-t border-borda px-5 py-3.5">
        <Button type="button" size="sm" onClick={salvar} disabled={salvando}>
          {salvo && <Check aria-hidden className="h-3.5 w-3.5" />}
          {salvando ? 'Salvando…' : salvo ? 'Escala salva' : 'Salvar escala'}
        </Button>
      </div>
    </section>
  );
}

function RegraModal({
  aberto,
  regra,
  formularios,
  onClose,
  onSalvo,
}: {
  aberto: boolean;
  regra: RegraPontuacao | null;
  formularios: FormularioQualificacao[];
  onClose: () => void;
  onSalvo: () => void;
}) {
  const [criterio, setCriterio] = useState(regra?.criterio ?? '');
  const [fonte, setFonte] = useState<FonteRegra>(regra?.fonte ?? 'comportamento');
  const [formularioId, setFormularioId] = useState(regra?.formularioId ?? formularios[0]?.id ?? '');
  const [campoId, setCampoId] = useState(regra?.campoId ?? '');
  const [campoMeta, setCampoMeta] = useState(regra?.campoMeta ?? '');
  const [campoComportamento, setCampoComportamento] = useState(regra?.campoComportamento ?? 'tempo_na_pagina');
  const [valoresTexto, setValoresTexto] = useState(() => inicializarValoresTexto(regra));
  const [pontos, setPontos] = useState(String(regra?.pontos ?? 20));
  const [ativo, setAtivo] = useState(regra?.ativo ?? true);
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const formularioEscolhido = formularios.find((f) => f.id === formularioId);

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);
    try {
      const valores = montarValores(fonte, campoComportamento, valoresTexto);
      const pedido: PedidoRegraPontuacao = {
        criterio,
        fonte,
        formularioId: fonte === 'campo_formulario' ? formularioId || null : null,
        campoId: fonte === 'campo_formulario' ? campoId || null : null,
        campoMeta: fonte === 'campo_meta' ? campoMeta || null : null,
        campoComportamento: fonte === 'comportamento' ? campoComportamento : null,
        valores,
        pontos: Number(pontos),
        ativo,
      };
      if (regra) {
        await alterarRegraPontuacao(regra.id, pedido);
      } else {
        await criarRegraPontuacao(pedido);
      }
      onSalvo();
    } catch (falha) {
      setErro(falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar a regra.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <Modal
      open={aberto}
      onClose={onClose}
      title={regra ? 'Editar regra de pontuação' : 'Nova regra de pontuação'}
      subtitle="Defina o critério, a fonte do dado e quantos pontos a regra soma quando bate."
      footer={
        <>
          <Button variant="outline" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="form-regra" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar regra'}
          </Button>
        </>
      }
    >
      <form id="form-regra" onSubmit={salvar} className="space-y-4" noValidate>
        {erro && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}

        <FormSection number={1} title="Critério e fonte">
          <FormField label="Nome do critério" required hint="Ex.: Cargo, Orçamento, Engajamento na página">
            <Input data-autofocus value={criterio} onChange={(e) => setCriterio(e.target.value)} />
          </FormField>
          <FormField label="Fonte da regra" required>
            <Select value={fonte} onChange={(e) => setFonte(e.target.value as FonteRegra)}>
              <option value="comportamento">Comportamento na página (Lead 0)</option>
              <option value="campo_formulario">Campo de formulário do Landing</option>
              <option value="campo_meta">Pergunta do formulário Meta Ads</option>
              <option value="utm">Origem do tráfego (UTM)</option>
            </Select>
          </FormField>

          {fonte === 'campo_formulario' && (
            <>
              <FormField label="Formulário do Landing" required>
                {formularios.length > 0 ? (
                  <Select value={formularioId} onChange={(e) => setFormularioId(e.target.value)}>
                    <option value="">Selecione…</option>
                    {formularios.map((f) => (
                      <option key={f.id} value={f.id}>
                        {f.nome}
                      </option>
                    ))}
                  </Select>
                ) : (
                  <Input
                    placeholder="UUID do formulário no Landing"
                    value={formularioId}
                    onChange={(e) => setFormularioId(e.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Campo do formulário" required>
                {formularioEscolhido && formularioEscolhido.campos.length > 0 ? (
                  <Select value={campoId} onChange={(e) => setCampoId(e.target.value)}>
                    <option value="">Selecione…</option>
                    {formularioEscolhido.campos.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.rotulo} ({c.tipo})
                      </option>
                    ))}
                  </Select>
                ) : (
                  <Input
                    placeholder="UUID do campo no formulário"
                    value={campoId}
                    onChange={(e) => setCampoId(e.target.value)}
                  />
                )}
              </FormField>
            </>
          )}

          {fonte === 'campo_meta' && (
            <FormField label="Nome da pergunta no formulário do Meta" required colSpan={2}>
              <Input
                placeholder="qual_o_seu_cargo"
                value={campoMeta}
                onChange={(e) => setCampoMeta(e.target.value)}
              />
            </FormField>
          )}

          {fonte === 'comportamento' && (
            <FormField label="Métrica de comportamento da visita" required colSpan={2}>
              <Select value={campoComportamento} onChange={(e) => setCampoComportamento(e.target.value)}>
                <option value="tempo_na_pagina">Tempo na página (segundos mínimos)</option>
                <option value="profundidade_rolagem">Profundidade de rolagem (% mínimo)</option>
                <option value="pagina_visitada">Página ou seção visitada (trecho)</option>
                <option value="elemento_clicado">Botão ou elemento clicado</option>
                <option value="tipo_dispositivo">Tipo de dispositivo (smartphone, tablet, desktop)</option>
              </Select>
            </FormField>
          )}
        </FormSection>

        <FormSection number={2} title="Condição e pontos">
          <FormField
            label={
              fonte === 'comportamento' &&
              (campoComportamento === 'tempo_na_pagina' || campoComportamento === 'profundidade_rolagem')
                ? 'Valor mínimo'
                : fonte === 'utm'
                  ? 'Condições UTM em JSON (ex.: [{"source":"google","medium":"cpc"}])'
                  : 'Valores aceitos (separados por vírgula)'
            }
            required
            colSpan={2}
          >
            <Input
              value={valoresTexto}
              onChange={(e) => setValoresTexto(e.target.value)}
              placeholder={
                fonte === 'comportamento' &&
                (campoComportamento === 'tempo_na_pagina' || campoComportamento === 'profundidade_rolagem')
                  ? '30'
                  : fonte === 'utm'
                    ? '[{"source":"google","medium":"cpc"}]'
                    : 'Decisor, Sócio, Gerente'
              }
            />
          </FormField>
          <FormField label="Pontos" required>
            <Input type="number" min={0} value={pontos} onChange={(e) => setPontos(e.target.value)} />
          </FormField>
          <FormField label="Status">
            <label className="mt-2 flex items-center gap-2 text-sm text-texto">
              <input
                type="checkbox"
                checked={ativo}
                onChange={(e) => setAtivo(e.target.checked)}
                className="h-4 w-4 rounded border-borda-forte"
              />
              Regra ativa
            </label>
          </FormField>
        </FormSection>
      </form>
    </Modal>
  );
}

function inicializarValoresTexto(regra: RegraPontuacao | null): string {
  if (!regra) return '30';
  if (regra.fonte === 'utm') {
    return JSON.stringify(regra.valores);
  }
  if (
    regra.fonte === 'comportamento' &&
    (regra.campoComportamento === 'tempo_na_pagina' || regra.campoComportamento === 'profundidade_rolagem')
  ) {
    const primeiro = regra.valores[0] as { minimo?: number } | undefined;
    return primeiro && typeof primeiro.minimo === 'number' ? String(primeiro.minimo) : '30';
  }
  return regra.valores.map(String).join(', ');
}

function montarValores(fonte: FonteRegra, metrica: string, texto: string): unknown[] {
  if (fonte === 'utm') {
    try {
      const parsed = JSON.parse(texto);
      return Array.isArray(parsed) ? parsed : [parsed];
    } catch {
      return texto
        .split(',')
        .map((v) => v.trim())
        .filter(Boolean)
        .map((source) => ({ source }));
    }
  }
  if (
    fonte === 'comportamento' &&
    (metrica === 'tempo_na_pagina' || metrica === 'profundidade_rolagem')
  ) {
    return [{ minimo: Number(texto) }];
  }
  return texto
    .split(',')
    .map((v) => v.trim())
    .filter(Boolean);
}
