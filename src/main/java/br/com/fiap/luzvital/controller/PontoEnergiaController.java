package br.com.fiap.luzvital.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.dto.PontoEnergiaDtos.PontoEnergiaRequest;
import br.com.fiap.luzvital.dto.PontoEnergiaDtos.PontoEnergiaResposta;
import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.service.PontoEnergiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/pontos-energia")
@Tag(name = "4. Pontos de energia", description = "Rede de apoio: qualquer usuário consulta; só o GESTOR cadastra, altera e exclui")
public class PontoEnergiaController {

    private final PontoEnergiaService pontoService;

    public PontoEnergiaController(PontoEnergiaService pontoService) {
        this.pontoService = pontoService;
    }

    @GetMapping
    @Operation(summary = "Lista pontos de energia (paginado). Filtros: bairro, disponivel, possuiGerador")
    public PaginaResposta<PontoEnergiaResposta> listar(@RequestParam(required = false) String bairro,
                                                       @RequestParam(required = false) Boolean disponivel,
                                                       @RequestParam(required = false) Boolean possuiGerador,
                                                       @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return pontoService.listar(bairro, disponivel, possuiGerador, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um ponto de energia")
    public PontoEnergiaResposta buscar(@PathVariable Long id) {
        return pontoService.buscar(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Cadastra um ponto de energia voluntário")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "403", description = "Somente GESTOR", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<PontoEnergiaResposta> criar(@RequestBody @Valid PontoEnergiaRequest req) {
        PontoEnergiaResposta criado = pontoService.criar(req);
        return ResponseEntity.created(URI.create("/pontos-energia/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Atualiza um ponto de energia (ex.: marcar como indisponível)")
    public PontoEnergiaResposta atualizar(@PathVariable Long id, @RequestBody @Valid PontoEnergiaRequest req) {
        return pontoService.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Exclui um ponto sem histórico de ocorrências")
    @ApiResponse(responseCode = "204", description = "Excluído")
    @ApiResponse(responseCode = "409", description = "Ponto com histórico", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        pontoService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
