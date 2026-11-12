package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/**
 * Tipos de socorro aceitos pela constraint CK_T_GS_ATEND_TIPO do banco Oracle.
 * Na API os nomes são sem acento; no banco são gravados exatamente como a constraint exige.
 */
public enum TipoSocorro {
    GERADOR_MOVEL("GERADOR_MOVEL"),
    RECARGA_BATERIA("RECARGA_BATERIA"),
    REMOCAO_UTI("REMOÇAO_UTI"),
    RELIGACAO_PRIORITARIA("RELIGAÇÃO_PRIORITARIA");

    private final String codigoBanco;

    TipoSocorro(String codigoBanco) {
        this.codigoBanco = codigoBanco;
    }

    public String codigoBanco() {
        return codigoBanco;
    }

    @Converter
    public static class Conversor extends CodigoBanco<TipoSocorro> {
        public Conversor() {
            super(TipoSocorro.class, null);
        }

        @Override
        protected String codigo(TipoSocorro valor) {
            return valor.codigoBanco();
        }
    }
}
