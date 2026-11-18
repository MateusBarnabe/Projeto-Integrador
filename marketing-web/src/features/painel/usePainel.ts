import { useCallback, useEffect, useRef, useState } from 'react';
import { ErroApi } from '@/api/cliente';
import { abrirFluxo, type EstadoDoFluxo } from '@/api/eventos';
import { obterPainel, type EventosDoPainel, type FiltroPainel, type Painel } from './api';
import { aplicarEvento, leadZeroDoFluxoVale, podeAplicarLocalmente } from './tempoReal';

/** Com o fluxo no ar, a tela confere com o servidor de tempos em tempos (as contas locais são estimativas). */
const CONFERENCIA_COM_FLUXO_MS = 60_000;
/** Sem fluxo (erro ou gateway cortando), cai para releitura a cada 30 s. */
const RELEITURA_SEM_FLUXO_MS = 30_000;
/** Espera para juntar uma rajada de eventos numa leitura só. */
const ESPERA_DA_RAJADA_MS = 3000;

const EVENTOS_CONHECIDOS = new Set<string>(['lead_criado', 'lead_etapa_alterada', 'lead_qualificado', 'lead_atribuido', 'lead_zero_atualizado']);

export type TempoReal = 'conectando' | 'conectado' | 'indisponivel';

function emTempoReal(estado: EstadoDoFluxo): TempoReal {
  if (estado === 'conectado') return 'conectado';
  if (estado === 'indisponivel') return 'indisponivel';
  return 'conectando';
}

/**
 * Dados do Painel: lê o resumo uma vez por filtro e o mantém vivo com o fluxo SSE (§9.1). `filtro` nulo
 * (por exemplo, intervalo de datas incompleto) não busca nada.
 */
export function usePainel(filtro: FiltroPainel | null) {
  const [painel, setPainel] = useState<Painel | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [tempoReal, setTempoReal] = useState<TempoReal>('conectando');

  const filtroAtual = useRef(filtro);
  filtroAtual.current = filtro;
  const controlador = useRef<AbortController | null>(null);
  const rajada = useRef<number | undefined>(undefined);

  const carregar = useCallback((silencioso: boolean) => {
    const filtroDaLeitura = filtroAtual.current;
    if (!filtroDaLeitura) return;
    controlador.current?.abort();
    const meu = new AbortController();
    controlador.current = meu;

    if (!silencioso) {
      setCarregando(true);
      setErro(null);
    }
    obterPainel(filtroDaLeitura, meu.signal)
      .then((dados) => {
        if (meu.signal.aborted) return;
        setPainel(dados);
        setErro(null);
      })
      .catch((falha) => {
        if (meu.signal.aborted) return;
        // Leitura em segundo plano que falha não apaga o que já está na tela
        if (!silencioso) setErro(falha instanceof ErroApi ? falha.message : 'Não foi possível carregar o painel.');
      })
      .finally(() => {
        if (!meu.signal.aborted) setCarregando(false);
      });
  }, []);

  // Uma leitura por filtro
  const chaveDoFiltro = filtro ? JSON.stringify(filtro) : '';
  useEffect(() => {
    if (!chaveDoFiltro) return;
    carregar(false);
    return () => controlador.current?.abort();
  }, [chaveDoFiltro, carregar]);

  // O fluxo vale para o tenant inteiro e fica aberto enquanto a tela existe, qualquer que seja o filtro
  useEffect(() => {
    function lerLogo() {
      window.clearTimeout(rajada.current);
      rajada.current = window.setTimeout(() => carregar(true), ESPERA_DA_RAJADA_MS);
    }

    const fechar = abrirFluxo(
      '/painel/eventos',
      (mensagem) => {
        const filtroDoEvento = filtroAtual.current;
        if (!filtroDoEvento || !EVENTOS_CONHECIDOS.has(mensagem.evento)) return;
        let dados: unknown;
        try {
          dados = JSON.parse(mensagem.dados);
        } catch {
          return;
        }
        const nome = mensagem.evento as keyof EventosDoPainel;
        const local = nome === 'lead_zero_atualizado' ? leadZeroDoFluxoVale(filtroDoEvento) : podeAplicarLocalmente(filtroDoEvento);
        if (!local) {
          lerLogo();
          return;
        }
        setPainel((atual) => (atual ? aplicarEvento(atual, nome, dados as EventosDoPainel[typeof nome], filtroDoEvento) : atual));
      },
      { aoMudarEstado: (estado) => setTempoReal(emTempoReal(estado)) },
    );
    return () => {
      window.clearTimeout(rajada.current);
      fechar();
    };
  }, [carregar]);

  // Conferência periódica: com fluxo, devagar; sem fluxo, é ela que mantém a tela viva
  useEffect(() => {
    if (tempoReal === 'conectando') return;
    const intervalo = window.setInterval(
      () => carregar(true),
      tempoReal === 'conectado' ? CONFERENCIA_COM_FLUXO_MS : RELEITURA_SEM_FLUXO_MS,
    );
    return () => window.clearInterval(intervalo);
  }, [tempoReal, carregar]);

  return { painel, erro, carregando, tempoReal, recarregar: () => carregar(false) };
}
