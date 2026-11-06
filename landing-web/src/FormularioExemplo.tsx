import { useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { CHAVE_FORMULARIO, SLUG_PAGINA, chamar, type CampoPublico, type FormularioPublico } from './api';
import type { Origem } from './origem';

type Valores = Record<string, string | string[]>;

const estiloCampo = 'w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-brand-700 focus:outline-none';

/**
 * Formulário de exemplo, renderizado a partir do schema público do placeholder
 * (GET /public/landing/formularios/{chavePublica}), com o aceite do consentimento.
 */
export function FormularioExemplo({ origem }: { origem: Origem }) {
  const [formulario, setFormulario] = useState<FormularioPublico | null>(null);
  const [falhaAoCarregar, setFalhaAoCarregar] = useState<string | null>(null);
  const [valores, setValores] = useState<Valores>({});
  const [aceito, setAceito] = useState(false);
  const [erros, setErros] = useState<Record<string, string>>({});
  const [mensagem, setMensagem] = useState<string | null>(null);
  const [sucesso, setSucesso] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    chamar<FormularioPublico>('GET', `/public/landing/formularios/${CHAVE_FORMULARIO}`).then((resposta) => {
      if (resposta.success && resposta.data) setFormulario(resposta.data);
      else setFalhaAoCarregar(resposta.message ?? 'Formulário indisponível.');
    });
  }, []);

  if (falhaAoCarregar) return <p className="text-sm text-red-700">{falhaAoCarregar}</p>;
  if (!formulario) return <p className="text-sm text-gray-500">Carregando o formulário...</p>;

  if (sucesso) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-xl border border-brand-300 bg-brand-50 p-8 text-center">
        <CheckCircle2 className="h-8 w-8 text-brand-700" />
        <p className="font-semibold">{sucesso}</p>
        <button
          onClick={() => {
            setSucesso(null);
            setValores({});
            setAceito(false);
          }}
          className="text-sm text-brand-800 underline"
        >
          Enviar outro
        </button>
      </div>
    );
  }

  const definir = (id: string, valor: string | string[]) => setValores((atual) => ({ ...atual, [id]: valor }));

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setMensagem(null);
    setErros({});
    const resposta = await chamar<{ mensagemSucesso: string }>(
      'POST',
      `/public/landing/formularios/${CHAVE_FORMULARIO}/envios`,
      { respostas: valores, consentimento: { aceito }, paginaSlug: SLUG_PAGINA, origem },
    );
    setEnviando(false);
    if (resposta.success && resposta.data) {
      setSucesso(resposta.data.mensagemSucesso);
      return;
    }
    setMensagem(resposta.message);
    setErros(Object.fromEntries(resposta.errors.map((e) => [e.campo ?? '', e.detalhe])));
  }

  return (
    <form onSubmit={enviar} noValidate className="flex flex-col gap-4 rounded-xl border border-gray-200 p-6">
      <h2 className="text-lg font-semibold">{formulario.nome}</h2>

      {formulario.campos.map((campo) => (
        <label key={campo.id} className="flex flex-col gap-1 text-sm">
          <span className="font-medium">
            {campo.rotulo}
            {campo.obrigatorio && <span className="text-red-600"> *</span>}
          </span>
          <Entrada campo={campo} valor={valores[campo.id]} definir={(v) => definir(campo.id, v)} />
          {erros[campo.id] && <span className="text-xs text-red-700">{erros[campo.id]}</span>}
        </label>
      ))}

      <label className="flex items-start gap-2 text-xs text-gray-600">
        <input type="checkbox" checked={aceito} onChange={(e) => setAceito(e.target.checked)} className="mt-0.5" />
        <span>{formulario.textoConsentimento}</span>
      </label>
      {erros.consentimento && <span className="text-xs text-red-700">{erros.consentimento}</span>}

      {mensagem && <p className="text-sm text-red-700">{mensagem}</p>}

      <button
        type="submit"
        disabled={enviando}
        className="rounded-lg bg-brand-700 px-5 py-2.5 font-semibold text-white hover:bg-brand-800 disabled:opacity-60"
      >
        {enviando ? 'Enviando...' : 'Enviar'}
      </button>
    </form>
  );
}

function Entrada({ campo, valor, definir }: { campo: CampoPublico; valor: string | string[] | undefined; definir: (v: string | string[]) => void }) {
  if (campo.tipo === 'TEXTO_LONGO') {
    return <textarea rows={3} className={estiloCampo} value={(valor as string) ?? ''} onChange={(e) => definir(e.target.value)} />;
  }
  if (campo.tipo === 'LISTA_OPCOES') {
    return (
      <select className={estiloCampo} value={(valor as string) ?? ''} onChange={(e) => definir(e.target.value)}>
        <option value="">Selecione</option>
        {campo.opcoes?.map((opcao) => <option key={opcao}>{opcao}</option>)}
      </select>
    );
  }
  if (campo.tipo === 'MULTIPLA_ESCOLHA') {
    const marcados = (valor as string[] | undefined) ?? [];
    return (
      <div className="flex flex-wrap gap-3">
        {campo.opcoes?.map((opcao) => (
          <label key={opcao} className="flex items-center gap-1.5">
            <input
              type="checkbox"
              checked={marcados.includes(opcao)}
              onChange={(e) => definir(e.target.checked ? [...marcados, opcao] : marcados.filter((m) => m !== opcao))}
            />
            {opcao}
          </label>
        ))}
      </div>
    );
  }
  const tipo = campo.tipo === 'EMAIL' ? 'email' : campo.tipo === 'TELEFONE' ? 'tel' : 'text';
  return (
    <input
      type={tipo}
      className={estiloCampo}
      placeholder={campo.placeholder ?? undefined}
      value={(valor as string) ?? ''}
      onChange={(e) => definir(e.target.value)}
    />
  );
}
