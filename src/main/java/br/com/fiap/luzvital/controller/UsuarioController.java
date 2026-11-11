package br.com.fiap.luzvital.controller;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.dto.AuthDtos.CadastroUsuarioRequest;
import br.com.fiap.luzvital.dto.AuthDtos.UsuarioResposta;
import br.com.fiap.luzvital.dto.PaginaResposta;
import br.com.fiap.luzvital.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
@PreAuthorize("hasRole('GESTOR')")
@Tag(name = "6. Usuários (GESTOR)", description = "Gestão de usuários: só um gestor cria outro gestor")
public class UsuarioController {

    private final AuthService authService;

    public UsuarioController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/gestores")
    @Operation(summary = "Cria um usuário GESTOR (Defesa Civil, atenção domiciliar ou distribuidora)")
    public ResponseEntity<UsuarioResposta> criarGestor(@RequestBody @Valid CadastroUsuarioRequest req) {
        UsuarioResposta criado = authService.cadastrarGestor(req);
        return ResponseEntity.created(URI.create("/usuarios/" + criado.id())).body(criado);
    }

    @GetMapping
    @Operation(summary = "Lista usuários (paginado), com filtro opcional por papel")
    public PaginaResposta<UsuarioResposta> listar(
            @Parameter(description = "CUIDADOR ou GESTOR") @RequestParam(required = false) Papel papel,
            @PageableDefault(size = 10, sort = "id") Pageable pageable) {
        return authService.listar(papel, pageable);
    }
}
