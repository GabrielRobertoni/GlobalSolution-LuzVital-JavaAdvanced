package br.com.fiap.luzvital.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.luzvital.dto.PacienteDtos.PacienteRequest;
import br.com.fiap.luzvital.dto.PacienteDtos.PacienteResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.dto.PlanoDtos.PlanoContingenciaResposta;
import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.security.UsuarioAtual;
import br.com.fiap.luzvital.service.PacienteService;
import br.com.fiap.luzvital.service.PlanoContingenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/pacientes")
@Tag(name = "2. Pacientes", description = "CUIDADOR vê e edita só os próprios pacientes; GESTOR consulta todos")
public class PacienteController {

    private final PacienteService pacienteService;
    private final PlanoContingenciaService planoService;
    private final UsuarioAtual usuarioAtual;

    public PacienteController(PacienteService pacienteService, PlanoContingenciaService planoService,
                              UsuarioAtual usuarioAtual) {
        this.pacienteService = pacienteService;
        this.planoService = planoService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    @Operation(summary = "Lista pacientes (paginado). Ex.: ?page=0&size=10&sort=nome,asc&bairro=Bela Vista")
    public PaginaResposta<PacienteResposta> listar(@RequestParam(required = false) String nome,
                                                   @RequestParam(required = false) String bairro,
                                                   @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return pacienteService.listar(nome, bairro, pageable, usuarioAtual.obter());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um paciente com seus aparelhos")
    @ApiResponse(responseCode = "403", description = "Paciente de outro cuidador", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public PacienteResposta buscar(@PathVariable Long id) {
        return pacienteService.buscar(id, usuarioAtual.obter());
    }

    @PostMapping
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Cadastra um paciente do cuidador logado")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos (CPF, CEP, UF, coordenadas...)", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    @ApiResponse(responseCode = "409", description = "CPF já cadastrado", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<PacienteResposta> criar(@RequestBody @Valid PacienteRequest req) {
        PacienteResposta criado = pacienteService.criar(req, usuarioAtual.obter());
        return ResponseEntity.created(URI.create("/pacientes/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Atualiza um paciente do cuidador logado")
    public PacienteResposta atualizar(@PathVariable Long id, @RequestBody @Valid PacienteRequest req) {
        return pacienteService.atualizar(id, req, usuarioAtual.obter());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Exclui um paciente sem ocorrências (os aparelhos e o plano saem junto)")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "409", description = "Paciente com histórico de ocorrências", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        pacienteService.excluir(id, usuarioAtual.obter());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/plano-contingencia")
    @Operation(summary = "Plano de contingência gerado pela IA a partir dos aparelhos (o app guarda para uso offline)")
    @ApiResponse(responseCode = "404", description = "Paciente sem aparelhos (ainda sem plano)", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public PlanoContingenciaResposta plano(@PathVariable Long id) {
        return planoService.consultar(id, usuarioAtual.obter());
    }

    @PostMapping("/{id}/plano-contingencia")
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Pede um novo plano à IA (processado de forma assíncrona via RabbitMQ)")
    @ApiResponse(responseCode = "202", description = "Plano em geração (status GERANDO)")
    public ResponseEntity<PlanoContingenciaResposta> regenerarPlano(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(planoService.regenerar(id, usuarioAtual.obter()));
    }
}
