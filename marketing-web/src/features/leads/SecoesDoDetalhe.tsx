import { useEffect, useState } from 'react';
import { AlertCircle, Building2 } from 'lucide-react';
import { Badge, DetailField } from '@/components/ui';
import {
  resumirContatoNoCrm,
  resumirEmpresaNoCrm,
  type DetalheLead,
  type ResumoContatoCrm,
  type ResumoEmpresaCrm,
} from './api';
import { NOME_ORIGEM_CONSENTIMENTO, dataHora } from './formatos';

/**
 * Evidência do consentimento (LGPD): cada aceite ou revogação, com o texto do termo e, só aqui, o IP
 * e o User-Agent de quem aceitou. Nunca em log, resumo ou evento (CLAUDE.md).
 */
export function HistoricoDeConsentimentos({ historico }: { historico: DetalheLead['consentimento']['historico'] }) {
  if (historico.length === 0) {
    return <p className="mb-6 text-xs text-texto-3">Nenhum registro de consentimento.</p>;
  }
  return (
    <div className="mb-6 overflow-x-auto rounded-xl border border-borda">
      <table className="w-full text-left text-xs">
        <caption className="sr-only">Histórico de consentimentos do lead</caption>
        <thead className="border-b border-borda bg-superficie-2 text-texto-3">
          <tr>
            <th scope="col" className="px-3 py-2 font-medium">Registro</th>
            <th scope="col" className="px-3 py-2 font-medium">Data</th>
            <th scope="col" className="px-3 py-2 font-medium">Origem</th>
            <th scope="col" className="px-3 py-2 font-medium">Termo</th>
            <th scope="col" className="px-3 py-2 font-medium">Evidência</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-borda align-top">
          {historico.map((registro) => (
            <tr key={registro.id}>
              <td className="px-3 py-2">
                {registro.aceito ? <Badge variant="green">Aceite</Badge> : <Badge variant="red">Revogação</Badge>}
              </td>
              <td className="px-3 py-2 whitespace-nowrap text-texto-2">{dataHora(registro.registradoEm)}</td>
              <td className="px-3 py-2 text-texto-2">
                {NOME_ORIGEM_CONSENTIMENTO[registro.origem] ?? registro.origem}
                {registro.formularioId && (
                  <span className="block font-mono text-[11px] text-texto-3" title={registro.formularioId}>
                    formulário {registro.formularioId.slice(0, 8)}
                  </span>
                )}
              </td>
              <td className="max-w-64 px-3 py-2 text-texto-2">
                {registro.textoTermo ?? <span className="text-texto-3">—</span>}
              </td>
              <td className="px-3 py-2 text-texto-3">
                {registro.ip || registro.userAgent ? (
                  <>
                    {registro.ip && <span className="block font-mono">{registro.ip}</span>}
                    {registro.userAgent && <span className="block max-w-56 break-words">{registro.userAgent}</span>}
                  </>
                ) : (
                  '—'
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

type Busca<T> = { situacao: 'carregando' } | { situacao: 'indisponivel' } | { situacao: 'ok'; dados: T };

/** Busca um resumo no CRM; qualquer falha vira "indisponível" sem derrubar o detalhe (§9.6). */
function useResumoDoCrm<T>(id: string | null, buscar: (id: string) => Promise<T>): Busca<T> | null {
  const [busca, setBusca] = useState<Busca<T>>({ situacao: 'carregando' });
  useEffect(() => {
    if (!id) return;
    let ativo = true;
    setBusca({ situacao: 'carregando' });
    buscar(id)
      .then((dados) => ativo && setBusca({ situacao: 'ok', dados }))
      .catch(() => ativo && setBusca({ situacao: 'indisponivel' }));
    return () => {
      ativo = false;
    };
    // `buscar` é uma função de módulo, estável
  }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  return id ? busca : null;
}

/**
 * Seção CRM: a oportunidade ligada pelo evento `crm.oportunidade.criada`, mais a razão social e o
 * contato lidos do CRM (resumos buscados com o token do usuário, §9.2). Falha do CRM aparece como
 * "indisponível no CRM", só naquele campo.
 */
export function DadosDoCrm({ crm }: { crm: DetalheLead['crm'] }) {
  const empresa = useResumoDoCrm<ResumoEmpresaCrm>(crm.empresaId, resumirEmpresaNoCrm);
  const contato = useResumoDoCrm<ResumoContatoCrm>(crm.contatoId, resumirContatoNoCrm);

  if (!crm.oportunidadeId && !crm.empresaId && !crm.contatoId) {
    return (
      <p className="text-sm text-texto-3 sm:col-span-2">
        Ainda sem oportunidade no CRM. Ela nasce quando o lead envia o formulário.
      </p>
    );
  }

  return (
    <>
      <DetailField label="Oportunidade" colSpan={2}>
        {crm.oportunidadeId && <span className="font-mono text-xs break-all">{crm.oportunidadeId}</span>}
      </DetailField>
      <DetailField label="Empresa">
        {empresa === null ? null : empresa.situacao === 'carregando' ? (
          <span className="text-texto-3">Carregando…</span>
        ) : empresa.situacao === 'indisponivel' ? (
          <ResumoIndisponivel />
        ) : (
          <span className="inline-flex items-start gap-1.5">
            <Building2 aria-hidden className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              {empresa.dados.razaoSocial}
              {(empresa.dados.cnpj || empresa.dados.cidade) && (
                <span className="block text-xs text-texto-3">
                  {[empresa.dados.cnpj, empresa.dados.cidade].filter(Boolean).join(' · ')}
                </span>
              )}
            </span>
          </span>
        )}
      </DetailField>
      <DetailField label="Contato">
        {contato === null ? null : contato.situacao === 'carregando' ? (
          <span className="text-texto-3">Carregando…</span>
        ) : contato.situacao === 'indisponivel' ? (
          <ResumoIndisponivel />
        ) : (
          <span>
            {contato.dados.nome}
            {contato.dados.cargo && <span className="text-texto-3"> · {contato.dados.cargo}</span>}
            {(contato.dados.email || contato.dados.telefone) && (
              <span className="block text-xs text-texto-3">
                {[contato.dados.email, contato.dados.telefone].filter(Boolean).join(' · ')}
              </span>
            )}
          </span>
        )}
      </DetailField>
    </>
  );
}

function ResumoIndisponivel() {
  return (
    <span className="inline-flex items-center gap-1 text-texto-3">
      <AlertCircle aria-hidden className="h-3.5 w-3.5 shrink-0" />
      indisponível no CRM
    </span>
  );
}
