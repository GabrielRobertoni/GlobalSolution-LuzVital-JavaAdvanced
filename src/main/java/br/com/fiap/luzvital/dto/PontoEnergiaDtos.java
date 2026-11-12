package br.com.fiap.luzvital.dto;

import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.TipoEstabelecimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PontoEnergiaDtos {

    private PontoEnergiaDtos() {
    }

    @Schema(description = "Ponto de energia comunitário (T_GS_PONTO_ENERGIA): condomínio, comércio ou posto público com gerador")
    public record PontoEnergiaRequest(
            @Schema(example = "Condomínio Edifício Paulista") @NotBlank @Size(max = 120) String nomeLocal,
            @Schema(example = "CONDOMINIO") @NotNull TipoEstabelecimento tipoEstabelecimento,
            @Schema(example = "Síndico Roberto") @NotBlank @Size(max = 100) String responsavel,
            @Schema(example = "(11) 3251-0000") @NotBlank
            @Pattern(regexp = AuthDtos.REGEX_TELEFONE, message = "deve ser um telefone válido, ex.: (11) 3251-0000")
            String telefone,
            @Schema(example = "Bela Vista") @NotBlank @Size(max = 80) String bairro,
            @Schema(example = "true") @NotNull Boolean possuiGerador,
            @Schema(example = "true", description = "Padrão: true") Boolean disponivel) {
    }

    public record PontoEnergiaResposta(Long id, String nomeLocal, TipoEstabelecimento tipoEstabelecimento,
                                       String responsavel, String telefone, String bairro, boolean possuiGerador,
                                       boolean disponivel) {
        public static PontoEnergiaResposta de(PontoEnergia p) {
            return new PontoEnergiaResposta(p.getId(), p.getNomeLocal(), p.getTipoEstabelecimento(), p.getResponsavel(),
                    p.getTelefone(), p.getBairro(), p.isPossuiGerador(), p.isDisponivel());
        }
    }
}
