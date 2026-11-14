package br.com.fiap.luzvital.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EquipamentoRequest;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EquipamentoResposta;
import br.com.fiap.luzvital.dto.EquipamentoDtos.EtiquetaLidaResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.security.UsuarioAtual;
import br.com.fiap.luzvital.service.EquipamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/equipamentos")
@Tag(name = "3. Equipamentos", description = "Aparelhos de uso contínuo. Salvar/alterar/excluir gera de novo o plano de contingência (Spring AI)")
public class EquipamentoController {

    private final EquipamentoService equipamentoService;
    private final UsuarioAtual usuarioAtual;

    public EquipamentoController(EquipamentoService equipamentoService, UsuarioAtual usuarioAtual) {
        this.equipamentoService = equipamentoService;
        this.usuarioAtual = usuarioAtual;
    }

    @GetMapping
    @Operation(summary = "Lista aparelhos (paginado). Filtros: pacienteId, tipo, suporteVida")
    public PaginaResposta<EquipamentoResposta> listar(@RequestParam(required = false) Long pacienteId,
                                                      @RequestParam(required = false) TipoAparelho tipo,
                                                      @RequestParam(required = false) Boolean suporteVida,
                                                      @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return equipamentoService.listar(pacienteId, tipo, suporteVida, pageable, usuarioAtual.obter());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um aparelho")
    public EquipamentoResposta buscar(@PathVariable Long id) {
        return equipamentoService.buscar(id, usuarioAtual.obter());
    }

    @PostMapping
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Cadastra um aparelho e dispara a geração do plano de contingência pela IA")
    @ApiResponse(responseCode = "201", description = "Criado")
    @ApiResponse(responseCode = "400", description = "Potência <= 0, autonomia negativa...", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<EquipamentoResposta> criar(@RequestBody @Valid EquipamentoRequest req) {
        EquipamentoResposta criado = equipamentoService.criar(req, usuarioAtual.obter());
        return ResponseEntity.created(URI.create("/equipamentos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Atualiza um aparelho (o plano é gerado de novo)")
    public EquipamentoResposta atualizar(@PathVariable Long id, @RequestBody @Valid EquipamentoRequest req) {
        return equipamentoService.atualizar(id, req, usuarioAtual.obter());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Exclui um aparelho (o plano é gerado de novo)")
    @ApiResponse(responseCode = "204", description = "Excluído")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        equipamentoService.excluir(id, usuarioAtual.obter());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/leitura-etiqueta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Cadastro por foto: a IA lê fabricante, modelo, tensão, corrente e potência da etiqueta",
            description = "Não salva nada: o app mostra os dados para o cuidador confirmar e depois chama POST /equipamentos.")
    @ApiResponse(responseCode = "200", description = "Leitura feita (confira legivel e potenciaEstimada)")
    @ApiResponse(responseCode = "422", description = "Arquivo ausente ou formato não suportado", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    @ApiResponse(responseCode = "503", description = "IA não configurada ou indisponível: preencher manualmente", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public EtiquetaLidaResposta lerEtiqueta(@RequestPart("foto") MultipartFile foto) {
        return equipamentoService.lerEtiqueta(foto);
    }
}
