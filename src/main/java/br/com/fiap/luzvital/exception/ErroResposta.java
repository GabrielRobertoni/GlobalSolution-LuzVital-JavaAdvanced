package br.com.fiap.luzvital.exception;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/** Formato único de erro de toda a API (validação, regra de negócio, 401, 403, 404, 409, 500...). */
@Schema(description = "Formato padrão de erro da API")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResposta(
        @Schema(example = "2026-11-10T21:30:00") LocalDateTime timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String erro,
        @Schema(example = "Dados inválidos") String mensagem,
        @Schema(example = "/equipamentos") String caminho,
        List<CampoInvalido> campos) {

    public record CampoInvalido(
            @Schema(example = "potenciaWatts") String campo,
            @Schema(example = "deve ser maior que 0") String mensagem) {
    }
}
