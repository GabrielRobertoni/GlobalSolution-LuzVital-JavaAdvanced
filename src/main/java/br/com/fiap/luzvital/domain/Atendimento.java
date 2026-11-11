package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Ação registrada pelo gestor em uma ocorrência (uma ocorrência tem vários atendimentos). */
@Entity
@Table(name = "ATENDIMENTO")
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_ATENDIMENTO")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_OCORRENCIA")
    private Ocorrencia ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO_GESTOR")
    private Usuario gestor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_PONTO")
    private PontoEnergia pontoEnergia;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_ATENDIMENTO", nullable = false, length = 30)
    private TipoAtendimento tipo;

    @Column(name = "DESCRICAO", nullable = false, length = 500)
    private String descricao;

    @Column(name = "DATA_REGISTRO", nullable = false)
    private LocalDateTime dataRegistro;

    protected Atendimento() {
    }

    public Atendimento(Ocorrencia ocorrencia, Usuario gestor, PontoEnergia pontoEnergia, TipoAtendimento tipo,
                       String descricao, LocalDateTime dataRegistro) {
        this.ocorrencia = ocorrencia;
        this.gestor = gestor;
        this.pontoEnergia = pontoEnergia;
        this.tipo = tipo;
        this.descricao = descricao;
        this.dataRegistro = dataRegistro;
    }

    public Long getId() { return id; }
    public Ocorrencia getOcorrencia() { return ocorrencia; }
    public Usuario getGestor() { return gestor; }
    public PontoEnergia getPontoEnergia() { return pontoEnergia; }
    public TipoAtendimento getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public LocalDateTime getDataRegistro() { return dataRegistro; }
}
