package br.com.fiap.luzvital.domain;

/** Mesmos valores da CHECK constraint CHK_OCO_STATUS do script Oracle. */
public enum StatusOcorrencia {
    EM_ABERTO,
    EM_ATENDIMENTO,
    ATENDIDO,
    CANCELADO;

    public boolean ativa() {
        return this == EM_ABERTO || this == EM_ATENDIMENTO;
    }
}
