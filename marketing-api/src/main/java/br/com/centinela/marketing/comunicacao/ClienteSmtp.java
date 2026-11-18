package br.com.centinela.marketing.comunicacao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cliente SMTP para envio de e-mails de boas-vindas (4.5) e de aquecimento (4.2), apontando para o
 * Mailpit em desenvolvimento ({@code MAIL_HOST:MAIL_PORT}, padrão {@code localhost:1025}).
 */
public interface ClienteSmtp {

    record MensagemSmtp(String remetente, String destinatario, String assunto, String corpo) {
    }

    void enviar(MensagemSmtp mensagem);

    @Component
    class Padrao implements ClienteSmtp {

        private static final int TIMEOUT_MS = 3000;

        private final String host;
        private final int porta;

        public Padrao(
                @Value("${spring.mail.host:localhost}") String host,
                @Value("${spring.mail.port:1025}") int porta) {
            this.host = host;
            this.porta = porta;
        }

        @Override
        public void enviar(MensagemSmtp mensagem) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, porta), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
                BufferedReader leitor = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter escritor = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                esperarCodigo(leitor, "220");
                comando(escritor, leitor, "EHLO marketing.local", "250");
                comando(escritor, leitor, "MAIL FROM:<" + limparEndereco(mensagem.remetente()) + ">", "250");
                comando(escritor, leitor, "RCPT TO:<" + limparEndereco(mensagem.destinatario()) + ">", "250");
                comando(escritor, leitor, "DATA", "354");

                String assuntoEncoded = "=?UTF-8?B?"
                        + Base64.getEncoder().encodeToString(mensagem.assunto().getBytes(StandardCharsets.UTF_8))
                        + "?=";
                String dataRfc = DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC));
                escritor.write("From: " + mensagem.remetente() + "\r\n");
                escritor.write("To: " + mensagem.destinatario() + "\r\n");
                escritor.write("Subject: " + assuntoEncoded + "\r\n");
                escritor.write("Date: " + dataRfc + "\r\n");
                escritor.write("Message-ID: <" + UUID.randomUUID() + "@marketing.local>\r\n");
                escritor.write("MIME-Version: 1.0\r\n");
                escritor.write("Content-Type: text/plain; charset=UTF-8\r\n");
                escritor.write("Content-Transfer-Encoding: 8bit\r\n\r\n");

                for (String linha : mensagem.corpo().split("\r?\n", -1)) {
                    if (linha.startsWith(".")) {
                        escritor.write(".");
                    }
                    escritor.write(linha + "\r\n");
                }
                comando(escritor, leitor, ".", "250");
                comando(escritor, leitor, "QUIT", "221");
            } catch (IOException falha) {
                throw new IllegalStateException("Falha no envio SMTP: " + falha.getClass().getSimpleName(), falha);
            }
        }

        private static void comando(BufferedWriter escritor, BufferedReader leitor, String cmd, String prefixoEsperado)
                throws IOException {
            escritor.write(cmd + "\r\n");
            escritor.flush();
            esperarCodigo(leitor, prefixoEsperado);
        }

        private static void esperarCodigo(BufferedReader leitor, String prefixoEsperado) throws IOException {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (!linha.startsWith(prefixoEsperado)) {
                    throw new IOException("Resposta SMTP inesperada: " + linha);
                }
                if (linha.length() < 4 || linha.charAt(3) != '-') {
                    return;
                }
            }
            throw new IOException("Conexão SMTP encerrada prematuramente.");
        }

        private static String limparEndereco(String email) {
            return email == null ? "" : email.replaceAll("[\\r\\n<>]", "").strip();
        }
    }
}
