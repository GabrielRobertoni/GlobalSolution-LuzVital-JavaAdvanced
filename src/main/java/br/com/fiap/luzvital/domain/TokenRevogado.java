package br.com.fiap.luzvital.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabela de apoio da API: token JWT invalidado no logout (pelo jti) até a data em que expiraria. */
@Entity
@Table(name = "T_GS_TOKEN_REVOGADO")
public class TokenRevogado {

    @Id
    @Column(name = "id_jti", length = 64)
    private String jti;

    @Column(name = "dh_expiracao", nullable = false)
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
