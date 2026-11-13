package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/**
 * Tipos de aparelho de uso contínuo (coluna tp_aparelho). No empate de minutos restantes da fila,
 * ventilador vem antes de concentrador ({@link #ordemDesempate()}).
 * <p>
 * Na API os nomes são sem acento; no banco são gravados como na carga do script Oracle da equipe de banco
 * (ex.: 'ASPIRADOR_SECREÇÃO'), para que filtros por tipo encontrem tanto os registros da carga quanto os novos.
 */
public enum TipoAparelho {
    VENTILADOR_MECANICO(0, "ventilador mecânico", "VENTILADOR_MECANICO"),
    CONCENTRADOR_OXIGENIO(1, "concentrador de oxigênio", "CONCENTRADOR_OXIGENIO"),
    ASPIRADOR_SECRECAO(2, "aspirador de secreção", "ASPIRADOR_SECREÇÃO"),
    DIALISE_DOMICILIAR(2, "equipamento de diálise domiciliar", "DIALISE_DOMICILIAR"),
    BOMBA_INFUSAO(2, "bomba de infusão", "BOMBA_INFUSAO"),
    CADEIRA_RODAS_ELETRICA(3, "cadeira de rodas elétrica", "CADEIRA_RODAS_ELETRICA"),
    OUTRO(3, "outro aparelho elétrico", "OUTRO");

    private final int ordemDesempate;
    private final String descricao;
    private final String codigoBanco;

    TipoAparelho(int ordemDesempate, String descricao, String codigoBanco) {
        this.ordemDesempate = ordemDesempate;
        this.descricao = descricao;
        this.codigoBanco = codigoBanco;
    }

    /** Nome legível em português (usado no texto enviado à IA). */
    public String descricao() {
        return descricao;
    }

    public int ordemDesempate() {
        return ordemDesempate;
    }

    /** Valor gravado na coluna tp_aparelho. */
    public String codigoBanco() {
        return codigoBanco;
    }

    /** Grava como no script Oracle ('ASPIRADOR_SECREÇÃO'); lê com ou sem acento; valor desconhecido vira OUTRO. */
    @Converter
    public static class Conversor extends CodigoBanco<TipoAparelho> {
        public Conversor() {
            super(TipoAparelho.class, OUTRO);
        }

        @Override
        protected String codigo(TipoAparelho valor) {
            return valor.codigoBanco();
        }
    }
}
