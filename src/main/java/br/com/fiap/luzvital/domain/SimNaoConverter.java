package br.com.fiap.luzvital.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converte boolean para as colunas CHAR(1) 'S'/'N' do modelo Oracle. */
@Converter
public class SimNaoConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean valor) {
        if (valor == null) {
            return null;
        }
        return valor ? "S" : "N";
    }

    @Override
    public Boolean convertToEntityAttribute(String coluna) {
        if (coluna == null) {
            return null;
        }
        return "S".equalsIgnoreCase(coluna.trim());
    }
}
