## 7. Regras de Negócio Detalhadas

### 7.1. Critérios de Pontuação (Lead Scoring)

A matriz de pontuação define a maturidade do lead antes do roteamento, combinando **Perfil Demográfico/Empresarial** e **Engajamento/Intenção de Compra**.

| Critério | Condição / Resposta | Pontuação |
| :--- | :--- | :--- |
| **Cargo / Papel** | Decisor / Sócio / Gerente | `+30 pts` |
| **Cargo / Papel** | Analista / Operacional / Outros | `+10 pts` |
| **Orçamento / Budget** | Acima do ticket mínimo ideal | `+30 pts` |
| **Orçamento / Budget** | Abaixo do ticket mínimo ou Não informado | `0 pts` |
| **Prazo de Compra** | Imediato / Até 30 dias | `+25 pts` |
| **Prazo de Compra** | Mais de 3 meses / Apenas pesquisando | `+5 pts` |
| **Origem do Tráfego** | Fundo de Funil (Google Search / Direto) | `+15 pts` |
| **Origem do Tráfego** | Topo de Funil (Display / Redes Sociais) | `+5 pts` |

#### Faixas de Classificação
* **Score ≥ 70 pts (MQL - Quente):** Envio prioritário e imediato para a equipe de vendas.
* **Score entre 40 e 69 pts (MQL - Morno):** Entrada na fila de atendimento padrão ou qualificação assistida.
* **Score < 40 pts (Nutrição/Frio):** Roteamento para réguas automatizadas de e-mail/conteúdo; não entra na fila comercial imediata.

---

### 7.2. Fila e Distribuição de Leads (Roteamento Comercial)

* **Algoritmo de Rodízio (*Round-Robin*):** Os leads MQL são distribuídos sequencialmente e de forma igualitária entre os vendedores ativos na escala.
* **Critério de Disponibilidade:**
  * O lead só é atribuído a vendedores com status "Online/Disponível" no CRM.
  * Fora do horário comercial (ex.: fins de semana ou após as 18h), o lead é atribuído à fila geral com agendamento de abordagem para as 08:00 do próximo dia útil.
* **Regra de Transbordo (SLA de Resposta):**
  * Se o vendedor atribuído não realizar o primeiro contato em até **15 minutos**, o lead é reatribuído automaticamente ao próximo vendedor da fila (*Fallback Round-Robin*).

