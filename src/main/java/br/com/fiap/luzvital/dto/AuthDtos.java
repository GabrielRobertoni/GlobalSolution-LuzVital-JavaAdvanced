package br.com.fiap.luzvital.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import br.com.fiap.luzvital.domain.Papel;
import br.com.fiap.luzvital.domain.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de autenticação e usuários (T_GS_USUARIO). */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** Telefone brasileiro com DDD, ex.: (11) 98888-1111 ou (11) 3251-0000. */
    public static final String REGEX_TELEFONE = "^\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}$";

    @Schema(description = "Cadastro de usuário (cuidador pelo app ou gestor criado por outro gestor)")
    public record CadastroUsuarioRequest(
            @Schema(example = "Ana Souza")
            @NotBlank @Size(max = 120) String nome,
            @Schema(example = "ana.souza@email.com")
            @NotBlank @Email @Size(max = 160) String email,
            @Schema(example = "Senha@123", description = "8 a 72 caracteres, com letra e número")
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "deve conter pelo menos uma letra e um número")
            String senha) {
    }

    public record LoginRequest(
            @Schema(example = "carlos.silva@gmail.com") @NotBlank @Email String email,
            @Schema(example = "hash_carlos123") @NotBlank String senha) {
    }

    public record UsuarioResposta(Long id, String nome, String email, Papel papel, boolean ativo,
                                  LocalDateTime dataCriacao) {
        public static UsuarioResposta de(Usuario u) {
            return new UsuarioResposta(u.getId(), u.getNome(), u.getEmail(), u.getPapel(), u.isAtivo(),
                    u.getDataCriacao());
        }
    }

    public record AuthResposta(
            @Schema(description = "JWT para o cabeçalho Authorization: Bearer <token>") String token,
            @Schema(example = "Bearer") String tipo,
            Instant expiraEm,
            UsuarioResposta usuario) {
    }
}
