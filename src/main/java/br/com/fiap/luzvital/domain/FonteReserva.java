package br.com.fiap.luzvital.domain;

/** Fonte de energia (ou de oxigênio) que a família tem além da bateria interna do aparelho. */
public enum FonteReserva {
    NENHUMA,
    BATERIA_EXTERNA,
    GERADOR,
    CILINDRO_OXIGENIO,
    NOBREAK
}
