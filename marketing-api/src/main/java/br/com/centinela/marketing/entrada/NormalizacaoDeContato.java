package br.com.centinela.marketing.entrada;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização de e-mail e telefone (norteador §3, "Normalização e identidade"). É a forma gravada
 * em {@code leads} e a forma usada para achar o lead, em toda entrada: chatbot (E1), envio (E5),
 * oportunidade (E6) e Meta (E4).
 *
 * <ul>
 * <li>E-mail: sem espaços, em minúsculas; sem um {@code @} entre duas partes não vazias, não serve
 * como identificador e vira nulo.</li>
 * <li>Telefone: só dígitos, em E.164 sem o {@code +} (o formato do Landing). Com {@code +} na frente, os
 * dígitos já são o E.164. Sem ele, zeros à esquerda (prefixo de discagem) saem e um número de 10 ou 11
 * dígitos (DDD + número, ex.: {@code 62999998888}) ganha o DDI 55. Com 12 ou 13 dígitos começando
 * por 55, o DDI já está lá. Fora de 8 a 15 dígitos, vira nulo.</li>
 * </ul>
 */
public final class NormalizacaoDeContato {

    public static final String DDI_BRASIL = "55";

    private static final Pattern ESPACOS = Pattern.compile("\\s+");
    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");
    private static final int MINIMO_DE_DIGITOS = 8;
    private static final int MAXIMO_DE_DIGITOS = 15; // E.164

    private NormalizacaoDeContato() {
    }

    /** E-mail sem espaços e em minúsculas; nulo se vazio ou sem a forma {@code local@dominio}. */
    public static String email(String bruto) {
        if (bruto == null) {
            return null;
        }
        String email = ESPACOS.matcher(bruto).replaceAll("").toLowerCase(Locale.ROOT);
        int arroba = email.indexOf('@');
        if (arroba <= 0 || arroba != email.lastIndexOf('@') || arroba == email.length() - 1) {
            return null;
        }
        return email;
    }

    /** Telefone em E.164 sem o {@code +}; nulo se vazio ou com dígitos de menos ou de mais. */
    public static String telefone(String bruto) {
        if (bruto == null) {
            return null;
        }
        String digitos = NAO_DIGITO.matcher(bruto).replaceAll("");
        if (!bruto.strip().startsWith("+")) {
            digitos = semZerosAEsquerda(digitos);
            if (digitos.length() == 10 || digitos.length() == 11) {
                digitos = DDI_BRASIL + digitos;
            }
        }
        if (digitos.length() < MINIMO_DE_DIGITOS || digitos.length() > MAXIMO_DE_DIGITOS) {
            return null;
        }
        return digitos;
    }

    /** Nome sem espaços nas pontas; nulo se vazio. */
    public static String nome(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        return bruto.strip();
    }

    private static String semZerosAEsquerda(String digitos) {
        int inicio = 0;
        while (inicio < digitos.length() && digitos.charAt(inicio) == '0') {
            inicio++;
        }
        return digitos.substring(inicio);
    }
}
