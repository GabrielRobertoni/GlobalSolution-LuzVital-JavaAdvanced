package br.com.fiap.luzvital.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.FonteReserva;
import br.com.fiap.luzvital.domain.TipoAparelho;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class EquipamentoDtos {

    private EquipamentoDtos() {
    }

    @Schema(description = "Aparelho de uso contínuo. Salvar ou alterar dispara a geração do plano de contingência pela IA")
    public record EquipamentoRequest(
            @Schema(example = "1") @NotNull @Positive Long pacienteId,
            @Schema(example = "VENTILADOR_MECANICO") @NotNull TipoAparelho tipoAparelho,
            @Schema(example = "Philips") @NotBlank @Size(max = 80) String fabricante,
            @Schema(example = "Trilogy Evo") @NotBlank @Size(max = 80) String modelo,
            @Schema(example = "120.00", description = "Potência em watts (maior que zero)")
            @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "a potência deve ser maior que zero")
            @DecimalMax(value = "9999.99", message = "a potência deve ser no máximo 9999.99 W")
            @Digits(integer = 4, fraction = 2) BigDecimal potenciaWatts,
            @Schema(example = "900", description = "Autonomia da bateria cheia em minutos; 0 se o aparelho não tem bateria")
            @NotNull @PositiveOrZero(message = "a autonomia não pode ser negativa")
            @Max(value = 99999, message = "a autonomia deve ser no máximo 99999 minutos") Integer autonomiaMinutos,
            @Schema(example = "true") @NotNull Boolean suporteVida,
            @Schema(example = "BATERIA_EXTERNA", description = "Padrão: NENHUMA") FonteReserva fonteReserva) {
    }

    public record EquipamentoResposta(
            Long id,
            Long pacienteId,
            TipoAparelho tipoAparelho,
            String fabricante,
            String modelo,
            BigDecimal potenciaWatts,
            Integer autonomiaMinutos,
            boolean suporteVida,
            FonteReserva fonteReserva,
            @Schema(description = "Consumo se ligado 24 h (potência x 24 h)", example = "2.88") BigDecimal consumoDiarioKwh,
            @Schema(description = "Horas que uma estação de energia de 1 kWh mantém o aparelho", example = "8.3")
            BigDecimal horasComEstacao1kWh) {

        public static EquipamentoResposta de(Equipamento e) {
            BigDecimal potencia = e.getPotenciaWatts();
            BigDecimal consumo = potencia.multiply(BigDecimal.valueOf(24)).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
            BigDecimal horasEstacao = BigDecimal.valueOf(1000).divide(potencia, 1, RoundingMode.HALF_UP);
            return new EquipamentoResposta(e.getId(), e.getPaciente().getId(), e.getTipoAparelho(), e.getFabricante(),
                    e.getModelo(), potencia, e.getAutonomiaMinutos(), e.isSuporteVida(), e.getFonteReserva(),
                    consumo, horasEstacao);
        }
    }

    @Schema(description = "Dados lidos da foto da etiqueta. O cuidador sempre confirma antes de salvar")
    public record EtiquetaLidaResposta(
            @Schema(example = "true") boolean legivel,
            @Schema(example = "Philips Respironics") String fabricante,
            @Schema(example = "EverFlo") String modelo,
            @Schema(example = "CONCENTRADOR_OXIGENIO") TipoAparelho tipoAparelhoSugerido,
            @Schema(example = "220") BigDecimal tensaoVolts,
            @Schema(example = "1.6") BigDecimal correnteAmperes,
            @Schema(example = "350") BigDecimal potenciaWatts,
            @Schema(description = "true quando a potência foi calculada por volts x ampères (potência aparente)")
            boolean potenciaEstimada,
            @Schema(example = "Etiqueta com reflexo; modelo pode estar incompleto") String observacoes,
            String aviso) {
    }
}
