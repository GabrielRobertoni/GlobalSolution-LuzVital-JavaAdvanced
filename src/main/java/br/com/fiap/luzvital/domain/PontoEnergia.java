package br.com.fiap.luzvital.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabela PONTO_ENERGIA do script Oracle: vizinho, condomínio ou comércio com gerador ou bateria. */
@Entity
@Table(name = "PONTO_ENERGIA")
public class PontoEnergia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PONTO")
    private Long id;

    @Column(name = "NOME_LOCAL", nullable = false, length = 100)
    private String nomeLocal;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_ESTABELECIMENTO", nullable = false, length = 50)
    private TipoEstabelecimento tipoEstabelecimento;

    @Column(name = "RESPONSAVEL", nullable = false, length = 100)
    private String responsavel;

    @Column(name = "TELEFONE", nullable = false, length = 20)
    private String telefone;

    @Column(name = "BAIRRO", nullable = false, length = 50)
    private String bairro;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "POSSUI_GERADOR", nullable = false, length = 1)
    private Boolean possuiGerador;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "STATUS_DISPONIVEL", nullable = false, length = 1)
    private Boolean disponivel;

    @Column(name = "LATITUDE", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 9, scale = 6)
    private BigDecimal longitude;

    public PontoEnergia() {
    }

    public void atualizar(String nomeLocal, TipoEstabelecimento tipoEstabelecimento, String responsavel, String telefone,
                          String bairro, Boolean possuiGerador, Boolean disponivel, BigDecimal latitude, BigDecimal longitude) {
        this.nomeLocal = nomeLocal;
        this.tipoEstabelecimento = tipoEstabelecimento;
        this.responsavel = responsavel;
        this.telefone = telefone;
        this.bairro = bairro;
        this.possuiGerador = Boolean.TRUE.equals(possuiGerador);
        this.disponivel = disponivel == null || disponivel;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public boolean possuiCoordenadas() {
        return latitude != null && longitude != null;
    }

    public Long getId() { return id; }
    public String getNomeLocal() { return nomeLocal; }
    public TipoEstabelecimento getTipoEstabelecimento() { return tipoEstabelecimento; }
    public String getResponsavel() { return responsavel; }
    public String getTelefone() { return telefone; }
    public String getBairro() { return bairro; }
    public boolean isPossuiGerador() { return Boolean.TRUE.equals(possuiGerador); }
    public boolean isDisponivel() { return Boolean.TRUE.equals(disponivel); }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
}
