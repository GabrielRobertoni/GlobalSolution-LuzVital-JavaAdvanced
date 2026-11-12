package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/** Fonte de energia (ou de oxigênio) que a família tem além da bateria interna (coluna tp_fonte_reserva). */
public enum FonteReserva {
    NENHUMA,
    BATERIA_EXTERNA,
    GERADOR,
    CILINDRO_OXIGENIO,
    NOBREAK;

    @Converter
    public static class Conversor extends CodigoBanco<FonteReserva> {
        public Conversor() {
            super(FonteReserva.class, NENHUMA);
        }
    }
}
