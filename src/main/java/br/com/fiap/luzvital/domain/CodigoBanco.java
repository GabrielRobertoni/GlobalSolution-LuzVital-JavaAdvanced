package br.com.fiap.luzvital.domain;

import java.text.Normalizer;
import java.util.Locale;

import jakarta.persistence.AttributeConverter;

/**
 * Converte enums para os códigos gravados no banco da disciplina, tolerando variações de acento
 * e maiúsculas na leitura (ex.: a carga Oracle grava 'ASPIRADOR_SECREÇÃO' e a constraint de
 * T_GS_ATENDIMENTO exige 'REMOÇAO_UTI' e 'RELIGAÇÃO_PRIORITARIA').
 */
public abstract class CodigoBanco<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> tipo;
    private final E padrao;

    protected CodigoBanco(Class<E> tipo, E padrao) {
        this.tipo = tipo;
        this.padrao = padrao;
    }

    /** Código gravado no banco para o valor (por padrão, o próprio nome do enum). */
    protected String codigo(E valor) {
        return valor.name();
    }

    @Override
    public String convertToDatabaseColumn(E valor) {
        return valor == null ? null : codigo(valor);
    }

    @Override
    public E convertToEntityAttribute(String coluna) {
        if (coluna == null || coluna.isBlank()) {
            return null;
        }
        String alvo = normalizar(coluna);
        for (E constante : tipo.getEnumConstants()) {
            if (normalizar(constante.name()).equals(alvo) || normalizar(codigo(constante)).equals(alvo)) {
                return constante;
            }
        }
        if (padrao == null) {
            throw new IllegalArgumentException("Valor desconhecido para " + tipo.getSimpleName() + ": " + coluna);
        }
        return padrao;
    }

    public static String normalizar(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
    }
}
