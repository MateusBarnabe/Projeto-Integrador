package br.com.centinela.marketing.compartilhado.dados;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;

/** Base dos conversores JPA de enums {@link ValorPersistido}; cada enum declara uma subclasse. */
public abstract class ConversorDeValor<E extends Enum<E> & ValorPersistido> implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected ConversorDeValor(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E constante) {
        return constante == null ? null : constante.valor();
    }

    @Override
    public E convertToEntityAttribute(String valor) {
        if (valor == null) {
            return null;
        }
        return Arrays.stream(tipo.getEnumConstants())
                .filter(constante -> constante.valor().equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Valor desconhecido para " + tipo.getSimpleName() + ": " + valor));
    }
}
