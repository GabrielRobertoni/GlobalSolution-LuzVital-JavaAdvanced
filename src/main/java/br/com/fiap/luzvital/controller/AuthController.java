package br.com.fiap.luzvital.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.fiap.luzvital.dto.AuthDtos.AuthResposta;
import br.com.fiap.luzvital.dto.AuthDtos.CadastroUsuarioRequest;
import br.com.fiap.luzvital.dto.AuthDtos.LoginRequest;
import br.com.fiap.luzvital.dto.AuthDtos.UsuarioResposta;
import br.com.fiap.luzvital.exception.ErroResposta;
import br.com.fiap.luzvital.security.UsuarioAtual;
import br.com.fiap.luzvital.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "1. Autenticação", description = "Cadastro do cuidador, login, logout e usuário logado (JWT)")
public class AuthController {

    private final AuthService authService;
    private final UsuarioAtual usuarioAtual;

    public AuthController(AuthService authService, UsuarioAtual usuarioAtual) {
        this.authService = authService;
        this.usuarioAtual = usuarioAtual;
    }

    @PostMapping("/cadastro")
    @SecurityRequirements
    @Operation(summary = "Cadastro de cuidador pelo app (papel CUIDADOR) já devolvendo o token")
    @ApiResponse(responseCode = "201", description = "Cadastrado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public ResponseEntity<AuthResposta> cadastrar(@RequestBody @Valid CadastroUsuarioRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrarCuidador(req));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Login com e-mail e senha; devolve o JWT para o cabeçalho Authorization")
    @ApiResponse(responseCode = "200", description = "Autenticado")
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos", content = @Content(schema = @Schema(implementation = ErroResposta.class)))
    public AuthResposta login(@RequestBody @Valid LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout: revoga o token atual (ele deixa de ser aceito)")
    @ApiResponse(responseCode = "204", description = "Token revogado")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do usuário logado")
    public UsuarioResposta me() {
        return authService.buscar(usuarioAtual.obter().id());
    }
}
