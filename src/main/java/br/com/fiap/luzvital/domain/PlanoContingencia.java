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
 * Tabela de apoio da API: plano de contingência do paciente gerado pela IA (Spring AI) a partir dos
 * aparelhos cadastrados. O conteúdo estruturado fica em JSON; o app o guarda para uso offline.
 */
@Entity
@Table(name = "T_GS_PLANO_CONTINGENCIA")
public class PlanoContingencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plano")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paciente", nullable = false, unique = true)
    private Paciente paciente;

    @Enumerated(EnumType.STRING)
    @Column(name = "st_plano", nullable = false, length = 10)
    private StatusPlano status;

    @Enumerated(EnumType.STRING)
    @Column(name = "tp_origem", length = 10)
    private OrigemPlano origem;

    /** JSON do plano estruturado (CLOB no Oracle, text no PostgreSQL). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(name = "ds_conteudo")
    private String conteudo;

    @Column(name = "nm_modelo_ia", length = 80)
    private String modeloIa;

    @Column(name = "ds_mensagem", length = 300)
    private String mensagem;

    @Column(name = "dh_solicitacao", nullable = false)
    private LocalDateTime dataSolicitacao;

    @Column(name = "dh_geracao")
    private LocalDateTime dataGeracao;

    protected PlanoContingencia() {
    }

    public PlanoContingencia(Paciente paciente) {
        this.paciente = paciente;
    }

    public void marcarGerando(LocalDateTime agora) {
        this.status = StatusPlano.GERANDO;
        this.dataSolicitacao = agora;
        this.mensagem = null;
    }

    public void concluir(String conteudoJson, OrigemPlano origem, String modeloIa, String aviso, LocalDateTime agora) {
        this.status = StatusPlano.PRONTO;
        this.conteudo = conteudoJson;
        this.origem = origem;
        this.modeloIa = modeloIa;
        this.mensagem = aviso == null ? null : aviso.substring(0, Math.min(300, aviso.length()));
        this.dataGeracao = agora;
    }

    public void falhar(String erro) {
        this.status = StatusPlano.ERRO;
        this.mensagem = erro == null ? "Falha ao gerar o plano" : erro.substring(0, Math.min(300, erro.length()));
    }

    public Long getId() { return id; }
    public Paciente getPaciente() { return paciente; }
    public StatusPlano getStatus() { return status; }
    public OrigemPlano getOrigem() { return origem; }
    public String getConteudo() { return conteudo; }
    public String getModeloIa() { return modeloIa; }
    public String getMensagemErro() { return mensagem; }
    public LocalDateTime getDataSolicitacao() { return dataSolicitacao; }
    public LocalDateTime getDataGeracao() { return dataGeracao; }
}
