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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Plano de contingência do paciente gerado pela IA (Spring AI) a partir dos aparelhos cadastrados.
 * O conteúdo estruturado é guardado em JSON; o app o salva para uso offline.
 */
@Entity
@Table(name = "PLANO_CONTINGENCIA")
public class PlanoContingencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PLANO")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_PACIENTE", unique = true)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 10)
    private StatusPlano status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORIGEM", length = 10)
    private OrigemPlano origem;

    /** JSON do plano estruturado (CLOB no Oracle, varchar longo no PostgreSQL). */
    @Column(name = "CONTEUDO", length = 20000)
    private String conteudo;

    @Column(name = "MODELO_IA", length = 80)
    private String modeloIa;

    @Column(name = "MENSAGEM_ERRO", length = 300)
    private String mensagemErro;

    @Column(name = "DATA_SOLICITACAO", nullable = false)
    private LocalDateTime dataSolicitacao;

    @Column(name = "DATA_GERACAO")
    private LocalDateTime dataGeracao;

    protected PlanoContingencia() {
    }

    public PlanoContingencia(Paciente paciente) {
        this.paciente = paciente;
    }

    public void marcarGerando(LocalDateTime agora) {
        this.status = StatusPlano.GERANDO;
        this.dataSolicitacao = agora;
        this.mensagemErro = null;
    }

    public void concluir(String conteudoJson, OrigemPlano origem, String modeloIa, String aviso, LocalDateTime agora) {
        this.status = StatusPlano.PRONTO;
        this.conteudo = conteudoJson;
        this.origem = origem;
        this.modeloIa = modeloIa;
        this.mensagemErro = aviso == null ? null : aviso.substring(0, Math.min(300, aviso.length()));
        this.dataGeracao = agora;
    }

    public void falhar(String erro) {
        this.status = StatusPlano.ERRO;
        this.mensagemErro = erro == null ? "Falha ao gerar o plano" : erro.substring(0, Math.min(300, erro.length()));
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public StatusPlano getStatus() { return status; }
    public OrigemPlano getOrigem() { return origem; }
    public String getConteudo() { return conteudo; }
    public String getModeloIa() { return modeloIa; }
    public String getMensagemErro() { return mensagemErro; }
    public LocalDateTime getDataSolicitacao() { return dataSolicitacao; }
    public LocalDateTime getDataGeracao() { return dataGeracao; }
}
