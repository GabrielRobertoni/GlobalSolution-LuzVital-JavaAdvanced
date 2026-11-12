package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/**
 * Tipos de aparelho de uso contínuo (coluna tp_aparelho). No empate de minutos restantes da fila,
 * ventilador vem antes de concentrador ({@link #ordemDesempate()}).
 */
public enum TipoAparelho {
    VENTILADOR_MECANICO(0),
    CONCENTRADOR_OXIGENIO(1),
    ASPIRADOR_SECRECAO(2),
    DIALISE_DOMICILIAR(2),
    BOMBA_INFUSAO(2),
    CADEIRA_RODAS_ELETRICA(3),
    OUTRO(3);

    private final int ordemDesempate;

    TipoAparelho(int ordemDesempate) {
        this.ordemDesempate = ordemDesempate;
    }

    public int ordemDesempate() {
        return ordemDesempate;
    }

    /** Lê 'ASPIRADOR_SECREÇÃO' (carga Oracle) como ASPIRADOR_SECRECAO; valor desconhecido vira OUTRO. */
    @Converter
    public static class Conversor extends CodigoBanco<TipoAparelho> {
        public Conversor() {
            super(TipoAparelho.class, OUTRO);
        }
    }
}
