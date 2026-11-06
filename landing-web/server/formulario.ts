/*
 * Formulário de exemplo do placeholder. No Landing real, formulários e campos são criados pela
 * pessoa gestora no editor do Grupo 7; aqui existe um só, fixo, de um tenant de teste.
 * Nada disto vem do Marketing nem é lido por ele, a não ser pela API do contrato (landing.yaml).
 */

/** Tenants de teste do identity (infra-integrador-2026, docs/usuarios-de-teste.md). */
export const TENANT_EMPRESA_A = 'a0000000-0000-4000-8000-00000000000a';

export type TipoCampo =
  | 'TEXTO'
  | 'TEXTO_LONGO'
  | 'EMAIL'
  | 'TELEFONE'
  | 'CNPJ'
  | 'CPF'
  | 'NUMERO'
  | 'DATA'
  | 'LISTA_OPCOES'
  | 'MULTIPLA_ESCOLHA'
  | 'CAIXA_MARCACAO';

/** Schema `Campo` do landing.yaml, mais o texto da pergunta usado pelo chatbot. */
export interface Campo {
  id: string;
  ordem: number;
  rotulo: string;
  tipo: TipoCampo;
  obrigatorio: boolean;
  placeholder: string | null;
  opcoes: string[] | null;
  mapeamentoCrm: string | null;
  textoPergunta: string;
}

export interface Formulario {
  id: string;
  tenantId: string;
  chavePublica: string;
  nome: string;
  descricao: string | null;
  status: 'ATIVO' | 'INATIVO';
  modoExibicao: 'TRADICIONAL' | 'PASSO_A_PASSO' | 'CHATBOT';
  textoConsentimento: string;
  mensagemSucesso: string;
  urlRedirecionamento: string | null;
  campos: Campo[];
  criadoEm: string;
  atualizadoEm: string;
}

/** Landing page de teste onde o formulário está embutido (canal `landing_page`). */
export const PAGINA_EXEMPLO = {
  id: 'a0000000-0000-4000-8000-0000000000b1',
  slug: 'empresa-a-consultoria',
};

function campo(n: number, dados: Omit<Campo, 'id' | 'ordem' | 'placeholder' | 'opcoes'> & Partial<Campo>): Campo {
  return {
    id: `a0000000-0000-4000-8000-00000000c00${n}`,
    ordem: n,
    placeholder: null,
    opcoes: null,
    ...dados,
  };
}

export const FORMULARIO_EXEMPLO: Formulario = {
  id: 'a0000000-0000-4000-8000-0000000000f1',
  tenantId: TENANT_EMPRESA_A,
  chavePublica: 'fk_empresaA01',
  nome: 'Contato — Consultoria Empresa A',
  descricao: 'Formulário de exemplo do placeholder do Landing',
  status: 'ATIVO',
  modoExibicao: 'TRADICIONAL',
  textoConsentimento:
    'Autorizo a Empresa A a usar os meus dados para contato comercial sobre os seus serviços, conforme a Política de Privacidade. Posso revogar este consentimento a qualquer momento.',
  mensagemSucesso: 'Recebemos seus dados. Em breve entraremos em contato.',
  urlRedirecionamento: null,
  campos: [
    campo(1, { rotulo: 'Nome', tipo: 'TEXTO', obrigatorio: true, placeholder: 'Seu nome completo', mapeamentoCrm: 'contato.nome', textoPergunta: 'Olá! Como você se chama?' }),
    campo(2, { rotulo: 'E-mail', tipo: 'EMAIL', obrigatorio: true, placeholder: 'voce@empresa.com.br', mapeamentoCrm: 'contato.email', textoPergunta: 'Qual é o seu melhor e-mail?' }),
    campo(3, { rotulo: 'Telefone (WhatsApp)', tipo: 'TELEFONE', obrigatorio: false, placeholder: '62999998888', mapeamentoCrm: 'contato.telefone', textoPergunta: 'Tem um telefone ou WhatsApp para contato?' }),
    campo(4, { rotulo: 'Empresa', tipo: 'TEXTO', obrigatorio: true, placeholder: 'Nome da empresa', mapeamentoCrm: 'empresa.nome', textoPergunta: 'Em qual empresa você trabalha?' }),
    campo(5, {
      rotulo: 'Quantos funcionários a empresa tem?',
      tipo: 'LISTA_OPCOES',
      obrigatorio: true,
      opcoes: ['1 a 10', '11 a 50', '51 a 200', 'Mais de 200'],
      mapeamentoCrm: null,
      textoPergunta: 'Quantos funcionários a empresa tem?',
    }),
    campo(6, {
      rotulo: 'O que você quer melhorar?',
      tipo: 'MULTIPLA_ESCOLHA',
      obrigatorio: false,
      opcoes: ['Vendas', 'Processos', 'Financeiro', 'Marketing'],
      mapeamentoCrm: null,
      textoPergunta: 'O que você quer melhorar primeiro?',
    }),
    campo(7, {
      rotulo: 'Quando pretende começar?',
      tipo: 'LISTA_OPCOES',
      obrigatorio: true,
      opcoes: ['Imediatamente', 'Em até 3 meses', 'Só estou pesquisando'],
      mapeamentoCrm: null,
      textoPergunta: 'E quando pretende começar?',
    }),
    campo(8, { rotulo: 'Mensagem', tipo: 'TEXTO_LONGO', obrigatorio: false, mapeamentoCrm: null, textoPergunta: 'Quer deixar alguma mensagem?' }),
  ],
  criadoEm: '2026-11-05T12:00:00Z',
  atualizadoEm: '2026-11-05T12:00:00Z',
};

const FORMULARIOS = [FORMULARIO_EXEMPLO];

export function buscarPorChave(chavePublica: string): Formulario | undefined {
  return FORMULARIOS.find((f) => f.chavePublica === chavePublica && f.status === 'ATIVO');
}

export function buscarPorId(id: string): Formulario | undefined {
  return FORMULARIOS.find((f) => f.id === id);
}

/** `FormularioPublico`: só o necessário para renderizar, sem `mapeamentoCrm` nem dado interno. */
export function formularioPublico(formulario: Formulario) {
  return {
    chavePublica: formulario.chavePublica,
    nome: formulario.nome,
    modoExibicao: formulario.modoExibicao,
    textoConsentimento: formulario.textoConsentimento,
    campos: formulario.campos.map(({ mapeamentoCrm: _m, ...publico }) => publico),
  };
}
