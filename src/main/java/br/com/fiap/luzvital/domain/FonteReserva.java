package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/** Fonte de energia (ou de oxigênio) que a família tem além da bateria interna (coluna tp_fonte_reserva). */
public enum FonteReserva {
    NENHUMA("nenhuma"),
    BATERIA_EXTERNA("bateria externa"),
    GERADOR("gerador"),
    CILINDRO_OXIGENIO("cilindro de oxigênio"),
    NOBREAK("nobreak");

    private final String descricao;

    FonteReserva(String descricao) {
        this.descricao = descricao;
    }

    /** Nome legível em português (usado no texto enviado à IA). */
    public String descricao() {
        return descricao;
    }

    @Converter
    public static class Conversor extends CodigoBanco<FonteReserva> {
        public Conversor() {
            super(FonteReserva.class, NENHUMA);
        }
    }
}
