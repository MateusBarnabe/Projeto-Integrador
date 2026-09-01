## 6. Levantamento de Requisitos

### 6.1. Requisitos Funcionais (RF)

| ID | Nome | Descrição | Prioridade |
| :--- | :--- | :--- | :--- |
| **RF01** | Captura de UTMs | O formulário deve capturar automaticamente via campos ocultos (*hidden fields*) os parâmetros `utm_source`, `utm_medium`, `utm_campaign`, `utm_content` e `utm_term` presentes na URL. | Alta |
| **RF02** | Validação de Dados de Entrada | O front-end e a camada de ingestão devem validar formatos de e-mail válidos e números de telefone com DDD/código de país (máscaras e Regex). | Alta |
| **RF03** | Roteamento e Webhook | Ao submeter o formulário, os dados devem ser despachados em tempo real via Webhook/API para a plataforma de automação/CRM. | Alta |
| **RF04** | Classificação Automática (Scoring) | O sistema de automação deve categorizar o lead como **MQL** ou **Nutrição/Incompleto** com base nas respostas e preferências fornecidas. | Alta |
| **RF05** | Notificação Comercial | A automação deve notificar o time de vendas (via CRM, canal interno ou WhatsApp) imediatamente após a entrada de um lead qualificado. | Alta |
| **RF06** | Disparo de Mensagem Transacional | O lead deve receber uma mensagem automática de boas-vindas/confirmação (via WhatsApp ou e-mail) nos primeiros minutos após o envio. | Média |
| **RF07** | Deduplicação de Leads | Se um lead submeter o formulário mais de uma vez, os dados anteriores devem ser atualizados no CRM sem gerar registros duplicados (identificação por e-mail/telefone). | Alta |
| **RF08** | Redirecionamento Pós-Envio | Após o envio bem-sucedido, o usuário deve ser redirecionado para a Equipe Comercial/Contratos disparando o evento de conversão configurado. | Média |

---

### 6.2. Requisitos Não Funcionais (RNF)

| ID | Categoria | Descrição |
| :--- | :--- | :--- |
| **RNF01** | **Performance & Latência** | O tempo de resposta do webhook de recepção não deve exceder 2 segundos para evitar *timeout* no front-end. |
| **RNF02** | **Disponibilidade (Uptime)** | As Landing Pages e endpoints de recepção devem manter disponibilidade mínima de 99,5%. |
| **RNF03** | **Segurança & Criptografia** | Todo o tráfego de dados entre cliente, servidor e automações deve trafegar obrigatoriamente sob protocolo HTTPS/TLS. |
| **RNF04** | **Resiliência e Retries** | O sistema de automação/webhooks deve possuir política de tentativas automáticas (*retries* com *exponential backoff*) em caso de falhas temporárias na API de destino. |
| **RNF05** | **Responsividade** | O formulário e a Landing Page devem ser 100% responsivos e otimizados para dispositivos móveis (Mobile First). |
| **RNF06** | **Limitação** | O formulário e dados enviados devem ter limitadores internos para impossibilitar com que haja requisições maliciosas, bloqueando o IP. |

---

### 6.3. Requisitos de Conformidade & LGPD (RC)

* **RC01 - Consentimento Explícito:** O formulário deve conter checkbox obrigatório de aceite aos Termos de Uso e Política de Privacidade antes da submissão.
* **RC02 - Opt-in de Comunicação:** Registro claro da concordância do usuário em receber mensagens via WhatsApp/E-mail.
* **RC03 - Retenção e Logs:** Armazenamento de logs de consentimento (data/hora, IP e formulário de origem) para auditoria.

---

### 6.4. Requisitos de Negócio & SLAs (RN)

* **RN01 - SLA de Primeiro Contato (MQL):** Leads classificados como MQL devem ser abordados pelo mensagem de confirmação em no máximo **5 minutos** em horário comercial.
* **RN02 - Fila de Distribuição:** A distribuição de novos leads na Triagem de contato deve seguir a lógica de rodízio (*Round-Robin*) equilibrada.
* **RN03 - Janela de Não Perturbe:** Mensagens automáticas via WhatsApp só devem ser disparadas entre as 08:00 e 20:00 (leads fora desse horário aguardam o próximo dia útil), E-mails não tendo o mesmo padrão.