package br.com.fiap.luzvital.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

/** Tabela PACIENTE do script Oracle, com o cuidador dono do cadastro e coordenadas opcionais. */
@Entity
@Table(name = "PACIENTE")
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_PACIENTE")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ID_USUARIO")
    private Usuario cuidador;

    @Column(name = "NOME", nullable = false, length = 100)
    private String nome;

    @Column(name = "CPF", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "LOGRADOURO", nullable = false, length = 150)
    private String logradouro;

    @Column(name = "NUMERO", nullable = false, length = 20)
    private String numero;

    @Column(name = "BAIRRO", nullable = false, length = 50)
    private String bairro;

    @Column(name = "CIDADE", nullable = false, length = 50)
    private String cidade;

    @Column(name = "UF", nullable = false, length = 2)
    private String uf;

    @Column(name = "CEP", nullable = false, length = 9)
    private String cep;

    @Column(name = "CONTATO_CUIDADOR", nullable = false, length = 20)
    private String contatoCuidador;

    @Column(name = "LATITUDE", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "LONGITUDE", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "DATA_CADASTRO", nullable = false)
    private LocalDateTime dataCadastro;

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
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
    }

    public void atualizar(String nome, String cpf, String logradouro, String numero, String bairro, String cidade,
                          String uf, String cep, String contatoCuidador, BigDecimal latitude, BigDecimal longitude) {
        this.nome = nome;
        this.cpf = cpf;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.cep = cep;
        this.contatoCuidador = contatoCuidador;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** Usado só na carga inicial, para dar dono aos pacientes vindos do script Oracle. */
    public void definirCuidador(Usuario cuidador) {
        this.cuidador = cuidador;
    }

    public boolean pertenceA(Long usuarioId) {
        return cuidador != null && cuidador.getId().equals(usuarioId);
    }

    /** Aparelhos de suporte à vida, que são os que contam para a urgência. */
    public List<Equipamento> equipamentosSuporteVida() {
        return equipamentos.stream().filter(Equipamento::isSuporteVida).toList();
    }

    /**
     * Aparelho crítico: o de suporte à vida com menor autonomia. No empate de autonomia,
     * ventilador vem antes de concentrador (regra de prioridade da documentação).
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

    public boolean possuiCoordenadas() {
        return latitude != null && longitude != null;
    }

    public Long getId() { return id; }
    public Usuario getCuidador() { return cuidador; }
    public String getNome() { return nome; }
    public String getCpf() { return cpf; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf; }
    public String getCep() { return cep; }
    public String getContatoCuidador() { return contatoCuidador; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public List<Equipamento> getEquipamentos() { return equipamentos; }
    public PlanoContingencia getPlano() { return plano; }
    public void setPlano(PlanoContingencia plano) { this.plano = plano; }
}
