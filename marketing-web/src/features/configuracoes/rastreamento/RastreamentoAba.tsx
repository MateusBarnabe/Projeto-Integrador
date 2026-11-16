import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import {
  AlertCircle,
  Check,
  Copy,
  ExternalLink,
  Link2,
  MousePointerClick,
  Pencil,
  Plus,
  Radar,
  Timer,
  Trash2,
} from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import { Badge, Button, EmptyState, FormField, Input, Modal, Spinner, statusBadge } from '@/components/ui';
import { Dispositivo, InteracaoVisitante, formatarDuracao } from '@/components/InteracaoVisitante';
import { dataHora } from '@/features/leads/formatos';
import {
  excluirLink,
  listarLinks,
  listarVisitas,
  obterConfiguracao,
  resumirVisitas,
  salvarConfiguracao,
  type ConfiguracaoRastreamento,
  type LinkCampanha,
  type ResumoVisitas,
  type VisitaAnuncio,
} from './api';
import { LinkModal } from './LinkModal';

/** As visitas mudam a cada sinal do script: a tela relê a cada 5 s (o SSE fica para a A4). */
const ATUALIZACAO_MS = 5000;

/**
 * Configurações › Rastreamento (R1): o "lead 0" de ponta a ponta. A pessoa gestora cria o link que
 * vai no anúncio, pede ao Landing a tag do script e acompanha as visitas: quem clicou, de que
 * aparelho, o que fez na página e se converteu ou foi descartada no prazo.
 */
export function RastreamentoAba() {
  const [configuracao, setConfiguracao] = useState<ConfiguracaoRastreamento | null>(null);
  const [links, setLinks] = useState<LinkCampanha[] | null>(null);
  const [visitas, setVisitas] = useState<VisitaAnuncio[] | null>(null);
  const [resumo, setResumo] = useState<ResumoVisitas | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [linkEmEdicao, setLinkEmEdicao] = useState<LinkCampanha | null | undefined>(undefined);
  const [linkAExcluir, setLinkAExcluir] = useState<LinkCampanha | null>(null);
  const [visitaAberta, setVisitaAberta] = useState<VisitaAnuncio | null>(null);
  const agora = useAgora();

  const carregarLinks = useCallback(() => listarLinks().then((pagina) => setLinks(pagina.itens)), []);

  const carregarVisitas = useCallback(
    () =>
      Promise.all([listarVisitas(), resumirVisitas()]).then(([pagina, novoResumo]) => {
        setVisitas(pagina.itens);
        setResumo(novoResumo);
      }),
    [],
  );

  useEffect(() => {
    Promise.all([obterConfiguracao().then(setConfiguracao), carregarLinks(), carregarVisitas()]).catch((falha) =>
      setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar o rastreamento.'),
    );
  }, [carregarLinks, carregarVisitas]);

  useEffect(() => {
    const intervalo = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        carregarVisitas().catch(() => undefined);
        carregarLinks().catch(() => undefined);
      }
    }, ATUALIZACAO_MS);
    return () => window.clearInterval(intervalo);
  }, [carregarLinks, carregarVisitas]);

  if (erro) {
    return <EmptyState icone={AlertCircle} erro titulo="Rastreamento indisponível" descricao={erro} />;
  }
  if (!configuracao || !links || !visitas) return <Spinner />;

  return (
    <div className="space-y-5">
      <Explicacao />

      <ResumoDasVisitas resumo={resumo} />

      <div className="grid gap-5 xl:grid-cols-[1fr_2fr]">
        <CartaoConfiguracao configuracao={configuracao} onSalvo={setConfiguracao} />

        <Cartao
          titulo="Links de campanha"
          icone={Link2}
          acao={
            <Button size="sm" onClick={() => setLinkEmEdicao(null)}>
              <Plus aria-hidden className="h-3 w-3 shrink-0" />
              Novo link
            </Button>
          }
        >
          {links.length === 0 ? (
            <EmptyState
              icone={Link2}
              titulo="Nenhum link ainda"
              descricao="Crie um link e use a URL dele como destino do anúncio no Meta ou no Google."
            />
          ) : (
            <ul className="divide-y divide-borda">
              {links.map((link) => (
                <li key={link.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-2 text-sm font-medium text-texto">
                      {link.nome}
                      {!link.ativo && statusBadge('inativo')}
                    </p>
                    <p className="truncate text-xs text-texto-3">
                      {[link.utmSource, link.utmMedium, link.utmCampaign].filter(Boolean).join(' · ') || 'sem UTM'} →{' '}
                      {link.urlDestino}
                    </p>
                    <p className="mt-1 flex items-center gap-1.5">
                      <code className="truncate rounded bg-superficie-2 px-1.5 py-0.5 text-xs text-texto-2">
                        {link.urlPublica}
                      </code>
                      <BotaoCopiar texto={link.urlPublica} rotulo="Copiar link do anúncio" />
                    </p>
                  </div>
                  <Badge variant="blue">
                    <MousePointerClick aria-hidden className="h-3 w-3" />
                    {link.visitas} {link.visitas === 1 ? 'clique' : 'cliques'}
                  </Badge>
                  <div className="flex gap-1">
                    <a
                      href={link.urlPublica}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1.5 rounded-lg bg-brand-800 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
                    >
                      <ExternalLink aria-hidden className="h-3 w-3 shrink-0" />
                      Simular clique
                    </a>
                    <Button variant="ghost" size="sm" aria-label={`Editar ${link.nome}`} onClick={() => setLinkEmEdicao(link)}>
                      <Pencil aria-hidden className="h-3 w-3 shrink-0" />
                    </Button>
                    <Button variant="ghost" size="sm" aria-label={`Excluir ${link.nome}`} onClick={() => setLinkAExcluir(link)}>
                      <Trash2 aria-hidden className="h-3 w-3 shrink-0" />
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Cartao>
      </div>

      <Cartao
        titulo="Visitas de anúncio ao vivo"
        icone={Radar}
        acao={<span className="text-xs text-texto-3">atualiza a cada 5 s</span>}
      >
        {visitas.length === 0 ? (
          <EmptyState
            icone={Radar}
            titulo="Nenhuma visita ainda"
            descricao='Clique em "Simular clique" num link: a visita aparece aqui, e o script da landing page manda o que a pessoa fizer.'
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-borda bg-superficie-2 text-xs text-texto-3">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">Situação</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Clique</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Link</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Aparelho</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Na página</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Rolagem</th>
                  <th scope="col" className="px-4 py-2.5 font-medium">Cliques</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-borda">
                {visitas.map((visita) => (
                  <tr
                    key={visita.id}
                    tabIndex={0}
                    onClick={() => setVisitaAberta(visita)}
                    onKeyDown={(evento) => evento.key === 'Enter' && setVisitaAberta(visita)}
                    className="cursor-pointer hover:bg-superficie-2 focus-visible:bg-superficie-2"
                  >
                    <td className="px-4 py-2.5">
                      <SituacaoDaVisita visita={visita} agora={agora} />
                    </td>
                    <td className="px-4 py-2.5 whitespace-nowrap text-texto-3">{dataHora(visita.clicadoEm)}</td>
                    <td className="px-4 py-2.5 text-texto-2">
                      {visita.linkNome ?? '—'}
                      {visita.utmCampaign && <span className="block text-xs text-texto-3">{visita.utmCampaign}</span>}
                    </td>
                    <td className="px-4 py-2.5">
                      {visita.contextoTecnico ? (
                        <Dispositivo tipo={visita.contextoTecnico.tipoDispositivo} />
                      ) : (
                        <span className="text-xs text-texto-3">dados descartados</span>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-texto-2">
                      {formatarDuracao(visita.comportamento?.tempoNaPaginaSegundos) ?? '—'}
                    </td>
                    <td className="px-4 py-2.5 text-texto-2">
                      {visita.comportamento?.profundidadeRolagem !== undefined
                        ? `${visita.comportamento.profundidadeRolagem}%`
                        : '—'}
                    </td>
                    <td className="px-4 py-2.5 text-texto-2">{visita.comportamento?.elementosClicados?.length ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Cartao>

      <LinkModal
        aberto={linkEmEdicao !== undefined}
        link={linkEmEdicao ?? null}
        onClose={() => setLinkEmEdicao(undefined)}
        onSalvo={() => {
          setLinkEmEdicao(undefined);
          carregarLinks();
        }}
      />
      <ConfirmarExclusao
        link={linkAExcluir}
        onClose={() => setLinkAExcluir(null)}
        onExcluido={() => {
          setLinkAExcluir(null);
          carregarLinks();
        }}
      />
      <DetalheDaVisita visita={visitaAberta} agora={agora} onClose={() => setVisitaAberta(null)} />
    </div>
  );
}

/** Relógio da tela, para a contagem regressiva do descarte. */
function useAgora() {
  const [agora, setAgora] = useState(() => Date.now());
  useEffect(() => {
    const intervalo = window.setInterval(() => setAgora(Date.now()), 1000);
    return () => window.clearInterval(intervalo);
  }, []);
  return agora;
}

function Explicacao() {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-brand-200 bg-brand-50 p-4 text-sm text-brand-900 dark:border-brand-800/40 dark:bg-brand-800/15 dark:text-brand-200">
      <Radar aria-hidden className="mt-0.5 h-5 w-5 shrink-0 text-brand-700" />
      <div>
        <p className="font-semibold">Lead 0: quem clicou no anúncio e ainda não deixou contato</p>
        <p className="mt-0.5 text-brand-800 dark:text-brand-300">
          O link registra o aparelho de quem clicou e o script da landing page manda o que a pessoa faz lá. Se ela enviar
          o formulário, a visita é ligada ao lead e pode somar pontos. Se não seguir em frente no prazo, os dados da
          visita são descartados.
        </p>
      </div>
    </div>
  );
}

function ResumoDasVisitas({ resumo }: { resumo: ResumoVisitas | null }) {
  const itens: { rotulo: string; valor: ReactNode; destaque?: boolean }[] = [
    { rotulo: 'Cliques (24 h)', valor: resumo?.total ?? '—' },
    { rotulo: 'Lead 0 em andamento', valor: resumo?.ativas ?? '—' },
    { rotulo: 'Convertidas em lead', valor: resumo?.convertidas ?? '—' },
    { rotulo: 'Descartadas no prazo', valor: resumo?.descartadas ?? '—' },
    {
      rotulo: 'Taxa de conversão',
      valor: resumo ? `${resumo.taxaConversao.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%` : '—',
      destaque: true,
    },
  ];
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {itens.map((item) => (
        <div key={item.rotulo} className="rounded-xl border border-borda bg-superficie p-4">
          <dt className="text-xs text-texto-3">{item.rotulo}</dt>
          <dd className={`mt-1 text-2xl font-bold ${item.destaque ? 'text-brand-700' : 'text-titulo'}`}>{item.valor}</dd>
        </div>
      ))}
    </dl>
  );
}

function Cartao({
  titulo,
  icone: Icone,
  acao,
  children,
}: {
  titulo: string;
  icone: typeof Radar;
  acao?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-xl border border-borda bg-superficie">
      <header className="flex items-center justify-between gap-3 border-b border-borda px-4 py-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-titulo">
          <Icone aria-hidden className="h-4 w-4 shrink-0 text-brand-700" />
          {titulo}
        </h2>
        {acao}
      </header>
      {children}
    </section>
  );
}

function CartaoConfiguracao({
  configuracao,
  onSalvo,
}: {
  configuracao: ConfiguracaoRastreamento;
  onSalvo: (configuracao: ConfiguracaoRastreamento) => void;
}) {
  const [ativo, setAtivo] = useState(configuracao.ativo);
  const [prazo, setPrazo] = useState(String(configuracao.prazoConversaoMinutos));
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);
  const [salvo, setSalvo] = useState(false);
  const tag = `<script src="${configuracao.urlScript}" async></script>`;
  const mudou = ativo !== configuracao.ativo || prazo !== String(configuracao.prazoConversaoMinutos);

  async function salvar() {
    setSalvando(true);
    setErro(null);
    try {
      onSalvo(await salvarConfiguracao(ativo, Number(prazo)));
      setSalvo(true);
      window.setTimeout(() => setSalvo(false), 2000);
    } catch (falha) {
      setErro(falha instanceof ErroApi ? (falha.erros[0]?.detalhe ?? falha.message) : 'Não foi possível salvar.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <Cartao titulo="Configuração" icone={Timer}>
      <div className="space-y-4 p-4">
        <label className="flex items-center justify-between gap-3 text-sm text-texto">
          Registrar as visitas dos links
          <input
            type="checkbox"
            role="switch"
            checked={ativo}
            onChange={(evento) => setAtivo(evento.target.checked)}
            className="h-5 w-9 cursor-pointer appearance-none rounded-full bg-borda-forte transition-colors before:block before:h-4 before:w-4 before:translate-x-0.5 before:rounded-full before:bg-white before:transition-transform checked:bg-brand-700 checked:before:translate-x-4.5"
          />
        </label>
        <FormField
          label="Prazo para converter (minutos)"
          error={erro}
          hint="Sem enviar o formulário nem dar sinal nesse tempo, os dados da visita são descartados."
        >
          <Input type="number" min={1} max={10080} value={prazo} onChange={(evento) => setPrazo(evento.target.value)} />
        </FormField>
        <Button size="sm" onClick={salvar} disabled={!mudou || salvando}>
          {salvo ? <Check aria-hidden className="h-3 w-3 shrink-0" /> : null}
          {salvando ? 'Salvando…' : salvo ? 'Salvo' : 'Salvar'}
        </Button>
        <div className="border-t border-borda pt-4">
          <p className="mb-1.5 text-xs text-texto-3">Tag para a landing page (pedido ao Landing):</p>
          <div className="flex items-start gap-1.5">
            <code className="block flex-1 rounded-lg bg-brand-950 px-3 py-2 font-mono text-xs break-all text-brand-300">
              {tag}
            </code>
            <BotaoCopiar texto={tag} rotulo="Copiar a tag" />
          </div>
        </div>
      </div>
    </Cartao>
  );
}

function BotaoCopiar({ texto, rotulo }: { texto: string; rotulo: string }) {
  const [copiado, setCopiado] = useState(false);
  return (
    <button
      type="button"
      aria-label={rotulo}
      title={rotulo}
      onClick={() => {
        navigator.clipboard?.writeText(texto).then(() => {
          setCopiado(true);
          window.setTimeout(() => setCopiado(false), 1500);
        });
      }}
      className="shrink-0 rounded-lg p-1.5 text-texto-3 hover:bg-superficie-2 hover:text-texto"
    >
      {copiado ? <Check aria-hidden className="h-3.5 w-3.5 text-brand-700" /> : <Copy aria-hidden className="h-3.5 w-3.5" />}
    </button>
  );
}

function SituacaoDaVisita({ visita, agora }: { visita: VisitaAnuncio; agora: number }) {
  if (visita.situacao !== 'ativa') {
    return statusBadge(visita.situacao);
  }
  const restante = Math.max(0, Math.round((new Date(visita.expiraEm).getTime() - agora) / 1000));
  const minutos = Math.floor(restante / 60);
  const segundos = String(restante % 60).padStart(2, '0');
  return (
    <span className="flex flex-col gap-0.5">
      {statusBadge('ativa', 'lead 0')}
      <span className="text-xs text-texto-3 tabular-nums">
        {restante > 0 ? `descarte em ${minutos}:${segundos}` : 'descartando…'}
      </span>
    </span>
  );
}

function DetalheDaVisita({
  visita,
  agora,
  onClose,
}: {
  visita: VisitaAnuncio | null;
  agora: number;
  onClose: () => void;
}) {
  const navegar = useNavigate();
  return (
    <Modal
      open={!!visita}
      onClose={onClose}
      title="Visita de anúncio"
      subtitle={visita ? `${visita.linkNome ?? 'Link excluído'} · clique em ${dataHora(visita.clicadoEm)}` : undefined}
      footer={
        visita?.leadId ? (
          <Button onClick={() => navegar(`/leads/${visita.leadId}`)}>Abrir o lead</Button>
        ) : (
          <Button variant="outline" onClick={onClose}>
            Fechar
          </Button>
        )
      }
    >
      {visita && (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <SituacaoDaVisita visita={visita} agora={agora} />
            {visita.utmCampaign && <Badge variant="gray">campanha {visita.utmCampaign}</Badge>}
            {visita.convertidaEm && <Badge variant="green">convertida em {dataHora(visita.convertidaEm)}</Badge>}
          </div>
          {visita.situacao === 'descartada' ? (
            <EmptyState
              icone={Trash2}
              titulo="Dados descartados"
              descricao={`A pessoa não enviou o formulário no prazo. Ficaram só o link, a campanha e as datas (descartada em ${dataHora(visita.descartadaEm)}).`}
            />
          ) : (
            <InteracaoVisitante contexto={visita.contextoTecnico} comportamento={visita.comportamento} />
          )}
        </>
      )}
    </Modal>
  );
}

function ConfirmarExclusao({
  link,
  onClose,
  onExcluido,
}: {
  link: LinkCampanha | null;
  onClose: () => void;
  onExcluido: () => void;
}) {
  const [erro, setErro] = useState<string | null>(null);
  useEffect(() => setErro(null), [link]);
  return (
    <Modal
      open={!!link}
      onClose={onClose}
      size="sm"
      title="Excluir link de campanha?"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            variant="danger"
            onClick={() =>
              link &&
              excluirLink(link.id)
                .then(onExcluido)
                .catch((falha) => setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível excluir.'))
            }
          >
            Excluir
          </Button>
        </>
      }
    >
      <p className="text-sm text-texto-2">
        O anúncio que apontar para <strong>{link?.nome}</strong> passa a cair numa página de erro. As visitas já
        registradas continuam nas contagens.
      </p>
      {erro && <p role="alert" className="mt-3 text-sm text-red-600">{erro}</p>}
    </Modal>
  );
}
