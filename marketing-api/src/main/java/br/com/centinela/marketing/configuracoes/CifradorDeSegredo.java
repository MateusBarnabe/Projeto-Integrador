package br.com.centinela.marketing.configuracoes;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra e decifra segredos de canais de anúncio em repouso (AES-256-GCM, D6, norteador §3 e §9.4).
 * Os segredos cifrados nunca são devolvidos pela API.
 *
 * <p>A chave vem de {@code MARKETING_CHAVE_CIFRA} e não tem valor padrão fora do perfil {@code dev}:
 * uma chave escrita no repositório não protege nada. Sem ela, a aplicação sobe normalmente, mas
 * cifrar ou decifrar um segredo lança {@link ChaveDeCifraAusente}, e a API responde {@code 503}.
 */
@Component
public class CifradorDeSegredo {

    private static final Logger LOG = LoggerFactory.getLogger(CifradorDeSegredo.class);

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Nulo quando a chave não foi configurada. */
    private final SecretKey chave;

    public CifradorDeSegredo(@Value("${marketing.seguranca.chave-cifra:}") String segredoChave) {
        this.chave = derivarChave(segredoChave);
        if (chave == null) {
            LOG.warn("MARKETING_CHAVE_CIFRA não configurada: os segredos dos canais de anúncio não podem ser gravados.");
        }
    }

    /** Exceção de quando falta a chave de cifra no ambiente. */
    public static class ChaveDeCifraAusente extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ChaveDeCifraAusente() {
            super("Chave de cifra (MARKETING_CHAVE_CIFRA) não configurada.");
        }
    }

    public boolean configurado() {
        return chave != null;
    }

    public String cifrar(String textoAberto) {
        if (textoAberto == null || textoAberto.isBlank()) {
            return null;
        }
        SecretKey chaveAtual = exigirChave();
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chaveAtual, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoAberto.strip().getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cifrado.length);
            buffer.put(iv);
            buffer.put(cifrado);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("Não foi possível cifrar o segredo.", erro);
        }
    }

    public String decifrar(String textoCifrado) {
        if (textoCifrado == null || textoCifrado.isBlank()) {
            return null;
        }
        SecretKey chaveAtual = exigirChave();
        try {
            byte[] dados = Base64.getDecoder().decode(textoCifrado);
            if (dados.length <= TAMANHO_IV_BYTES) {
                throw new IllegalArgumentException("Segredo cifrado inválido.");
            }
            ByteBuffer buffer = ByteBuffer.wrap(dados);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chaveAtual, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException erro) {
            throw new IllegalStateException("Não foi possível decifrar o segredo.", erro);
        }
    }

    private SecretKey exigirChave() {
        if (chave == null) {
            throw new ChaveDeCifraAusente();
        }
        return chave;
    }

    private static SecretKey derivarChave(String segredo) {
        if (segredo == null || segredo.isBlank()) {
            return null;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(segredo.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("SHA-256 indisponível.", erro);
        }
    }
}
