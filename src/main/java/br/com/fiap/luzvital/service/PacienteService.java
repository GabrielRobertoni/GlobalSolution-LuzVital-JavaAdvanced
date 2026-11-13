package br.com.fiap.luzvital.service;

import java.util.Locale;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.Usuario;
import br.com.fiap.luzvital.dto.PacienteDtos.PacienteRequest;
import br.com.fiap.luzvital.dto.PacienteDtos.PacienteResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.ConflitoException;
import br.com.fiap.luzvital.exception.RecursoNaoEncontradoException;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PacienteRepository;
import br.com.fiap.luzvital.repository.UsuarioRepository;
import br.com.fiap.luzvital.security.UsuarioAutenticado;

@Service
public class PacienteService {

    private final PacienteRepository pacientes;
    private final UsuarioRepository usuarios;
    private final OcorrenciaRepository ocorrencias;
    private final PlanoContingenciaService planoService;

    public PacienteService(PacienteRepository pacientes, UsuarioRepository usuarios, OcorrenciaRepository ocorrencias,
                           PlanoContingenciaService planoService) {
        this.pacientes = pacientes;
        this.usuarios = usuarios;
        this.ocorrencias = ocorrencias;
        this.planoService = planoService;
    }

    @Transactional(readOnly = true)
    public PaginaResposta<PacienteResposta> listar(String nome, String bairro, Pageable pageable,
                                                   UsuarioAutenticado usuario) {
        var pagina = pacientes.buscar(Acesso.filtroCuidador(usuario), vazioParaNulo(nome), vazioParaNulo(bairro), pageable);
        return PaginaResposta.de(pagina, this::resposta);
    }

    @Transactional(readOnly = true)
    public PacienteResposta buscar(Long id, UsuarioAutenticado usuario) {
        Paciente paciente = carregar(id);
        Acesso.leitura(paciente, usuario);
        return resposta(paciente);
    }

    @Transactional
    public PacienteResposta criar(PacienteRequest req, UsuarioAutenticado usuario) {
        String cpf = Formatos.cpf(req.cpf());
        if (pacientes.existsByCpf(cpf)) {
            throw new ConflitoException("Já existe um paciente cadastrado com este CPF");
        }
        Usuario cuidador = usuarios.findById(usuario.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário", usuario.id()));
        Paciente paciente = new Paciente(cuidador);
        aplicar(paciente, req, cpf);
        return resposta(pacientes.save(paciente));
    }

    @Transactional
    public PacienteResposta atualizar(Long id, PacienteRequest req, UsuarioAutenticado usuario) {
        Paciente paciente = carregar(id);
        Acesso.escrita(paciente, usuario);
        String cpf = Formatos.cpf(req.cpf());
        if (pacientes.existsByCpfAndIdNot(cpf, id)) {
            throw new ConflitoException("Já existe outro paciente cadastrado com este CPF");
        }
        boolean mudouBairro = !paciente.getBairro().equalsIgnoreCase(req.bairro().trim());
        aplicar(paciente, req, cpf);
        if (mudouBairro && !paciente.getEquipamentos().isEmpty()) {
            // Os pontos de energia do plano dependem do bairro
            planoService.solicitar(paciente, "endereço alterado");
        }
        return resposta(paciente);
    }

    @Transactional
    public void excluir(Long id, UsuarioAutenticado usuario) {
        Paciente paciente = carregar(id);
        Acesso.escrita(paciente, usuario);
        if (ocorrencias.existsByPacienteId(id)) {
            throw new ConflitoException("Paciente com ocorrências registradas não pode ser excluído (histórico de atendimento)");
        }
        pacientes.delete(paciente); // os equipamentos saem em cascata
        planoService.descartar(id);
    }

    private PacienteResposta resposta(Paciente p) {
        return PacienteResposta.de(p, planoService.status(p.getId()));
    }

    Paciente carregar(Long id) {
        return pacientes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", id));
    }

    private void aplicar(Paciente p, PacienteRequest req, String cpf) {
        p.atualizar(req.nome().trim(), cpf, req.endereco().trim(), req.bairro().trim(), req.cidade().trim(),
                req.uf().toUpperCase(Locale.ROOT), Formatos.cep(req.cep()), req.telefone().trim());
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
