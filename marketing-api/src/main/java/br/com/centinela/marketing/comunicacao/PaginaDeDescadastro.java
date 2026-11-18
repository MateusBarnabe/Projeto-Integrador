package br.com.centinela.marketing.comunicacao;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Página HTML do descadastro (E7, norteador §6), para quem abre o link do e-mail no navegador. Não
 * mostra nada do lead, nem o token: a mesma página serve para token válido, inválido ou já usado.
 * Sem script; o botão é um formulário que faz {@code POST} na própria URL.
 */
final class PaginaDeDescadastro {

    static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    /** Sem script nem recurso externo; o token na URL não vaza por {@code Referer} nem fica em cache. */
    private static final String POLITICA = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'";

    private PaginaDeDescadastro() {
    }

    static ResponseEntity<String> confirmacao() {
        return pagina("Descadastro de e-mails",
                "Confirme para deixar de receber nossas comunicações de marketing por e-mail.",
                """
                <form method="post">
                  <button type="submit">Confirmar descadastro</button>
                </form>
                """);
    }

    static ResponseEntity<String> concluido() {
        return pagina("Descadastro registrado",
                "Se o endereço constava na nossa lista, você não vai mais receber nossas comunicações de marketing. "
                        + "Pode fechar esta página.",
                "");
    }

    private static ResponseEntity<String> pagina(String titulo, String mensagem, String acao) {
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>%s</title>
                <style>
                  :root { color-scheme: light dark; --fundo: #f4f5f7; --cartao: #ffffff; --texto: #1f2933;
                          --suave: #52606d; --marca: #2563eb; --marca-texto: #ffffff; }
                  @media (prefers-color-scheme: dark) {
                    :root { --fundo: #111827; --cartao: #1f2937; --texto: #f3f4f6; --suave: #9ca3af;
                            --marca: #3b82f6; }
                  }
                  * { box-sizing: border-box; }
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center; padding: 16px;
                         background: var(--fundo); color: var(--texto);
                         font: 16px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
                  main { width: 100%%; max-width: 440px; background: var(--cartao); border-radius: 12px;
                         padding: 32px 24px; box-shadow: 0 1px 3px rgba(0, 0, 0, .12); }
                  h1 { margin: 0 0 12px; font-size: 1.35rem; }
                  p { margin: 0; color: var(--suave); }
                  form { margin-top: 24px; }
                  button { width: 100%%; padding: 12px 16px; border: 0; border-radius: 8px; cursor: pointer;
                           background: var(--marca); color: var(--marca-texto); font: inherit; font-weight: 600; }
                  button:focus-visible { outline: 3px solid var(--texto); outline-offset: 2px; }
                </style>
                </head>
                <body>
                <main>
                  <h1>%s</h1>
                  <p>%s</p>
                  %s
                </main>
                </body>
                </html>
                """.formatted(titulo, titulo, mensagem, acao);
        return ResponseEntity.ok()
                .contentType(HTML)
                .header("Content-Security-Policy", POLITICA)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Robots-Tag", "noindex")
                .body(html);
    }
}
