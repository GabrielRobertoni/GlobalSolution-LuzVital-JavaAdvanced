package br.com.fiap.luzvital.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
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
import br.com.fiap.luzvital.messaging.EnergiaCaiuEvento;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.repository.UsuarioRepository;
import br.com.fiap.luzvital.service.PlanoContingenciaService;

/**
 * Carga inicial espelhada do script Oracle da disciplina de banco (GSBancoOracle.sql): os mesmos 5 usuários,
 * pacientes, equipamentos, pontos de energia, ocorrências e atendimentos. Só roda com o banco vazio.
 * <p>
 * Com o banco Oracle já carregado pelo script da equipe, as senhas fictícias ('hash_...') dos 5 usuários de
 * teste são trocadas pelo hash BCrypt das senhas documentadas no README, para permitir o login.
 * Senhas: cuidadores = Cuidador@123, gestores = Gestor@123.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String SENHA_CUIDADOR = "Cuidador@123";
    public static final String SENHA_GESTOR = "Gestor@123";

    /** Usuários do script Oracle: e-mail -> [nome, papel]. */
    private static final String[][] USUARIOS = {
            {"Carlos Alberto Silva", "carlos.silva@gmail.com", "CUIDADOR"},
            {"Mariana Santos Rocha", "mariana.rocha@hotmail.com", "CUIDADOR"},
            {"Roberto Ferreira", "roberto.ferreira@outlook.com", "CUIDADOR"},
            {"Luciana Mendes", "luciana.mendes@defesacivil.sp.gov.br", "GESTOR"},
            {"Dr. Fernando Souza", "fernando.souza@enel.com.br", "GESTOR"}
    };

    private final UsuarioRepository usuarios;
    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final OcorrenciaRepository ocorrencias;
    private final PasswordEncoder encoder;
    private final PlanoContingenciaService planoService;
    private final ApplicationEventPublisher eventos;
    private final LuzVitalProperties props;

    public DataSeeder(UsuarioRepository usuarios, PacienteRepository pacientes, PontoEnergiaRepository pontos,
                      OcorrenciaRepository ocorrencias, PasswordEncoder encoder, PlanoContingenciaService planoService,
                      ApplicationEventPublisher eventos, LuzVitalProperties props) {
        this.usuarios = usuarios;
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.ocorrencias = ocorrencias;
        this.encoder = encoder;
        this.planoService = planoService;
        this.eventos = eventos;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().habilitado()) {
            return;
        }
        if (usuarios.count() == 0) {
            log.info("Banco vazio: carregando os dados do script Oracle da equipe de banco");
            carregarEspelhoDoScriptOracle();
        } else {
            ajustarSenhasFicticias();
        }
        // Plano de contingência (tabela de apoio da API) para quem tem aparelho e ainda não tem plano
        pacientes.findAll().stream()
                .filter(p -> !p.getEquipamentos().isEmpty() && p.getPlano() == null)
                .forEach(p -> planoService.solicitar(p, "carga inicial"));
    }

    /** No Oracle carregado pelo script, ds_senha tem textos fictícios ('hash_carlos123'): troca por BCrypt. */
    private void ajustarSenhasFicticias() {
        for (String[] u : USUARIOS) {
            usuarios.findByEmailIgnoreCase(u[1]).ifPresent(usuario -> {
                String hash = usuario.getSenhaHash();
                if (hash == null || !hash.startsWith("$2")) {
                    usuario.definirSenhaHash(encoder.encode(senhaDe(usuario.getPapel())));
                    log.info("Senha de teste definida para {}", usuario.getEmail());
                }
            });
        }
    }

    private void carregarEspelhoDoScriptOracle() {
        Map<String, Usuario> porEmail = new HashMap<>();
        for (String[] u : USUARIOS) {
            Papel papel = Papel.valueOf(u[2]);
            porEmail.put(u[1], usuarios.save(new Usuario(u[0], u[1], encoder.encode(senhaDe(papel)), papel)));
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

        // As ocorrências ativas passam pelo consumidor do RabbitMQ (score atual + aviso aos pontos do bairro)
        for (Ocorrencia o : new Ocorrencia[]{ocoMaria, ocoAna, ocoJose}) {
            eventos.publishEvent(new EnergiaCaiuEvento(o.getId(), o.getPaciente().getId(), o.getPaciente().getBairro(),
                    o.getDataInicio()));
        }
    }

    private static String senhaDe(Papel papel) {
        return papel == Papel.GESTOR ? SENHA_GESTOR : SENHA_CUIDADOR;
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
