import { useEffect, useState, type ReactNode } from 'react';
import {
  Activity,
  AlertCircle,
  Award,
  CheckCircle2,
  ClipboardList,
  History,
  MapPinned,
  Megaphone,
  User,
  UserRoundPen,
  X,
} from 'lucide-react';
import { ErroApi } from '@/api/cliente';
import {
  Badge,
  Button,
  DetailField,
  DetailSection,
  EmptyState,
  Modal,
  Spinner,
  Tabs,
  statusBadge,
  type Aba,
} from '@/components/ui';
import { InteracaoVisitante } from '@/components/InteracaoVisitante';
import { plataforma, usarPermissao } from '@/plataforma';
import { detalharLead, type DetalheLead } from './api';
import { ReatribuirVendedorModal } from './ReatribuirVendedorModal';
import { DadosDoCrm, HistoricoDeConsentimentos } from './SecoesDoDetalhe';
import {
  DICA_NOME_DO_VENDEDOR,
  NOME_CANAL,
  NOME_ETAPA,
  NOME_EVENTO,
  NOME_FAIXA,
  NOME_FONTE_REGRA,
  NOME_METRICA,
  NOME_ORIGEM,
  dataHora,
  valorDaResposta,
  vendedorEncurtado,
} from './formatos';

type AbaDetalhe = 'dados' | 'origem' | 'interacao' | 'respostas' | 'pontuacao' | 'timeline';

/**
 * Detalhe do lead (norteador §9.2): Modal xl noPad com cabeçalho brand-950 e abas internas
 * (Design System 5.2). A aba "Interação" mostra a visita do anúncio que converteu (R1). A aba "Dados"
 * traz o contato, o vendedor (com a reatribuição manual), o histórico de consentimentos (a evidência
 * de IP e User-Agent só aparece aqui), os e-mails enviados (D3) e, em "CRM", a oportunidade com os
 * resumos de empresa e contato, buscados direto no CRM com o token do usuário (§9.2): se o CRM falhar,
 * só aquele campo diz "indisponível" e o resto do detalhe continua (§9.6).
 */
export function DetalheLeadModal({ id, onClose }: { id: string | null; onClose: () => void }) {
  const [detalhe, setDetalhe] = useState<DetalheLead | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [aba, setAba] = useState<AbaDetalhe>('dados');
  const [reatribuindo, setReatribuindo] = useState(false);
  const [aviso, setAviso] = useState<string | null>(null);
  // Esconder o botão é só usabilidade: a API continua respondendo 403 sem a permissão.
  const podeReatribuir = usarPermissao('marketing.qualificacao.editar');

  useEffect(() => {
    if (!id) return;
    let ativo = true;
    setDetalhe(null);
    setErro(null);
    setAviso(null);
    setReatribuindo(false);
    setAba('dados');
    detalharLead(id)
      .then((dados) => ativo && setDetalhe(dados))
      .catch((falha) => ativo && setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível abrir o lead.'));
    return () => {
      ativo = false;
    };
  }, [id]);

  /** Relê o detalhe depois de reatribuir, sem voltar para a aba "Dados" nem mostrar o carregando. */
  function recarregar() {
    if (!id) return;
    detalharLead(id)
      .then((dados) => {
        setDetalhe(dados);
        setAviso('Vendedor reatribuído.');
        plataforma.notificar('sucesso', 'Vendedor reatribuído.');
      })
      .catch(() => setAviso('Vendedor reatribuído, mas não foi possível recarregar o lead. Feche e abra de novo.'));
  }

  const temInteracao = !!(detalhe?.interacao?.contextoTecnico || detalhe?.interacao?.comportamento);
  const abas: Aba[] = [
    { valor: 'dados', rotulo: 'Dados', icone: User },
    { valor: 'origem', rotulo: 'Origem', icone: Megaphone },
    { valor: 'interacao', rotulo: 'Interação', icone: Activity },
    { valor: 'respostas', rotulo: 'Respostas', icone: ClipboardList },
    { valor: 'pontuacao', rotulo: 'Pontuação', icone: Award },
    { valor: 'timeline', rotulo: 'Timeline', icone: History, contador: detalhe?.timeline.length },
  ];

  return (
    <Modal open={!!id} onClose={onClose} noPad size="xl" title="Detalhe do lead">
      <header className="shrink-0 bg-brand-950 px-6 pt-5 text-white">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <h2 className="truncate text-lg font-bold">
              {detalhe ? nomeDoLead(detalhe) : erro ? 'Lead' : 'Carregando…'}
            </h2>
            {detalhe && (
              <p className="mt-0.5 truncate text-sm text-brand-300">
                {[detalhe.contato.email, detalhe.contato.telefone].filter(Boolean).join(' · ') || 'sem contato'}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Fechar"
            className="-mr-1.5 shrink-0 rounded-lg p-1.5 text-brand-300 hover:bg-white/10 hover:text-white"
          >
            <X aria-hidden className="h-4 w-4" />
          </button>
        </div>
        {detalhe && (
          <div className="mt-3 flex flex-wrap gap-1.5">
            <Badge variant="indigo">{NOME_ETAPA[detalhe.etapa] ?? `Etapa ${detalhe.etapa}`}</Badge>
            {detalhe.temperatura && statusBadge(detalhe.temperatura)}
            {detalhe.qualificacao.faixaQualificacao &&
              statusBadge(
                detalhe.qualificacao.faixaQualificacao,
                `${NOME_FAIXA[detalhe.qualificacao.faixaQualificacao]} · ${detalhe.qualificacao.pontuacao ?? 0} pts`,
              )}
            {temInteracao && <Badge variant="purple">veio do anúncio</Badge>}
            {detalhe.crm.oportunidadeId && statusBadge('no_crm', 'no CRM')}
            {detalhe.anonimizado && statusBadge('anonimizado')}
          </div>
        )}
        <Tabs
          abas={abas}
          ativa={aba}
          onChange={(valor) => setAba(valor as AbaDetalhe)}
          escura
          className="mt-3 -mb-px"
          label="Seções do lead"
        />
      </header>

      <div role="tabpanel" className="min-h-72 flex-1 overflow-y-auto px-6 py-5">
        {erro ? (
          <EmptyState icone={AlertCircle} erro titulo="Não foi possível abrir o lead" descricao={erro} />
        ) : !detalhe ? (
          <Spinner />
        ) : (
          <>
            {aviso && (
              <p
                role="status"
                className="mb-4 flex items-center gap-2 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-800 dark:bg-brand-800/20 dark:text-brand-300"
              >
                <CheckCircle2 aria-hidden className="h-3.5 w-3.5 shrink-0" />
                {aviso}
              </p>
            )}
            <ConteudoDaAba
              aba={aba}
              detalhe={detalhe}
              onReatribuir={podeReatribuir ? () => setReatribuindo(true) : undefined}
            />
          </>
        )}
      </div>

      {detalhe && (
        <ReatribuirVendedorModal
          aberto={reatribuindo}
          leadId={detalhe.id}
          vendedorAtual={detalhe.vendedor.vendedorId}
          onClose={() => setReatribuindo(false)}
          onReatribuido={recarregar}
        />
      )}
    </Modal>
  );
}

function nomeDoLead(detalhe: DetalheLead) {
  if (detalhe.anonimizado) return 'Lead anonimizado';
  return detalhe.contato.nome || detalhe.contato.email || 'Lead sem nome';
}

function ConteudoDaAba({
  aba,
  detalhe,
  onReatribuir,
}: {
  aba: AbaDetalhe;
  detalhe: DetalheLead;
  onReatribuir?: () => void;
}) {
  switch (aba) {
    case 'dados':
      return (
        <>
          <DetailSection number={1} title="Contato">
            <DetailField label="Nome">{detalhe.contato.nome}</DetailField>
            <DetailField label="E-mail">{detalhe.contato.email}</DetailField>
            <DetailField label="Telefone">{detalhe.contato.telefone}</DetailField>
            <DetailField label="WhatsApp">{detalhe.contato.whatsapp}</DetailField>
          </DetailSection>
          <DetailSection number={2} title="Funil e distribuição">
            <DetailField label="Etapa">{NOME_ETAPA[detalhe.etapa]}</DetailField>
            <DetailField label="Entrou em">{dataHora(detalhe.criadoEm)}</DetailField>
            <DetailField label="Vendedor">
              <span className="flex flex-wrap items-center gap-2">
                {detalhe.vendedor.vendedorId ? (
                  <span title={DICA_NOME_DO_VENDEDOR}>{vendedorEncurtado(detalhe.vendedor.vendedorId)}</span>
                ) : (
                  statusBadge('aguardando_vendedor', 'aguardando vendedor')
                )}
                {onReatribuir && (
                  <Button variant="outline" size="sm" onClick={onReatribuir}>
                    <UserRoundPen aria-hidden className="h-3 w-3 shrink-0" />
                    {detalhe.vendedor.vendedorId ? 'Reatribuir' : 'Atribuir'}
                  </Button>
                )}
              </span>
            </DetailField>
            <DetailField label="Atribuído em">{dataHora(detalhe.vendedor.atribuidoEm)}</DetailField>
          </DetailSection>
          <DetailSection number={3} title="Consentimento" className="mb-3">
            <DetailField label="Marketing">{detalhe.consentimento.marketing ? 'Aceito' : 'Sem aceite'}</DetailField>
            <DetailField label="Aceito em">{dataHora(detalhe.consentimento.aceitoEm)}</DetailField>
          </DetailSection>
          <HistoricoDeConsentimentos historico={detalhe.consentimento.historico} />
          <DetailSection number={4} title="Comunicações e ciclo de vida">
            <DetailField label="Boas-vindas">
              {detalhe.aquecimento.boasVindasEnviadaEm
                ? `Enviada em ${dataHora(detalhe.aquecimento.boasVindasEnviadaEm)}`
                : detalhe.etapa === 3
                  ? statusBadge('pendente', 'aguardando envio')
                  : 'Não aplicável'}
            </DetailField>
            <DetailField label="Aquecimento">
              {statusBadge(detalhe.aquecimento.status)} ({detalhe.aquecimento.tentativas} tentativa(s))
            </DetailField>
            {detalhe.aquecimento.comunicacoes && detalhe.aquecimento.comunicacoes.length > 0 && (
              <DetailField label="E-mails enviados" colSpan={2}>
                <ul className="mt-1 divide-y divide-borda rounded-lg border border-borda text-xs">
                  {detalhe.aquecimento.comunicacoes.map((c) => (
                    <li key={c.id} className="flex items-center justify-between gap-2 px-3 py-2">
                      <span>
                        <strong>{c.modeloNome ?? 'Modelo'}</strong> ({c.tipoModelo ?? c.canal})
                      </span>
                      <span className="flex items-center gap-2 text-texto-3">
                        {dataHora(c.enviadoEm)}
                        {statusBadge(c.status)}
                      </span>
                    </li>
                  ))}
                </ul>
              </DetailField>
            )}
          </DetailSection>
          <DetailSection number={5} title="CRM">
            <DadosDoCrm crm={detalhe.crm} />
          </DetailSection>
        </>
      );
    case 'origem':
      return (
        <>
          <DetailSection number={1} title="Entrada">
            <DetailField label="Origem">{NOME_ORIGEM[detalhe.origem.origem] ?? detalhe.origem.origem}</DetailField>
            <DetailField label="Canal">{detalhe.origem.canal && NOME_CANAL[detalhe.origem.canal]}</DetailField>
            <DetailField label="Página de entrada" colSpan={2}>
              {detalhe.origem.landingPageUrl && (
                <span className="font-mono text-xs break-all">{detalhe.origem.landingPageUrl}</span>
              )}
            </DetailField>
            <DetailField label="Primeiro toque">{dataHora(detalhe.origem.primeiroToque)}</DetailField>
            <DetailField label="Último toque">{dataHora(detalhe.origem.ultimoToque)}</DetailField>
          </DetailSection>
          <DetailSection number={2} title="Campanha (UTM da última origem)">
            <DetailField label="utm_source">{detalhe.origem.utmSource}</DetailField>
            <DetailField label="utm_medium">{detalhe.origem.utmMedium}</DetailField>
            <DetailField label="utm_campaign">{detalhe.origem.utmCampaign}</DetailField>
            <DetailField label="utm_content">{detalhe.origem.utmContent}</DetailField>
          </DetailSection>
        </>
      );
    case 'interacao':
      return detalhe.interacao?.contextoTecnico || detalhe.interacao?.comportamento ? (
        <>
          <p className="mb-4 flex items-start gap-2 rounded-lg bg-brand-50 px-3 py-2 text-xs text-brand-800 dark:bg-brand-800/20 dark:text-brand-300">
            <MapPinned aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            Quem enviou o formulário chegou por um link de campanha. Este é o rastro da visita, do clique no anúncio
            até o envio.
          </p>
          <InteracaoVisitante
            contexto={detalhe.interacao.contextoTecnico}
            comportamento={detalhe.interacao.comportamento}
          />
        </>
      ) : (
        <EmptyState
          icone={Activity}
          titulo="Sem visita de anúncio ligada"
          descricao="O lead não chegou por um link de campanha rastreado, ou a visita foi descartada antes do envio."
        />
      );
    case 'respostas':
      return <Respostas detalhe={detalhe} />;
    case 'pontuacao':
      return (
        <>
          <DetailSection number={1} title="Resultado">
            <DetailField label="Pontuação">{detalhe.qualificacao.pontuacao}</DetailField>
            <DetailField label="Faixa">
              {detalhe.qualificacao.faixaQualificacao &&
                statusBadge(
                  detalhe.qualificacao.faixaQualificacao,
                  NOME_FAIXA[detalhe.qualificacao.faixaQualificacao],
                )}
            </DetailField>
            <DetailField label="Calculada em">{dataHora(detalhe.qualificacao.qualificadoEm)}</DetailField>
          </DetailSection>
          <h3 className="mb-3 text-sm font-semibold text-titulo">Regras que somaram pontos</h3>
          {detalhe.qualificacao.detalhe.length === 0 ? (
            <p className="text-sm text-texto-3">Nenhuma regra bateu.</p>
          ) : (
            <ul className="divide-y divide-borda rounded-xl border border-borda">
              {detalhe.qualificacao.detalhe.map((item) => (
                <li key={item.regraId ?? item.criterio} className="flex items-center justify-between gap-3 px-4 py-2.5">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-texto">{item.criterio}</p>
                    <p className="text-xs text-texto-3">
                      {NOME_FONTE_REGRA[item.fonte] ?? item.fonte}
                      {item.campoComportamento && ` · ${NOME_METRICA[item.campoComportamento] ?? item.campoComportamento}`}
                    </p>
                  </div>
                  <Badge variant={item.fonte === 'comportamento' ? 'purple' : 'green'}>+{item.pontos}</Badge>
                </li>
              ))}
            </ul>
          )}
        </>
      );
    case 'timeline':
      return (
        <ol className="relative ml-2 border-l border-borda">
          {detalhe.timeline.map((item) => (
            <li key={item.sequencia} className="mb-4 ml-4 last:mb-0">
              <span aria-hidden className="absolute -left-1.5 mt-1.5 h-3 w-3 rounded-full border-2 border-superficie bg-brand-700" />
              <p className="text-sm font-medium text-texto">
                {NOME_EVENTO[item.tipo] ?? item.tipo}
                {item.etapaNova !== null && ` → etapa ${item.etapaNova}`}
              </p>
              <p className="text-xs text-texto-3">
                {dataHora(item.ocorridoEm)}
                {resumoDoEvento(item.payload) && ` · ${resumoDoEvento(item.payload)}`}
              </p>
            </li>
          ))}
        </ol>
      );
  }
}

function Respostas({ detalhe }: { detalhe: DetalheLead }) {
  const { situacao, itens, lidasEm } = detalhe.respostas;
  if (situacao === 'sem_envio') {
    return <EmptyState icone={ClipboardList} titulo="Sem envio de formulário" descricao="O lead ainda não enviou um formulário do Landing." />;
  }
  if (situacao === 'aguardando_landing') {
    return <EmptyState icone={ClipboardList} titulo="Aguardando o Landing" descricao="O envio chegou, mas as respostas ainda não foram lidas. O Marketing tenta de novo a cada minuto." />;
  }
  if (situacao === 'indisponiveis') {
    return <EmptyState icone={AlertCircle} titulo="Respostas indisponíveis" descricao="O Landing não respondeu depois de 10 tentativas." />;
  }
  return (
    <>
      <p className="mb-3 text-xs text-texto-3">Lidas do Landing em {dataHora(lidasEm)}. O formulário é do Landing.</p>
      <DetailSection title="Respostas do envio">
        {itens.map((resposta, indice) => (
          <DetailField key={resposta.campoId ?? indice} label={resposta.rotulo ?? 'Campo'}>
            {valorDaResposta(resposta.valor)}
          </DetailField>
        ))}
      </DetailSection>
    </>
  );
}

/** Uma linha do payload da timeline, sem repetir o que já está no título. */
function resumoDoEvento(payload: Record<string, unknown> | null): ReactNode {
  if (!payload) return null;
  const partes: string[] = [];
  if (payload.faixaNova) partes.push(`${NOME_FAIXA[String(payload.faixaNova)] ?? payload.faixaNova} (${payload.pontuacao} pts)`);
  if (payload.utmCampaign) partes.push(`campanha ${payload.utmCampaign}`);
  if (payload.tipoDispositivo) partes.push(String(payload.tipoDispositivo));
  if (typeof payload.tempoNaPaginaSegundos === 'number') partes.push(`${payload.tempoNaPaginaSegundos} s na página`);
  if (typeof payload.respostas === 'number') partes.push(`${payload.respostas} respostas`);
  return partes.join(' · ') || null;
}
