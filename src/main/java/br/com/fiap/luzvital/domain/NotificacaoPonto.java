package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tabela de apoio da API: aviso gravado pelo consumidor do evento energia.caiu para cada ponto de energia
 * do bairro do paciente. A unique (ocorrência, ponto) deixa o consumidor idempotente se a mensagem for reentregue.
 */
@Entity
@Table(name = "T_GS_NOTIFICACAO_PONTO",
        uniqueConstraints = @UniqueConstraint(name = "UK_T_GS_NOTIF_OCO_PONTO", columnNames = {"id_ocorrencia", "id_ponto"}))
public class NotificacaoPonto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ocorrencia", nullable = false)
    private Ocorrencia ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_ponto", nullable = false)
    private PontoEnergia pontoEnergia;

    @Column(name = "ds_mensagem", nullable = false, length = 300)
    private String mensagem;

    @Column(name = "dh_envio", nullable = false)
    private LocalDateTime dataEnvio;

    protected NotificacaoPonto() {
    }

    public NotificacaoPonto(Ocorrencia ocorrencia, PontoEnergia pontoEnergia, String mensagem, LocalDateTime dataEnvio) {
        this.ocorrencia = ocorrencia;
        this.pontoEnergia = pontoEnergia;
        this.mensagem = mensagem;
        this.dataEnvio = dataEnvio;
    }

    public Long getId() { return id; }
    public Ocorrencia getOcorrencia() { return ocorrencia; }
    public PontoEnergia getPontoEnergia() { return pontoEnergia; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
}
