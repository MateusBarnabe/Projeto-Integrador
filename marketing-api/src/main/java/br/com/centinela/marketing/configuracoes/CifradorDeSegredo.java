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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra e decifra segredos de canais de anúncio em repouso (AES-256-GCM, D6, norteador §3 e §9.4).
 * Os segredos cifrados nunca são devolvidos pela API.
 */
@Component
public class CifradorDeSegredo {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKey chave;

    public CifradorDeSegredo(
            @Value("${marketing.seguranca.chave-cifra:marketing-dev-chave-local-2026}") String segredoChave) {
        this.chave = derivarChave(segredoChave);
    }

    public String cifrar(String textoAberto) {
        if (textoAberto == null || textoAberto.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
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
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(cifrado), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException erro) {
            throw new IllegalStateException("Não foi possível decifrar o segredo.", erro);
        }
    }

    private static SecretKey derivarChave(String segredo) {
        try {
            String fonte = segredo == null || segredo.isBlank() ? "marketing-dev-chave-local-2026" : segredo;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(fonte.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (GeneralSecurityException erro) {
            throw new IllegalStateException("SHA-256 indisponível.", erro);
        }
    }
}
