package br.com.fiap.luzvital.dto;

import java.math.BigDecimal;

import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.domain.TipoEstabelecimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PontoEnergiaDtos {

    private PontoEnergiaDtos() {
    }

    @Schema(description = "Ponto de energia voluntário (condomínio, comércio, vizinho) com gerador ou bateria")
    public record PontoEnergiaRequest(
            @Schema(example = "Condomínio Edifício Paulista") @NotBlank @Size(max = 100) String nomeLocal,
            @Schema(example = "CONDOMINIO") @NotNull TipoEstabelecimento tipoEstabelecimento,
            @Schema(example = "Síndico Roberto") @NotBlank @Size(max = 100) String responsavel,
            @Schema(example = "(11) 3251-0000") @NotBlank
            @Pattern(regexp = AuthDtos.REGEX_TELEFONE, message = "deve ser um telefone válido, ex.: (11) 3251-0000")
            String telefone,
            @Schema(example = "Bela Vista") @NotBlank @Size(max = 50) String bairro,
            @Schema(example = "true") @NotNull Boolean possuiGerador,
            @Schema(example = "true", description = "Padrão: true") Boolean disponivel,
            @Schema(example = "-23.561400")
            @DecimalMin(value = "-90.0", message = "latitude deve estar entre -90 e 90")
            @DecimalMax(value = "90.0", message = "latitude deve estar entre -90 e 90") BigDecimal latitude,
            @Schema(example = "-46.655900")
            @DecimalMin(value = "-180.0", message = "longitude deve estar entre -180 e 180")
            @DecimalMax(value = "180.0", message = "longitude deve estar entre -180 e 180") BigDecimal longitude) {
    }

    public record PontoEnergiaResposta(Long id, String nomeLocal, TipoEstabelecimento tipoEstabelecimento,
                                       String responsavel, String telefone, String bairro, boolean possuiGerador,
                                       boolean disponivel, BigDecimal latitude, BigDecimal longitude) {
        public static PontoEnergiaResposta de(PontoEnergia p) {
            return new PontoEnergiaResposta(p.getId(), p.getNomeLocal(), p.getTipoEstabelecimento(), p.getResponsavel(),
                    p.getTelefone(), p.getBairro(), p.isPossuiGerador(), p.isDisponivel(), p.getLatitude(),
                    p.getLongitude());
        }
    }
}
