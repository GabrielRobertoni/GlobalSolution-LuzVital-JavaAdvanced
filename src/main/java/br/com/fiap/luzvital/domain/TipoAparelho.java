package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/**
 * Tipos de aparelho de uso contínuo (coluna tp_aparelho). No empate de minutos restantes da fila,
 * ventilador vem antes de concentrador ({@link #ordemDesempate()}).
 */
public enum TipoAparelho {
    VENTILADOR_MECANICO(0, "ventilador mecânico"),
    CONCENTRADOR_OXIGENIO(1, "concentrador de oxigênio"),
    ASPIRADOR_SECRECAO(2, "aspirador de secreção"),
    DIALISE_DOMICILIAR(2, "equipamento de diálise domiciliar"),
    BOMBA_INFUSAO(2, "bomba de infusão"),
    CADEIRA_RODAS_ELETRICA(3, "cadeira de rodas elétrica"),
    OUTRO(3, "outro aparelho elétrico");

    private final int ordemDesempate;
    private final String descricao;

    TipoAparelho(int ordemDesempate, String descricao) {
        this.ordemDesempate = ordemDesempate;
        this.descricao = descricao;
    }

    /** Nome legível em português (usado no texto enviado à IA). */
    public String descricao() {
        return descricao;
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
