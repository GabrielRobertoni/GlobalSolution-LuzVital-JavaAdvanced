package br.com.fiap.luzvital.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Tabela T_GS_PACIENTE do banco da disciplina. Um cuidador (T_GS_USUARIO) tem vários pacientes. */
@Entity
@Table(name = "T_GS_PACIENTE")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_paciente")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario cuidador;

    @Column(name = "nm_paciente", nullable = false, length = 120)
    private String nome;

    @Column(name = "nr_cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "ds_endereco", nullable = false, length = 200)
    private String endereco;

    @Column(name = "nm_bairro", nullable = false, length = 80)
    private String bairro;

    @Column(name = "nm_cidade", nullable = false, length = 80)
    private String cidade;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sg_estado", nullable = false, length = 2)
    private String uf;

    @Column(name = "nr_cep", nullable = false, length = 9)
    private String cep;

    /** Telefone de contato do paciente/cuidador em emergência. */
    @Column(name = "nr_telefone", nullable = false, length = 25)
    private String telefone;

    @Column(name = "dt_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @OneToMany(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Equipamento> equipamentos = new ArrayList<>();

    @OneToOne(mappedBy = "paciente", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private PlanoContingencia plano;

    protected Paciente() {
    }

    public Paciente(Usuario cuidador) {
        this.cuidador = cuidador;
    }

    @PrePersist
    void aoCriar() {
        if (dataCriacao == null) {
            dataCriacao = LocalDateTime.now();
        }
    }

    public void atualizar(String nome, String cpf, String endereco, String bairro, String cidade, String uf,
                          String cep, String telefone) {
        this.nome = nome;
        this.cpf = cpf;
        this.endereco = endereco;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.cep = cep;
        this.telefone = telefone;
    }

    public boolean pertenceA(Long usuarioId) {
        return cuidador != null && cuidador.getId().equals(usuarioId);
    }

    /** Aparelhos de suporte à vida, que são os que contam para a urgência. */
    public List<Equipamento> equipamentosSuporteVida() {
        return equipamentos.stream().filter(Equipamento::isSuporteVida).toList();
    }

    public boolean possuiSuporteVida() {
        return equipamentos.stream().anyMatch(Equipamento::isSuporteVida);
    }

    /**
     * Aparelho crítico: o de suporte à vida com menor autonomia (mesma regra de FN_T_GS_MINUTOS_RESTANTES).
     * No empate de autonomia, ventilador vem antes de concentrador.
     */
    public Optional<Equipamento> equipamentoCritico() {
        return equipamentosSuporteVida().stream()
                .min(Comparator.comparingInt(Equipamento::getAutonomiaMinutos)
                        .thenComparingInt(e -> e.getTipoAparelho().ordemDesempate()));
    }

    /** A família tem fonte reserva se algum aparelho de suporte à vida tem bateria externa, gerador, cilindro etc. */
    public boolean possuiFonteReserva() {
        return equipamentosSuporteVida().stream()
                .anyMatch(e -> e.getFonteReserva() != null && e.getFonteReserva() != FonteReserva.NENHUMA);
    }

    public Long getId() { return id; }
    public Usuario getCuidador() { return cuidador; }
    public String getNome() { return nome; }
    public String getCpf() { return cpf; }
    public String getEndereco() { return endereco; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf == null ? null : uf.trim(); }
    public String getCep() { return cep; }
    public String getTelefone() { return telefone; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public List<Equipamento> getEquipamentos() { return equipamentos; }
    public PlanoContingencia getPlano() { return plano; }
    public void setPlano(PlanoContingencia plano) { this.plano = plano; }
}
