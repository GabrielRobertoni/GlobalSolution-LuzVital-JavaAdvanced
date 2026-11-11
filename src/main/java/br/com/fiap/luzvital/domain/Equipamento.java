package br.com.fiap.luzvital.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/** Tabela EQUIPAMENTO do script Oracle. Um paciente tem vários equipamentos. */
@Entity
@Table(name = "EQUIPAMENTO")
public class Equipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_EQUIPAMENTO")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_PACIENTE")
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "TIPO_APARELHO", nullable = false, length = 50)
    private TipoAparelho tipoAparelho;

    @Column(name = "FABRICANTE", nullable = false, length = 50)
    private String fabricante;

    @Column(name = "MODELO", nullable = false, length = 50)
    private String modelo;

    @Column(name = "POTENCIA_WATTS", nullable = false, precision = 6, scale = 2)
    private BigDecimal potenciaWatts;

    /** Autonomia total com a bateria cheia. Zero quando o aparelho não tem bateria (ex.: concentrador estacionário). */
    @Column(name = "AUTONOMIA_MINUTOS", nullable = false)
    private Integer autonomiaMinutos;

    @Convert(converter = SimNaoConverter.class)
    @Column(name = "SUPORTE_VIDA", nullable = false, length = 1)
    private Boolean suporteVida;

    @Enumerated(EnumType.STRING)
    @Column(name = "FONTE_RESERVA", nullable = false, length = 50)
    private FonteReserva fonteReserva;

    protected Equipamento() {
    }

    public Equipamento(Paciente paciente) {
        this.paciente = paciente;
    }

    public void atualizar(TipoAparelho tipoAparelho, String fabricante, String modelo, BigDecimal potenciaWatts,
                          Integer autonomiaMinutos, Boolean suporteVida, FonteReserva fonteReserva) {
        this.tipoAparelho = tipoAparelho;
        this.fabricante = fabricante;
        this.modelo = modelo;
        this.potenciaWatts = potenciaWatts;
        this.autonomiaMinutos = autonomiaMinutos;
        this.suporteVida = suporteVida;
        this.fonteReserva = fonteReserva == null ? FonteReserva.NENHUMA : fonteReserva;
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public TipoAparelho getTipoAparelho() { return tipoAparelho; }
    public String getFabricante() { return fabricante; }
    public String getModelo() { return modelo; }
    public BigDecimal getPotenciaWatts() { return potenciaWatts; }
    public Integer getAutonomiaMinutos() { return autonomiaMinutos; }
    public boolean isSuporteVida() { return Boolean.TRUE.equals(suporteVida); }
    public FonteReserva getFonteReserva() { return fonteReserva; }
}
