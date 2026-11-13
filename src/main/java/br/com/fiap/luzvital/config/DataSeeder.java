package br.com.fiap.luzvital.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Atendimento;
import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.StatusOcorrencia;
import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.domain.TipoEstabelecimento;
import br.com.fiap.luzvital.domain.TipoSocorro;
import br.com.fiap.luzvital.domain.Usuario;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.repository.UsuarioRepository;

/**
 * Carga inicial: só roda com o banco VAZIO (ex.: PostgreSQL local). Espelha o script Oracle da disciplina de banco
 * (GSBancoOracle.sql): os mesmos 5 usuários, pacientes, equipamentos, pontos de energia, ocorrências e atendimentos.
 * <p>
 * Com o banco já carregado (ex.: o Oracle da equipe de banco), não grava nem altera nada.
 * Os usuários de teste entram com a senha que está no script ('hash_carlos123', 'hash_luciana_adm'...);
 * aqui no espelho essas senhas são guardadas com BCrypt.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Usuários do script Oracle: [nome, e-mail, papel, senha gravada no script]. */
    private static final String[][] USUARIOS = {
            {"Carlos Alberto Silva", "carlos.silva@gmail.com", "CUIDADOR", "hash_carlos123"},
            {"Mariana Santos Rocha", "mariana.rocha@hotmail.com", "CUIDADOR", "hash_mariana456"},
            {"Roberto Ferreira", "roberto.ferreira@outlook.com", "CUIDADOR", "hash_roberto789"},
            {"Luciana Mendes", "luciana.mendes@defesacivil.sp.gov.br", "GESTOR", "hash_luciana_adm"},
            {"Dr. Fernando Souza", "fernando.souza@enel.com.br", "GESTOR", "hash_enel_gestor"}
    };

    private final UsuarioRepository usuarios;
    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final OcorrenciaRepository ocorrencias;
    private final PasswordEncoder encoder;
    private final LuzVitalProperties props;

    public DataSeeder(UsuarioRepository usuarios, PacienteRepository pacientes, PontoEnergiaRepository pontos,
                      OcorrenciaRepository ocorrencias, PasswordEncoder encoder, LuzVitalProperties props) {
        this.usuarios = usuarios;
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.ocorrencias = ocorrencias;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().habilitado()) {
            return;
        }
        if (usuarios.count() > 0) {
            log.info("Banco já carregado: a carga inicial não cria nem altera nenhum dado");
            return;
        }
        log.info("Banco vazio: carregando o espelho do script Oracle da equipe de banco");
        carregarEspelhoDoScriptOracle();
    }

    private void carregarEspelhoDoScriptOracle() {
        Map<String, Usuario> porEmail = new HashMap<>();
        for (String[] u : USUARIOS) {
            Papel papel = Papel.valueOf(u[2]);
            porEmail.put(u[1], usuarios.save(new Usuario(u[0], u[1], encoder.encode(u[3]), papel)));
        }

        PontoEnergia paulista = ponto("Condomínio Edifício Paulista", TipoEstabelecimento.CONDOMINIO, "Síndico Roberto",
                "(11) 3251-0000", "Bela Vista", true);
        PontoEnergia padaria = ponto("Padaria Central Novo Milênio", TipoEstabelecimento.COMERCIO, "Gerente Carlos",
                "(11) 3105-1111", "Jardim Paulista", true);
        ponto("Drogaria São Paulo 24h", TipoEstabelecimento.COMERCIO, "Farmacêutico Marcos", "(11) 3887-2222",
                "Consolação", true);
        PontoEnergia postoVilaMariana = ponto("Posto de Saúde Vila Mariana", TipoEstabelecimento.PUBLICO,
                "Dra. Patricia", "(11) 5571-3333", "Vila Mariana", true);
        PontoEnergia plazaPinheiros = ponto("Condomínio Plaza Pinheiros", TipoEstabelecimento.CONDOMINIO,
                "Zelador Antonio", "(11) 3032-4444", "Pinheiros", false);

        Paciente joao = paciente(porEmail.get("carlos.silva@gmail.com"), "João Carlos Silva", "123.456.789-01",
                "Rua das Flores, 105", "Jardim Paulista", "01415-000", "(11) 98888-1111");
        equipamento(joao, TipoAparelho.VENTILADOR_MECANICO, "Philips", "Trilogy Evo", "120.00", 900, true,
                FonteReserva.BATERIA_EXTERNA);
        Paciente maria = paciente(porEmail.get("mariana.rocha@hotmail.com"), "Maria Aparecida Rocha", "987.654.321-02",
                "Av. Brigadeiro Luís Antônio, 2100", "Bela Vista", "01318-000", "(11) 97777-2222");
        equipamento(maria, TipoAparelho.CONCENTRADOR_OXIGENIO, "Philips Respironics", "EverFlo", "350.00", 0, true,
                FonteReserva.CILINDRO_OXIGENIO);
        Paciente pedro = paciente(porEmail.get("roberto.ferreira@outlook.com"), "Pedro Henrique Ferreira",
                "456.789.123-03", "Rua Augusta, 500", "Consolação", "01305-000", "(11) 96666-3333");
        equipamento(pedro, TipoAparelho.BOMBA_INFUSAO, "Fresenius", "Agilia", "30.00", 240, false, FonteReserva.NENHUMA);
        Paciente ana = paciente(porEmail.get("luciana.mendes@defesacivil.sp.gov.br"), "Ana Beatriz Mendes",
                "234.567.890-04", "Rua Domingos de Morais, 1200", "Vila Mariana", "04010-100", "(11) 95555-4444");
        equipamento(ana, TipoAparelho.ASPIRADOR_SECRECAO, "Nevoni", "1004-SUCTION", "80.00", 60, true,
                FonteReserva.NENHUMA);
        Paciente jose = paciente(porEmail.get("fernando.souza@enel.com.br"), "José Eduardo Souza", "345.678.901-05",
                "Av. Rebouças, 3000", "Pinheiros", "05402-000", "(11) 94444-5555");
        equipamento(jose, TipoAparelho.VENTILADOR_MECANICO, "ResMed", "Astral 150", "100.00", 480, true,
                FonteReserva.BATERIA_EXTERNA);

        LocalDateTime agora = LocalDateTime.now();
        Ocorrencia ocoMaria = ocorrencia(maria, agora.minusHours(2), StatusOcorrencia.EM_ABERTO, 0, 1200,
                "Apagão por vendaval em Bela Vista. Concentrador sem bateria.");
        Ocorrencia ocoAna = ocorrencia(ana, agora.minusHours(1), StatusOcorrencia.EM_ATENDIMENTO, 60, 1000,
                "Paciente em uso de aspirador de secreção.");
        Ocorrencia ocoJose = ocorrencia(jose, agora.minusMinutes(30), StatusOcorrencia.EM_ABERTO, 480, 700,
                "Ventilador em bateria interna com 8h restantes.");
        Ocorrencia ocoJoao = ocorrencia(joao, agora.minusHours(4), StatusOcorrencia.ATENDIDO, 900, 300,
                "Alimentação elétrica restabelecida pela Enel.");
        ocoJoao.finalizarEm(StatusOcorrencia.ATENDIDO, ocoJoao.getDataInicio().plusMinutes(42));
        Ocorrencia ocoPedro = ocorrencia(pedro, agora.minusMinutes(10), StatusOcorrencia.CANCELADO, 240, 100,
                "Queda de disjuntor interno resolvida pelo cuidador.");
        ocoPedro.finalizarEm(StatusOcorrencia.CANCELADO, agora);

        atendimento(ocoMaria, paulista, TipoSocorro.GERADOR_MOVEL, "Despachado gerador portátil de 3kW da Defesa Civil.");
        atendimento(ocoAna, postoVilaMariana, TipoSocorro.RECARGA_BATERIA,
                "Bateria externa levada para recarga no Posto de Saúde.");
        atendimento(ocoJoao, null, TipoSocorro.RELIGACAO_PRIORITARIA,
                "Equipe da distribuidora direcionada para religação do alimentador.");
        atendimento(ocoJose, plazaPinheiros, TipoSocorro.REMOCAO_UTI,
                "Paciente removido preventivamente para abrigo com gerador.");
        atendimento(ocoPedro, padaria, TipoSocorro.RECARGA_BATERIA,
                "Orientação remota fornecida para troca por cilindro reserva.");
        ocorrencias.flush();
    }

    private PontoEnergia ponto(String nome, TipoEstabelecimento tipo, String responsavel, String telefone, String bairro,
                               boolean gerador) {
        PontoEnergia p = new PontoEnergia();
        p.atualizar(nome, tipo, responsavel, telefone, bairro, gerador, true);
        return pontos.save(p);
    }

    private Paciente paciente(Usuario cuidador, String nome, String cpf, String endereco, String bairro, String cep,
                              String telefone) {
        Paciente p = new Paciente(cuidador);
        p.atualizar(nome, cpf, endereco, bairro, "São Paulo", "SP", cep, telefone);
        return pacientes.save(p);
    }

    private void equipamento(Paciente p, TipoAparelho tipo, String fabricante, String modelo, String potencia,
                             int autonomia, boolean suporteVida, FonteReserva reserva) {
        Equipamento e = new Equipamento(p);
        e.atualizar(tipo, fabricante, modelo, new BigDecimal(potencia), autonomia, suporteVida, reserva);
        p.getEquipamentos().add(e);
    }

    private Ocorrencia ocorrencia(Paciente p, LocalDateTime inicio, StatusOcorrencia status, int bateria, int score,
                                  String observacao) {
        return ocorrencias.save(Ocorrencia.registrar(p, status, bateria, score, observacao, inicio));
    }

    private void atendimento(Ocorrencia o, PontoEnergia ponto, TipoSocorro tipo, String descricao) {
        o.getAtendimentos().add(new Atendimento(o, ponto, tipo, descricao, o.getDataInicio().plusMinutes(10)));
    }
}
