package br.com.fiap.luzvital.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token JWT invalidado no logout (pelo jti) até a data em que ele expiraria de qualquer forma. */
@Entity
@Table(name = "TOKEN_REVOGADO")
public class TokenRevogado {

    @Id
    @Column(name = "JTI", length = 64)
    private String jti;

    @Column(name = "DATA_EXPIRACAO", nullable = false)
    private Instant dataExpiracao;

    protected TokenRevogado() {
    }

    public TokenRevogado(String jti, Instant dataExpiracao) {
        this.jti = jti;
        this.dataExpiracao = dataExpiracao;
    }

    public String getJti() { return jti; }
    public Instant getDataExpiracao() { return dataExpiracao; }
}
