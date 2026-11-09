import { useEffect, useState, type FormEvent } from 'react';
import { ErroApi } from '@/api/cliente';
import { Button, FormField, FormSection, Input, Modal } from '@/components/ui';
import { alterarLink, criarLink, type LinkCampanha, type PedidoLink } from './api';

const VAZIO: PedidoLink = {
  nome: '',
  urlDestino: '',
  utmSource: '',
  utmMedium: '',
  utmCampaign: '',
  utmTerm: '',
  utmContent: '',
  ativo: true,
};

/** Criar ou editar um link de campanha (Modal xl com título, Design System 5.1). */
export function LinkModal({
  aberto,
  link,
  onClose,
  onSalvo,
}: {
  aberto: boolean;
  /** Nulo para criar. */
  link: LinkCampanha | null;
  onClose: () => void;
  onSalvo: (link: LinkCampanha) => void;
}) {
  const [valores, setValores] = useState<PedidoLink>(VAZIO);
  const [erro, setErro] = useState<string | null>(null);
  const [errosCampo, setErrosCampo] = useState<Record<string, string>>({});
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!aberto) return;
    setErro(null);
    setErrosCampo({});
    setValores(link ? { ...VAZIO, ...link } : VAZIO);
  }, [aberto, link]);

  const campo = (chave: keyof PedidoLink) => ({
    value: (valores[chave] as string | null) ?? '',
    onChange: (evento: { target: { value: string } }) => setValores((atual) => ({ ...atual, [chave]: evento.target.value })),
  });

  async function salvar(evento: FormEvent) {
    evento.preventDefault();
    setSalvando(true);
    setErro(null);
    setErrosCampo({});
    try {
      const salvo = link ? await alterarLink(link.id, valores) : await criarLink(valores);
      onSalvo(salvo);
    } catch (falha) {
      if (falha instanceof ErroApi) {
        setErro(falha.erros.find((e) => !e.campo)?.detalhe ?? falha.message);
        setErrosCampo(Object.fromEntries(falha.erros.filter((e) => e.campo).map((e) => [e.campo, e.detalhe])));
      } else {
        setErro('Não foi possível salvar o link.');
      }
    } finally {
      setSalvando(false);
    }
  }

  return (
    <Modal
      open={aberto}
      onClose={onClose}
      title={link ? 'Editar link de campanha' : 'Novo link de campanha'}
      subtitle="O anúncio aponta para o link; o clique registra a visita e segue para a landing page."
      footer={
        <>
          <Button variant="outline" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="form-link" disabled={salvando}>
            {salvando ? 'Salvando…' : 'Salvar'}
          </Button>
        </>
      }
    >
      <form id="form-link" onSubmit={salvar} noValidate>
        {erro && (
          <p role="alert" className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/15 dark:text-red-300">
            {erro}
          </p>
        )}
        <FormSection number={1} title="Destino">
          <FormField label="Nome" required error={errosCampo.nome} hint="Para você achar o link, ex.: Black Friday · Google">
            <Input {...campo('nome')} data-autofocus maxLength={120} />
          </FormField>
          <FormField label="URL da landing page" required error={errosCampo.urlDestino} colSpan={2}>
            <Input {...campo('urlDestino')} type="url" placeholder="http://localhost:3008/" />
          </FormField>
        </FormSection>
        <FormSection number={2} title="Campanha (UTM acrescentada à URL)">
          <FormField label="utm_source" error={errosCampo.utmSource}>
            <Input {...campo('utmSource')} placeholder="google" />
          </FormField>
          <FormField label="utm_medium" error={errosCampo.utmMedium}>
            <Input {...campo('utmMedium')} placeholder="cpc" />
          </FormField>
          <FormField label="utm_campaign" error={errosCampo.utmCampaign}>
            <Input {...campo('utmCampaign')} placeholder="black-friday-2026" />
          </FormField>
          <FormField label="utm_content" error={errosCampo.utmContent}>
            <Input {...campo('utmContent')} placeholder="criativo-a" />
          </FormField>
        </FormSection>
      </form>
    </Modal>
  );
}
