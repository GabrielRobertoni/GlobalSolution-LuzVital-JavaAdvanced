package br.com.fiap.luzvital.controller;

import java.net.URI;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.luzvital.domain.StatusOcorrencia;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AberturaOcorrenciaRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AtendimentoRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AtendimentoResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.AtualizacaoBateriaRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.CancelamentoRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.EncerramentoRequest;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.FilaItemResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.IndicadoresResposta;
import br.com.fiap.luzvital.dto.OcorrenciaDtos.OcorrenciaResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.security.UsuarioAtual;
import br.com.fiap.luzvital.service.OcorrenciaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/ocorrencias")
@Tag(name = "5. Ocorrências e fila do gestor", description = "\"Estou sem luz\", evento energia.caiu (RabbitMQ), fila por bateria restante, atendimentos e encerramento")
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;
    private final UsuarioAtual usuarioAtual;

    public OcorrenciaController(OcorrenciaService ocorrenciaService, UsuarioAtual usuarioAtual) {
        this.ocorrenciaService = ocorrenciaService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUIDADOR')")
    @Operation(summary = "Estou sem luz: abre a ocorrência e publica energia.caiu no RabbitMQ",
            description = "Envie o cabeçalho Idempotency-Key (ex.: UUID gerado no app). Reenviar a mesma chave devolve 200 "
                    + "com a mesma ocorrência, sem duplicar. Sem a chave, uma segunda ocorrência ativa devolve 409.")
    @ApiResponse(responseCode = "201", description = "Ocorrência criada")
    @ApiResponse(responseCode = "200", description = "Mesma Idempotency-Key: devolve a ocorrência já criada")
    @ApiResponse(responseCode = "409", description = "Já existe ocorrência ativa para o paciente", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    @ApiResponse(responseCode = "422", description = "Paciente sem aparelho de suporte à vida", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<OcorrenciaResposta> abrir(
            @Parameter(description = "Chave única do toque, gerada pelo app (8 a 100 caracteres)")
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Pattern(regexp = "^[A-Za-z0-9._:-]{8,100}$", message = "deve ter de 8 a 100 caracteres (letras, números, . _ : -)")
            String idempotencyKey,
            @RequestBody @Valid AberturaOcorrenciaRequest req) {
        var resultado = ocorrenciaService.abrir(req, idempotencyKey, usuarioAtual.obter());
        if (!resultado.criada()) {
            return ResponseEntity.ok(resultado.ocorrencia());
        }
        return ResponseEntity.created(URI.create("/ocorrencias/" + resultado.ocorrencia().id())).body(resultado.ocorrencia());
    }

    @GetMapping
    @Operation(summary = "Lista ocorrências (paginado). CUIDADOR vê as dos seus pacientes; GESTOR vê todas")
    public PaginaResposta<OcorrenciaResposta> listar(@RequestParam(required = false) Long pacienteId,
                                                     @RequestParam(required = false) StatusOcorrencia status,
                                                     @ParameterObject @PageableDefault(size = 10, sort = "dataInicio", direction = Sort.Direction.DESC)
                                                     Pageable pageable) {
        return ocorrenciaService.listar(pacienteId, status, pageable, usuarioAtual.obter());
    }

    @GetMapping("/fila")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Fila do gestor: ocorrências ativas ordenadas pelos minutos de bateria restantes",
            description = "Desempate: ventilador antes de concentrador, depois maior score e ordem de chegada.")
    @ApiResponse(responseCode = "403", description = "CUIDADOR não acessa a fila", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public PaginaResposta<FilaItemResposta> fila(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ocorrenciaService.fila(page, size);
    }

    @GetMapping("/indicadores")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Indicadores de impacto: tempo médio de atendimento, resolvidas antes do fim da bateria, pontos por bairro")
    public IndicadoresResposta indicadores() {
        return ocorrenciaService.indicadores();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha a ocorrência com contagem regressiva, atendimentos e pontos de energia avisados")
    public OcorrenciaResposta buscar(@PathVariable Long id) {
        return ocorrenciaService.buscar(id, usuarioAtual.obter());
    }

    @PatchMapping("/{id}/bateria")
    @Operation(summary = "Atualiza os minutos de bateria que restam agora (recalcula a prioridade)")
    @ApiResponse(responseCode = "409", description = "Ocorrência já finalizada", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public OcorrenciaResposta atualizarBateria(@PathVariable Long id, @RequestBody @Valid AtualizacaoBateriaRequest req) {
        return ocorrenciaService.atualizarBateria(id, req.minutosRestantes(), usuarioAtual.obter());
    }

    @PostMapping("/{id}/atendimentos")
    @PreAuthorize("hasRole('GESTOR')")
    @Operation(summary = "Gestor registra a resposta: gerador, recarga, remoção, religação prioritária...")
    @ApiResponse(responseCode = "201", description = "Atendimento registrado (status passa a EM_ATENDIMENTO)")
    public ResponseEntity<AtendimentoResposta> registrarAtendimento(@PathVariable Long id,
                                                                    @RequestBody @Valid AtendimentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ocorrenciaService.registrarAtendimento(id, req));
    }

    @GetMapping("/{id}/atendimentos")
    @Operation(summary = "Lista os atendimentos da ocorrência")
    public List<AtendimentoResposta> listarAtendimentos(@PathVariable Long id) {
        return ocorrenciaService.listarAtendimentos(id, usuarioAtual.obter());
    }

    @PostMapping("/{id}/encerramento")
    @Operation(summary = "Encerra a ocorrência como ATENDIDO (gestor ou o cuidador dono)")
    @ApiResponse(responseCode = "409", description = "Já encerrada ou cancelada", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public OcorrenciaResposta encerrar(@PathVariable Long id, @RequestBody @Valid EncerramentoRequest req) {
        return ocorrenciaService.encerrar(id, req.resultado(), usuarioAtual.obter());
    }

    @PostMapping("/{id}/cancelamento")
    @Operation(summary = "Cancela a ocorrência (ex.: a energia voltou ou o toque foi por engano)")
    @ApiResponse(responseCode = "409", description = "Já encerrada ou cancelada", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public OcorrenciaResposta cancelar(@PathVariable Long id, @RequestBody @Valid CancelamentoRequest req) {
        return ocorrenciaService.cancelar(id, req.motivo(), usuarioAtual.obter());
    }
}
