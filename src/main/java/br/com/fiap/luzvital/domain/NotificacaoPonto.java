package br.com.fiap.luzvital.domain;

import java.math.BigDecimal;
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
 * Aviso gravado pelo consumidor do evento energia.caiu para cada ponto de energia próximo.
 * A unique (ocorrência, ponto) deixa o consumidor idempotente se a mensagem for reentregue.
 */
@Entity
@Table(name = "NOTIFICACAO_PONTO",
        uniqueConstraints = @UniqueConstraint(name = "UK_NOTIF_OCO_PONTO", columnNames = {"ID_OCORRENCIA", "ID_PONTO"}))
public class NotificacaoPonto {

    public static final String CRITERIO_RAIO = "RAIO";
    public static final String CRITERIO_BAIRRO = "BAIRRO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_NOTIFICACAO")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_OCORRENCIA")
    private Ocorrencia ocorrencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_PONTO")
    private PontoEnergia pontoEnergia;

    /** Distância em km quando paciente e ponto têm coordenadas; nulo quando o critério foi o bairro. */
    @Column(name = "DISTANCIA_KM", precision = 7, scale = 2)
    private BigDecimal distanciaKm;

    @Column(name = "CRITERIO", nullable = false, length = 10)
    private String criterio;

    @Column(name = "MENSAGEM", nullable = false, length = 300)
    private String mensagem;

    @Column(name = "DATA_ENVIO", nullable = false)
    private LocalDateTime dataEnvio;

    protected NotificacaoPonto() {
    }

    public NotificacaoPonto(Ocorrencia ocorrencia, PontoEnergia pontoEnergia, BigDecimal distanciaKm, String criterio,
                            String mensagem, LocalDateTime dataEnvio) {
        this.ocorrencia = ocorrencia;
        this.pontoEnergia = pontoEnergia;
        this.distanciaKm = distanciaKm;
        this.criterio = criterio;
        this.mensagem = mensagem;
        this.dataEnvio = dataEnvio;
    }

    public Long getId() { return id; }
    public Ocorrencia getOcorrencia() { return ocorrencia; }
    public PontoEnergia getPontoEnergia() { return pontoEnergia; }
    public BigDecimal getDistanciaKm() { return distanciaKm; }
    public String getCriterio() { return criterio; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
}
