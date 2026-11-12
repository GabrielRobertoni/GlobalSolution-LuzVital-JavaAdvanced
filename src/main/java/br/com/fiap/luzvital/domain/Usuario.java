package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Tabela T_GS_USUARIO do banco da disciplina: cuidadores e gestores. */
@Entity
@Table(name = "T_GS_USUARIO")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    @Column(name = "nm_usuario", nullable = false, length = 120)
    private String nome;

    @Column(name = "ds_email", nullable = false, unique = true, length = 160)
    private String email;

    /** Hash BCrypt da senha. */
    @Column(name = "ds_senha", nullable = false, length = 255)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_usuario", nullable = false, length = 20)
    private Papel papel;

    @Convert(converter = SimNaoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "st_usuario", nullable = false, length = 1)
    private Boolean ativo;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    protected Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, Papel papel) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.ativo = true;
    }

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
        if (ativo == null) {
            ativo = true;
        }
    }

    /** Usado só na carga inicial, para trocar as senhas fictícias da carga Oracle ('hash_...') por BCrypt. */
    public void definirSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isAtivo() { return !Boolean.FALSE.equals(ativo); }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
