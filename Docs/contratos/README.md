# Contratos do Marketing

O que o Marketing (Grupo 4) combina com os outros grupos. Aqui fica só o que atravessa a fronteira do módulo. Tudo o que é interno está no [documento norteador](../documento-norteador.md), que é a fonte da verdade do grupo.

| Arquivo | Para quem | O que é | Situação |
|---|---|---|---|
| [landing-requisitos.md](landing-requisitos.md) | Grupo 7 — Landing | O que precisamos receber do Landing em cada etapa do lead (0 a 3), incluindo UTM, e como o Landing lê os campos do formulário definidos no Marketing | Rascunho |
| [landing-eventos-propostos.asyncapi.yaml](landing-eventos-propostos.asyncapi.yaml) | Grupo 7 — Landing | Os eventos acima em AsyncAPI 3.0, para o Landing incorporar ao `landing.asyncapi.yaml` dele | Rascunho |
| [marketing.yaml](marketing.yaml) | Casca e demais módulos | Nossos endpoints consumidos fora do módulo, em OpenAPI 3.1: saúde, busca global, definição do formulário (Landing) e resumo do lead | Rascunho |

## Regras seguidas

Seguem o `contratos/README.md` do `infra-integrador-2026` e o Contrato de Integração v0.7:

- Só entra o que outro módulo realmente consome. Endpoint usado apenas pelo nosso front não é contrato (§14.1).
- Envelope `{ success, data, message, errors }` e códigos da §8.4 em todo endpoint; envelope da §9.7 em todo evento.
- Mudança incompatível publica uma `v2` **ao lado** da atual, até os consumidores migrarem.
- Eventos de outro módulo são do dono dele. O que escrevemos para o Landing é **proposta**: quem decide nome e formato final é o Grupo 7.

## Ainda não existe

- `marketing.asyncapi.yaml`: começa com `marketing.formulario.publicado`, que tem o Landing como consumidor, assim que o Grupo 7 aceitar a proposta. Os eventos de lead (`marketing.lead.qualificado`, `marketing.lead.atribuido`, `marketing.lead.descadastrado`) entram quando um interessado confirmar.
- `marketing.views.md`: só se o Grupo 3 pedir *views* `vw_pub_*` para relatórios.

## Quando for para o `infra-integrador-2026`

Por enquanto estes arquivos vivem só aqui. No PR para o infra, `marketing.yaml` vai para `contratos/`, junto com `permissoes/marketing.yaml` e `modulos/marketing.json`, que hoje estão na seção 7 do documento norteador. Os arquivos `landing-*` não vão no nosso PR: são entregues ao Grupo 7.
