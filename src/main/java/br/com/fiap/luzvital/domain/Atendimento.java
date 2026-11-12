package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Tabela T_GS_ATENDIMENTO do banco da disciplina: socorro registrado pelo gestor (uma ocorrência tem vários). */
@Entity
@Table(name = "T_GS_ATENDIMENTO")
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_atendimento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ocorrencia", nullable = false)
    private Ocorrencia ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ponto")
    private PontoEnergia pontoEnergia;

    @Convert(converter = TipoSocorro.Conversor.class)
    @Column(name = "tp_socorro", nullable = false, length = 50)
    private TipoSocorro tipoSocorro;

    @Column(name = "ds_atendimento", nullable = false, length = 1000)
    private String descricao;

    @Column(name = "dh_atendimento", nullable = false)
    private LocalDateTime dataAtendimento;

    protected Atendimento() {
    }

    public Atendimento(Ocorrencia ocorrencia, PontoEnergia pontoEnergia, TipoSocorro tipoSocorro, String descricao,
                       LocalDateTime dataAtendimento) {
        this.ocorrencia = ocorrencia;
        this.pontoEnergia = pontoEnergia;
        this.tipoSocorro = tipoSocorro;
        this.descricao = descricao;
        this.dataAtendimento = dataAtendimento;
    }

    public Long getId() { return id; }
    public Ocorrencia getOcorrencia() { return ocorrencia; }
    public PontoEnergia getPontoEnergia() { return pontoEnergia; }
    public TipoSocorro getTipoSocorro() { return tipoSocorro; }
    public String getDescricao() { return descricao; }
    public LocalDateTime getDataAtendimento() { return dataAtendimento; }
}
