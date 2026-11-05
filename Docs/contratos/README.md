# Contratos do Marketing

O que o Marketing (Grupo 4) combina com os outros grupos. Aqui fica só o que atravessa a fronteira do módulo. Tudo o que é interno está no [documento norteador](../documento-norteador.md), que é a fonte da verdade do grupo.

| Arquivo | Para quem | O que é | Situação |
|---|---|---|---|
| [marketing.yaml](marketing.yaml) | Casca e demais módulos | Nossos endpoints consumidos fora do módulo, em OpenAPI 3.1: saúde, busca global e resumo do lead (individual e em lote) | Publicado no infra (0.2.0) |
| [marketing.asyncapi.yaml](marketing.asyncapi.yaml) | Demais módulos | Eventos publicados pelo Marketing no RabbitMQ em AsyncAPI 3.0. Hoje sem mensagens: os eventos de lead entram quando alguém confirmar que consome | Publicado no infra (0.2.0) |
| [landing-requisitos.md](landing-requisitos.md) | Grupo 7 — Landing | O que consumimos do contrato do Landing e o que ainda pedimos (`sessaoId` no envio, `origemModuloId` da oportunidade, IP e *User-Agent* do aceite, fonte para a etapa 2) | A enviar |

`marketing.yaml` e `marketing.asyncapi.yaml` são cópias dos arquivos em `contratos/` do `infra-integrador-2026`. Mudou um, mude o outro no mesmo PR.

## Regras seguidas

Seguem o `contratos/README.md` do `infra-integrador-2026` e o Contrato de Integração v0.7:

- Só entra o que outro módulo realmente consome. Endpoint usado apenas pelo nosso front não é contrato (§14.1).
- Envelope `{ success, data, message, errors }` e códigos da §8.4 em todo endpoint; envelope da §9.7 em todo evento.
- Mudança incompatível publica uma `v2` **ao lado** da atual, até os consumidores migrarem.
- Eventos e rotas de outro módulo são do dono dele. O que pedimos ao Landing é **pedido**: quem decide nome e formato final é o Grupo 7.

## O que consumimos de outros módulos

| Módulo | O quê | Contrato |
|---|---|---|
| Landing | `landing.formulario.recebido`, `landing.chatbot.lead_capturado`, `GET /api/landing/envios/{id}`, `GET /api/landing/formularios/{id}/resumo` | `contratos/landing.yaml` e `landing.asyncapi.yaml` do infra |
| CRM | `crm.oportunidade.criada`; resumo de empresa e contato | `contratos/crm.yaml` e `crm.asyncapi.yaml` do infra |
| Identity | resolver o tenant pelo subdomínio, notificação e timeline | `contratos/identity.yaml` e `identity.asyncapi.yaml` do infra |

## Ainda não existe

- `marketing.views.md`: só se o Grupo 3 pedir *views* `vw_pub_*` para relatórios.
- Eventos de lead no AsyncAPI (`marketing.lead.qualificado`, `marketing.lead.atribuido`, `marketing.lead.descadastrado`): entram quando os módulos consumidores confirmarem interesse.
- Pedido ao CRM de uma ação para gravar o responsável da oportunidade (norteador, seção 6): ainda sem documento próprio.
