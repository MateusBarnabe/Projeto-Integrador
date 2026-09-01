## 8. Segurança, Privacidade & Conformidade (LGPD)

### 8.1. Consentimento e Gestão de Opt-in
* **Aceite Explícito:** Checkbox desmarcado por padrão (*Opt-in* ativo) com link direto para a Política de Privacidade e Termos de Uso vigentes.
* **Registro de Evidência (Log de Aceite):** A cada submissão, a automação deve armazenar:
  * Data e hora exata do envio (`UTC`).
  * Endereço IP e *User-Agent* do dispositivo.
  * Versão do termo aceito pelo usuário.
* **Mecanismo de Opt-out:** Todos os e-mails automatizados devem conter link de descadastro (*Unsubscribe*) em 1 clique. As mensagens de WhatsApp devem respeitar comandos de parada (ex.: "PARAR" ou "SAIR").

### 8.2. Segurança no Tráfego e Armazenamento
* **Criptografia:** Tráfego 100% via HTTPS/TLS 1.3. Dados em repouso no banco de dados com criptografia padrão (AES-256).
* **Anonimização e Descarte:** Leads inativos há mais de 12 meses sem qualquer interação devem ter seus dados pessoais anonimizados ou expurgados automaticamente das bases ativas de marketing.
* **Controle de Acesso:** Acesso aos dados de leads restrito por perfis (RBAC), garantindo que apenas operadores autorizados visualizem contatos telefônicos e e-mails.
