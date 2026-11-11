package br.com.fiap.luzvital.config;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.domain.TipoEstabelecimento;
import br.com.fiap.luzvital.domain.Usuario;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;
import br.com.fiap.luzvital.repository.UsuarioRepository;
import br.com.fiap.luzvital.service.PlanoContingenciaService;

/**
 * Carga inicial com dados fictícios (mesmos pacientes, aparelhos e pontos do script Oracle da equipe de banco).
 * Só roda com o banco vazio. Usuários de teste:
 * gestor@luzvital.com / Gestor@123 · cuidador@luzvital.com / Cuidador@123 · cuidador2@luzvital.com / Cuidador@123
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepository usuarios;
    private final PacienteRepository pacientes;
    private final PontoEnergiaRepository pontos;
    private final PasswordEncoder encoder;
    private final PlanoContingenciaService planoService;
    private final LuzVitalProperties props;

    public DataSeeder(UsuarioRepository usuarios, PacienteRepository pacientes, PontoEnergiaRepository pontos,
                      PasswordEncoder encoder, PlanoContingenciaService planoService, LuzVitalProperties props) {
        this.usuarios = usuarios;
        this.pacientes = pacientes;
        this.pontos = pontos;
        this.encoder = encoder;
        this.planoService = planoService;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().habilitado() || usuarios.count() > 0) {
            return;
        }
        log.info("Sem usuários: criando os usuários de teste");
        usuarios.save(new Usuario("Equipe Defesa Civil SP", "gestor@luzvital.com", encoder.encode("Gestor@123"),
                Papel.GESTOR, "(11) 3333-0199"));
        Usuario ana = usuarios.save(new Usuario("Ana Souza", "cuidador@luzvital.com", encoder.encode("Cuidador@123"),
                Papel.CUIDADOR, "(11) 98888-1111"));
        Usuario bruno = usuarios.save(new Usuario("Bruno Lima", "cuidador2@luzvital.com", encoder.encode("Cuidador@123"),
                Papel.CUIDADOR, "(11) 96666-3333"));

        if (pacientes.count() > 0) {
            // Banco Oracle já carregado pelo script da equipe de banco: os pacientes sem dono passam para a cuidadora de teste
            for (Paciente p : pacientes.findByCuidadorIsNull()) {
                p.definirCuidador(ana);
                planoService.solicitar(p, "carga inicial");
            }
            log.info("Pacientes existentes associados a cuidador@luzvital.com");
            return;
        }
        log.info("Banco vazio: criando pacientes, aparelhos e pontos de energia de teste");
        if (pontos.count() == 0) {
            criarPontos();
        }
        criarPacientes(ana, bruno);
    }

    private void criarPontos() {
        ponto("Condomínio Edifício Paulista", TipoEstabelecimento.CONDOMINIO, "Síndico Roberto", "(11) 3251-0000",
                "Bela Vista", true, true, "-23.561400", "-46.655900");
        ponto("Padaria Central Novo Milênio", TipoEstabelecimento.COMERCIO, "Gerente Carlos", "(11) 3105-1111",
                "Jardim Paulista", true, true, "-23.569000", "-46.660000");
        ponto("UBS Consolação", TipoEstabelecimento.UNIDADE_SAUDE, "Enfermeira Júlia", "(11) 3256-2222",
                "Consolação", true, true, "-23.552000", "-46.656000");
        ponto("Vizinho Marcos (bateria portátil)", TipoEstabelecimento.VIZINHO, "Marcos Pereira", "(11) 97777-4444",
                "Jardim Paulista", false, true, null, null);
        ponto("Mercado Bela Vista", TipoEstabelecimento.COMERCIO, "Dona Sônia", "(11) 3288-5555",
                "Bela Vista", false, false, null, null);
    }

    private void criarPacientes(Usuario ana, Usuario bruno) {
        // Ventilador com 15 h de bateria (Philips Trilogy Evo: 2 baterias de 7,5 h)
        Paciente maria = paciente(ana, "Maria Oliveira", "123.456.789-09", "Rua das Flores", "105", "Jardim Paulista",
                "01415-000", "(11) 98888-1111", "-23.571000", "-46.657000");
        equipamento(maria, TipoAparelho.VENTILADOR_MECANICO, "Philips", "Trilogy Evo", "120.00", 900, true,
                FonteReserva.BATERIA_EXTERNA);

        // Concentrador estacionário sem bateria: para assim que a luz cai (caso mais frágil)
        Paciente joao = paciente(ana, "João Santos", "987.654.321-00", "Av. Brigadeiro Luís Antônio", "2100",
                "Bela Vista", "01318-000", "(11) 98888-1111", "-23.562000", "-46.648000");
        equipamento(joao, TipoAparelho.CONCENTRADOR_OXIGENIO, "Philips Respironics", "EverFlo", "350.00", 0, true,
                FonteReserva.CILINDRO_OXIGENIO);

        // Só bomba de infusão (não é suporte à vida): serve para o teste negativo de abertura de ocorrência
        Paciente carlos = paciente(bruno, "Carlos Eduardo", "456.789.123-64", "Rua Augusta", "500", "Consolação",
                "01305-000", "(11) 96666-3333", "-23.553000", "-46.653000");
        equipamento(carlos, TipoAparelho.BOMBA_INFUSAO, "Fresenius", "Agilia", "30.00", 240, false, FonteReserva.NENHUMA);

        for (Paciente p : new Paciente[]{maria, joao, carlos}) {
            planoService.solicitar(p, "carga inicial");
        }
    }

    private void ponto(String nome, TipoEstabelecimento tipo, String responsavel, String telefone, String bairro,
                       boolean gerador, boolean disponivel, String lat, String lon) {
        PontoEnergia p = new PontoEnergia();
        p.atualizar(nome, tipo, responsavel, telefone, bairro, gerador, disponivel,
                lat == null ? null : new BigDecimal(lat), lon == null ? null : new BigDecimal(lon));
        pontos.save(p);
    }

    private Paciente paciente(Usuario cuidador, String nome, String cpf, String logradouro, String numero, String bairro,
                              String cep, String contato, String lat, String lon) {
        Paciente p = new Paciente(cuidador);
        p.atualizar(nome, cpf, logradouro, numero, bairro, "São Paulo", "SP", cep, contato, new BigDecimal(lat),
                new BigDecimal(lon));
        return pacientes.save(p);
    }

    private void equipamento(Paciente p, TipoAparelho tipo, String fabricante, String modelo, String potencia,
                             int autonomia, boolean suporteVida, FonteReserva reserva) {
        Equipamento e = new Equipamento(p);
        e.atualizar(tipo, fabricante, modelo, new BigDecimal(potencia), autonomia, suporteVida, reserva);
        p.getEquipamentos().add(e);
    }
}
