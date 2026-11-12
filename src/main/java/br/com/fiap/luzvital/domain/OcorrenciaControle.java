package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Tabela de apoio da API (não faz parte das 7 tabelas da disciplina de banco), 1:1 com T_GS_OCORRENCIA:
 * guarda a chave de idempotência do app, o horário em que a bateria foi informada e quando o consumidor
 * do RabbitMQ priorizou a ocorrência.
 */
@Entity
@Table(name = "T_GS_OCORRENCIA_CONTROLE")
public class OcorrenciaControle {

    @Id
    @Column(name = "id_ocorrencia")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ocorrencia")
    private Ocorrencia ocorrencia;

    @Column(name = "ds_chave_idempotencia", unique = true, length = 100)
    private String chaveIdempotencia;

    @Column(name = "dh_bateria", nullable = false)
    private LocalDateTime dataBateria;

    @Column(name = "dh_priorizacao")
    private LocalDateTime dataPriorizacao;

    protected OcorrenciaControle() {
    }

    OcorrenciaControle(Ocorrencia ocorrencia, String chaveIdempotencia, LocalDateTime dataBateria) {
        this.ocorrencia = ocorrencia;
        this.chaveIdempotencia = chaveIdempotencia;
        this.dataBateria = dataBateria;
    }

    void registrarBateria(LocalDateTime agora) {
        this.dataBateria = agora;
    }

    void registrarPriorizacao(LocalDateTime agora) {
        this.dataPriorizacao = agora;
    }

    public Long getId() { return id; }
    public Ocorrencia getOcorrencia() { return ocorrencia; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public LocalDateTime getDataBateria() { return dataBateria; }
    public LocalDateTime getDataPriorizacao() { return dataPriorizacao; }
}
