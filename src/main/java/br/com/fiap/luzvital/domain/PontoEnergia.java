package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Tabela T_GS_PONTO_ENERGIA do banco da disciplina: condomínio, comércio ou posto público com gerador. */
@Entity
@Table(name = "T_GS_PONTO_ENERGIA")
public class PontoEnergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ponto")
    private Long id;

    @Column(name = "nm_local", nullable = false, length = 120)
    private String nomeLocal;

    @Convert(converter = TipoEstabelecimento.Conversor.class)
    @Column(name = "tp_estabelecimento", nullable = false, length = 50)
    private TipoEstabelecimento tipoEstabelecimento;

    @Column(name = "nm_responsavel", nullable = false, length = 100)
    private String responsavel;

    @Column(name = "nr_telefone", nullable = false, length = 25)
    private String telefone;

    @Column(name = "nm_bairro", nullable = false, length = 80)
    private String bairro;

    @Convert(converter = SimNaoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fl_possui_gerador", nullable = false, length = 1)
    private Boolean possuiGerador;

    @Convert(converter = SimNaoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "st_disponivel", nullable = false, length = 1)
    private Boolean disponivel;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    public PontoEnergia() {
    }

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

    public void atualizar(String nomeLocal, TipoEstabelecimento tipoEstabelecimento, String responsavel, String telefone,
                          String bairro, Boolean possuiGerador, Boolean disponivel) {
        this.nomeLocal = nomeLocal;
        this.tipoEstabelecimento = tipoEstabelecimento;
        this.responsavel = responsavel;
        this.telefone = telefone;
        this.bairro = bairro;
        this.possuiGerador = Boolean.TRUE.equals(possuiGerador);
        this.disponivel = disponivel == null || disponivel;
    }

    public Long getId() { return id; }
    public String getNomeLocal() { return nomeLocal; }
    public TipoEstabelecimento getTipoEstabelecimento() { return tipoEstabelecimento; }
    public String getResponsavel() { return responsavel; }
    public String getTelefone() { return telefone; }
    public String getBairro() { return bairro; }
    public boolean isPossuiGerador() { return Boolean.TRUE.equals(possuiGerador); }
    public boolean isDisponivel() { return Boolean.TRUE.equals(disponivel); }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
