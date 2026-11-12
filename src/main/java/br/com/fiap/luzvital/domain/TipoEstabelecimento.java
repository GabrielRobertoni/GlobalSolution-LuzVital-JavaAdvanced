package br.com.fiap.luzvital.domain;

import jakarta.persistence.Converter;

/** Tipo do ponto de energia (coluna tp_estabelecimento). A carga do banco usa CONDOMINIO, COMERCIO e PUBLICO. */
public enum TipoEstabelecimento {
    CONDOMINIO,
    COMERCIO,
    PUBLICO,
    UNIDADE_SAUDE,
    VIZINHO,
    OUTRO;

    @Converter
    public static class Conversor extends CodigoBanco<TipoEstabelecimento> {
        public Conversor() {
            super(TipoEstabelecimento.class, OUTRO);
        }
    }
}
