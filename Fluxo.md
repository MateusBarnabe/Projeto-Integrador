
# Documentação Técnica Geral: Marketing, Landing Pages & Automação de Leads

> **Contexto:** Este módulo documenta o ciclo de vida do lead, desde a origem de tráfego (UTMs) até o roteamento para a equipe de atendimento/vendas, cobrindo as integrações entre Landing Pages, Formulários e Automações.

---

## 1. Aquisição & Rastreamento de Tráfego

* **Segmentação & Nichos:** Definição das campanhas externas segmentadas por público-alvo e canal (Google Ads, Meta Ads, Tráfego Orgânico, Parcerias).
* **Padronização de Parâmetros UTM:** Rastreamento obrigatório de ponta a ponta para identificar a origem e a qualidade de cada conversão.

| Parâmetro | Finalidade | Exemplo |
| :--- | :--- | :--- |
| `utm_source` | Plataforma de origem | `google`, `meta`, `instagram` |
| `utm_medium` | Tipo de mídia | `cpc`, `stories`, `email` |
| `utm_campaign` | Nome identificador da campanha | `blackfriday_2026`, `institucional` |
| `utm_content` | Variação do criativo/anúncio | `video_01`, `banner_azul` |

---

## 2. Conversão & Captação (Landing Pages & Formulários)

* **Landing Pages (LPs):** Estruturas focadas em conversão, com proposta de valor clara e tags de monitoramento (Google Tag Manager, Meta Pixel).
* **Formulários Dinâmicos / Progressivos:**
  * Coleta de dados de contato (Nome, E-mail, WhatsApp).
  * Coleta de dados de qualificação (Orçamento, Necessidade, Etapas preenchidas, Preferências).
  * Captura de campos ocultos (*Hidden Fields*) para registrar as UTMs automaticamente no envio.
* **Recepção e Confirmação:** Redirecionamento para o Comercial/Contratos e disparo de eventos.

---

## 3. Qualificação, Triagem & Roteamento (Automação)

A automação recebe o webhook de dados do formulário e processa o lead com base em regras de negócio predefinidas.

### 3.1. Classificação do Lead
* **MQL (Marketing Qualified Lead):** Atende aos critérios mínimos de maturidade e perfil ideal.
* **Nutrição / Incompleto:** Preenchimento parcial ou lead fora do perfil atual (enviado para fluxo de nutrição).

### 3.2. Fluxo de Tratamento de Dados

```text
[Envio do Formulário]
       │
       ▼
[Webhook / API de Entrada]
       │
       ├── Validação de E-mail / Telefone
       ├── Ingestão de Tags e UTMs
       └── Classificação de Perfil (Scoring / Triagem)
       │
       ▼
[Distribuição Comercial]
       │
       ├── Notificação Interna (Slack / Teams / WhatsApp)
       └── Criação de Negócio no CRM / SDR
```

---

## 4. Ativação Comercial & Engajamento

* **Disparo Imediato (Automação):** Mensagens transacionais ou de boas-vindas via WhatsApp/E-mail nos primeiros minutos após a conversão.
* **Facilitação do Contato Comercial:**
  * Notificação direta para a equipe comercial com resumo do lead e preferências preenchidas.
  * Templates de abordagem pré-formatados conforme o lead/campanha de origem.
  * Facilitação de contato direto com o Comercial/Contratos

---

## 5. Governança, Métricas & Gestão

* **Métricas de Performance:**
  * Taxa de Conversão da LP (`Visitantes` → `Leads`).
  * Taxa de Qualificação (`Leads` → `MQLs`).
  * Taxa de Contato / Resposta.
* **Gestão de Custos & Campanhas:**
  * Acompanhamento de Custo por Lead (CPL) e Custo por Lead Qualificado por canal.
  * Monitoramento de ROI/ROAS para suporte à tomada de decisão orçamentária.

```