package br.com.fiap.luzvital.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Tabela OCORRENCIA do script Oracle: aberta no "Estou sem luz".
 * <p>
 * BATERIA_INICIAL_MIN guarda os minutos de bateria informados em DATA_BATERIA (na abertura, a menor
 * autonomia entre os aparelhos de suporte à vida). Os minutos restantes são sempre calculados na hora:
 * BATERIA_INICIAL_MIN menos o tempo decorrido desde DATA_BATERIA.
 * <p>
 * {@code @DynamicUpdate}: o UPDATE só leva as colunas alteradas. Assim o gatilho Oracle
 * TRG_UPDATE_BATERIA_PRIORIDADE só dispara quando a bateria muda de fato.
 */
@Entity
@Table(name = "OCORRENCIA")
@DynamicUpdate
public class Ocorrencia {

    private static final int TAMANHO_OBSERVACAO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_OCORRENCIA")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_PACIENTE")
    private Paciente paciente;

    @Column(name = "DATA_INICIO", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "DATA_FIM")
    private LocalDateTime dataFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private StatusOcorrencia status;

    @Column(name = "BATERIA_INICIAL_MIN", nullable = false)
    private Integer bateriaInicialMin;

    @Column(name = "DATA_BATERIA", nullable = false)
    private LocalDateTime dataBateria;

    @Column(name = "PRIORIDADE_SCORE", nullable = false)
    private Integer prioridadeScore;

    @Column(name = "OBSERVACAO", length = TAMANHO_OBSERVACAO)
    private String observacao;

    /** Chave enviada pelo app no cabeçalho Idempotency-Key: o mesmo toque reenviado não cria outra ocorrência. */
    @Column(name = "CHAVE_IDEMPOTENCIA", unique = true, length = 100)
    private String chaveIdempotencia;

    /** Preenchida pelo consumidor do evento energia.caiu quando calcula a prioridade e avisa os pontos. */
    @Column(name = "DATA_PRIORIZACAO")
    private LocalDateTime dataPriorizacao;

    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.ALL)
    @OrderBy("dataRegistro ASC")
    private List<Atendimento> atendimentos = new ArrayList<>();

    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.ALL)
    @OrderBy("distanciaKm ASC")
    private List<NotificacaoPonto> notificacoes = new ArrayList<>();

    protected Ocorrencia() {
    }

    public static Ocorrencia abrir(Paciente paciente, int bateriaMinutos, String observacao, String chaveIdempotencia,
                                   LocalDateTime agora) {
        Ocorrencia o = new Ocorrencia();
        o.paciente = paciente;
        o.dataInicio = agora;
        o.dataBateria = agora;
        o.bateriaInicialMin = bateriaMinutos;
        o.status = StatusOcorrencia.EM_ABERTO;
        o.prioridadeScore = 0;
        o.observacao = observacao;
        o.chaveIdempotencia = chaveIdempotencia;
        return o;
    }

    public int minutosRestantes(LocalDateTime agora) {
        long decorridos = Math.max(0, Duration.between(dataBateria, agora).toMinutes());
        return (int) Math.max(0, bateriaInicialMin - decorridos);
    }

    public LocalDateTime previsaoEsgotamento() {
        return dataBateria.plusMinutes(bateriaInicialMin);
    }

    public void atualizarBateria(int minutosRestantes, LocalDateTime agora) {
        this.bateriaInicialMin = minutosRestantes;
        this.dataBateria = agora;
    }

    public void aplicarPrioridade(int score, LocalDateTime agora) {
        this.prioridadeScore = score;
        this.dataPriorizacao = agora;
    }

    public void registrarAtendimento(Atendimento atendimento) {
        atendimentos.add(atendimento);
        if (status == StatusOcorrencia.EM_ABERTO) {
            status = StatusOcorrencia.EM_ATENDIMENTO;
        }
    }

    public void encerrar(String resultado, LocalDateTime agora) {
        finalizar(StatusOcorrencia.ATENDIDO, "Encerramento: " + resultado, agora);
    }

    public void cancelar(String motivo, LocalDateTime agora) {
        finalizar(StatusOcorrencia.CANCELADO, "Cancelamento: " + motivo, agora);
    }

    private void finalizar(StatusOcorrencia novoStatus, String texto, LocalDateTime agora) {
        this.status = novoStatus;
        this.dataFim = agora;
        // Mesmo formato do PRC_ENCERRAR_OCORRENCIA: a observação final é anexada à inicial
        String completa = (observacao == null || observacao.isBlank()) ? texto : observacao + " | " + texto;
        this.observacao = completa.length() > TAMANHO_OBSERVACAO ? completa.substring(0, TAMANHO_OBSERVACAO) : completa;
    }

    public boolean isAtiva() {
        return status != null && status.ativa();
    }

    public boolean isPriorizada() {
        return dataPriorizacao != null;
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public LocalDateTime getDataInicio() { return dataInicio; }
    public LocalDateTime getDataFim() { return dataFim; }
    public StatusOcorrencia getStatus() { return status; }
    public Integer getBateriaInicialMin() { return bateriaInicialMin; }
    public LocalDateTime getDataBateria() { return dataBateria; }
    public Integer getPrioridadeScore() { return prioridadeScore; }
    public String getObservacao() { return observacao; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public LocalDateTime getDataPriorizacao() { return dataPriorizacao; }
    public List<Atendimento> getAtendimentos() { return atendimentos; }
    public List<NotificacaoPonto> getNotificacoes() { return notificacoes; }
}
