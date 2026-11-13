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
 * Tabela T_GS_OCORRENCIA do banco da disciplina: aberta no "Estou sem luz".
 * <p>
 * qt_bateria_min guarda os minutos de bateria informados (na abertura, a menor autonomia entre os
 * aparelhos de suporte à vida, como em PR_T_GS_ABRIR_OCORRENCIA). Os minutos restantes são calculados
 * na hora a partir do momento em que a bateria foi informada.
 * <p>
 * {@code @DynamicUpdate}: o UPDATE leva só as colunas alteradas, então o gatilho TRG_T_GS_UPDATE_BATERIA
 * (BEFORE UPDATE OF qt_bateria_min) só dispara quando a bateria muda de fato.
 */
@Entity
@Table(name = "T_GS_OCORRENCIA")
@DynamicUpdate
public class Ocorrencia {

    private static final int TAMANHO_OBSERVACAO = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ocorrencia")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false)
    private Paciente paciente;

    @Column(name = "dh_inicio", nullable = false)
    private LocalDateTime dataInicio;

    @Column(name = "dh_fim")
    private LocalDateTime dataFim;

    @Enumerated(EnumType.STRING)
    @Column(name = "st_ocorrencia", nullable = false, length = 20)
    private StatusOcorrencia status;

    @Column(name = "qt_bateria_min", nullable = false)
    private Integer bateriaMinutos;

    /** 0 até o consumidor do evento energia.caiu priorizar (ou o valor calculado pela carga do banco). */
    @Column(name = "vl_score_prioridade", nullable = false)
    private Integer prioridadeScore;

    @Column(name = "ds_observacao", length = TAMANHO_OBSERVACAO)
    private String observacao;

    @OneToMany(mappedBy = "ocorrencia", cascade = CascadeType.ALL)
    @OrderBy("dataAtendimento ASC")
    private List<Atendimento> atendimentos = new ArrayList<>();

    protected Ocorrencia() {
    }

    /** Abertura pelo "Estou sem luz": score 0 até o consumidor do evento energia.caiu priorizar. */
    public static Ocorrencia abrir(Paciente paciente, int bateriaMinutos, String observacao, LocalDateTime agora) {
        return registrar(paciente, StatusOcorrencia.EM_ABERTO, bateriaMinutos, 0, observacao, agora);
    }

    /** Registro direto (carga inicial espelhada do script do banco). */
    public static Ocorrencia registrar(Paciente paciente, StatusOcorrencia status, int bateriaMinutos, int score,
                                       String observacao, LocalDateTime inicio) {
        Ocorrencia o = new Ocorrencia();
        o.paciente = paciente;
        o.dataInicio = inicio;
        o.status = status;
        o.bateriaMinutos = bateriaMinutos;
        o.prioridadeScore = score;
        o.observacao = observacao;
        return o;
    }

    /** Minutos que restam agora, descontando o tempo desde {@code referenciaBateria} (quando a bateria foi informada). */
    public int minutosRestantes(LocalDateTime referenciaBateria, LocalDateTime agora) {
        long decorridos = Math.max(0, Duration.between(referenciaBateria, agora).toMinutes());
        return (int) Math.max(0, bateriaMinutos - decorridos);
    }

    public LocalDateTime previsaoEsgotamento(LocalDateTime referenciaBateria) {
        return referenciaBateria.plusMinutes(bateriaMinutos);
    }

    public void atualizarBateria(int minutosRestantes) {
        this.bateriaMinutos = minutosRestantes;
    }

    public void aplicarPrioridade(int score) {
        this.prioridadeScore = score;
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

    /** Usado na carga inicial para registrar ocorrências já finalizadas. */
    public void finalizarEm(StatusOcorrencia novoStatus, LocalDateTime fim) {
        this.status = novoStatus;
        this.dataFim = fim;
    }

    private void finalizar(StatusOcorrencia novoStatus, String texto, LocalDateTime agora) {
        this.status = novoStatus;
        this.dataFim = agora;
        // Mesmo formato de PR_T_GS_ENCERRAR_OCORRENCIA: o desfecho é anexado à observação
        String completa = (observacao == null || observacao.isBlank()) ? texto : observacao + " | " + texto;
        this.observacao = completa.length() > TAMANHO_OBSERVACAO ? completa.substring(0, TAMANHO_OBSERVACAO) : completa;
    }

    public boolean isAtiva() {
        return status != null && status.ativa();
    }

    /** Já tem score calculado (pelo consumidor de energia.caiu ou pela carga do banco). */
    public boolean isPriorizada() {
        return prioridadeScore != null && prioridadeScore > 0;
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public LocalDateTime getDataInicio() { return dataInicio; }
    public LocalDateTime getDataFim() { return dataFim; }
    public StatusOcorrencia getStatus() { return status; }
    public Integer getBateriaMinutos() { return bateriaMinutos; }
    public Integer getPrioridadeScore() { return prioridadeScore; }
    public String getObservacao() { return observacao; }
    public List<Atendimento> getAtendimentos() { return atendimentos; }
}
