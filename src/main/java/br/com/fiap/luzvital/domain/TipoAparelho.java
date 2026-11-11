package br.com.fiap.luzvital.domain;

/**
 * Tipos de aparelho de uso contínuo. A ordem de desempate da fila vem de {@link #ordemDesempate()}:
 * no empate de minutos restantes, ventilador vem antes de concentrador.
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
}
