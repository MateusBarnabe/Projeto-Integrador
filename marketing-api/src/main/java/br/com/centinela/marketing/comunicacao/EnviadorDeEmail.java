package br.com.centinela.marketing.comunicacao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.centinela.marketing.comunicacao.ClienteSmtp.MensagemSmtp;
import br.com.centinela.marketing.leads.Lead;

/**
 * Monta e envia e-mails de boas-vindas (4.5) e de aquecimento (4.2) por SMTP (Mailpit em
 * desenvolvimento, norteador §4.2 e §4.5). Substitui {@code {{nome}}} e {@code {{origem}}} e inclui
 * sempre o link de descadastro {@code /public/marketing/descadastro/{token_descadastro}}.
 */
@Component
public class EnviadorDeEmail {

    private final ClienteSmtp smtp;
    private final String remetente;
    private final String urlPublica;

    public EnviadorDeEmail(ClienteSmtp smtp,
            @Value("${marketing.comunicacao.remetente:marketing@centinela.local}") String remetente,
            @Value("${marketing.rastreamento.url-publica:http://localhost:8087}") String urlPublica) {
        this.smtp = smtp;
        this.remetente = remetente;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    /** Conteúdo final do e-mail, já com as variáveis do lead e o rodapé de descadastro. */
    public record EmailRenderizado(String destinatario, String assunto, String corpo, String urlDescadastro) {
    }

    public EmailRenderizado renderizar(ModeloMensagem modelo, Lead lead) {
        String nome = lead.getNome() != null && !lead.getNome().isBlank() ? lead.getNome().strip() : "cliente";
        String origem = origemLegivel(lead);
        String urlDescadastro = urlDescadastro(lead.getTokenDescadastro());
        String assunto = aplicarVariaveis(modelo.getAssunto(), nome, origem);
        String corpoBase = aplicarVariaveis(modelo.getCorpo(), nome, origem);
        String corpo = corpoBase.contains(urlDescadastro)
                ? corpoBase
                : corpoBase + "\n\n---\nPara não receber novas comunicações, acesse: " + urlDescadastro;
        return new EmailRenderizado(lead.getEmail(), assunto, corpo, urlDescadastro);
    }

    public String urlDescadastro(String tokenDescadastro) {
        return urlPublica + "/public/marketing/descadastro/" + tokenDescadastro;
    }

    public EmailRenderizado enviar(ModeloMensagem modelo, Lead lead) {
        EmailRenderizado email = renderizar(modelo, lead);
        smtp.enviar(new MensagemSmtp(remetente, email.destinatario(), email.assunto(), email.corpo()));
        return email;
    }

    private static String aplicarVariaveis(String texto, String nome, String origem) {
        if (texto == null) {
            return "";
        }
        return texto
                .replace("{{nome}}", nome)
                .replace("{{origem}}", origem);
    }

    private static String origemLegivel(Lead lead) {
        if (lead.getUtmSource() != null && !lead.getUtmSource().isBlank()) {
            return lead.getUtmSource().strip();
        }
        if (lead.getCanal() != null) {
            return switch (lead.getCanal()) {
                case CHATBOT -> "nosso chatbot";
                case LANDING_PAGE -> "nossa página";
                case FORMULARIO, EMBED -> "nosso formulário";
            };
        }
        if (lead.getOrigem() != null) {
            return switch (lead.getOrigem()) {
                case META_ADS -> "Meta Ads";
                case LANDING_PAGE -> "nossa página";
                case MANUAL -> "atendimento";
            };
        }
        return "nosso site";
    }
}
