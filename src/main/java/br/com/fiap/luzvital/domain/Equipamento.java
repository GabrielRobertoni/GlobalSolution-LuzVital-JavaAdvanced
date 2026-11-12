package br.com.fiap.luzvital.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Tabela T_GS_EQUIPAMENTO do banco da disciplina. Um paciente tem vários equipamentos. */
@Entity
@Table(name = "T_GS_EQUIPAMENTO")
public class Equipamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_equipamento")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Convert(converter = TipoAparelho.Conversor.class)
    @Column(name = "tp_aparelho", nullable = false, length = 50)
    private TipoAparelho tipoAparelho;

    @Column(name = "nm_fabricante", nullable = false, length = 80)
    private String fabricante;

    @Column(name = "nm_modelo", nullable = false, length = 80)
    private String modelo;

    @Column(name = "vl_potencia_watts", nullable = false, precision = 6, scale = 2)
    private BigDecimal potenciaWatts;

    /** Autonomia total com a bateria cheia. Zero quando o aparelho não tem bateria (ex.: concentrador estacionário). */
    @Column(name = "qt_autonomia_min", nullable = false, precision = 5)
    private Integer autonomiaMinutos;

    @Convert(converter = SimNaoConverter.class)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fl_suporte_vida", nullable = false, length = 1)
    private Boolean suporteVida;

    @Convert(converter = FonteReserva.Conversor.class)
    @Column(name = "tp_fonte_reserva", nullable = false, length = 50)
    private FonteReserva fonteReserva;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    protected Equipamento() {
    }

    public Equipamento(Paciente paciente) {
        this.paciente = paciente;
    }

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
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
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
