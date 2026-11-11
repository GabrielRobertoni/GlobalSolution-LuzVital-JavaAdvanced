package br.com.fiap.luzvital.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.validator.constraints.br.CPF;

import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.StatusPlano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PacienteDtos {

    private PacienteDtos() {
    }

    @Schema(description = "Dados do paciente eletrodependente (use dados fictícios: saúde e endereço são dados sensíveis)")
    public record PacienteRequest(
            @Schema(example = "Maria Oliveira") @NotBlank @Size(max = 100) String nome,
            @Schema(example = "123.456.789-09") @NotBlank @CPF(message = "CPF inválido") String cpf,
            @Schema(example = "Rua das Flores") @NotBlank @Size(max = 150) String logradouro,
            @Schema(example = "105") @NotBlank @Size(max = 20) String numero,
            @Schema(example = "Jardim Paulista") @NotBlank @Size(max = 50) String bairro,
            @Schema(example = "São Paulo") @NotBlank @Size(max = 50) String cidade,
            @Schema(example = "SP") @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "deve ter 2 letras") String uf,
            @Schema(example = "01415-000") @NotBlank
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "deve estar no formato 00000-000") String cep,
            @Schema(example = "(11) 98888-1111") @NotBlank
            @Pattern(regexp = AuthDtos.REGEX_TELEFONE, message = "deve ser um telefone válido, ex.: (11) 98888-1111")
            String contatoCuidador,
            @Schema(example = "-23.567300", description = "Opcional; usado para achar pontos de energia por distância")
            @DecimalMin(value = "-90.0", message = "latitude deve estar entre -90 e 90")
            @DecimalMax(value = "90.0", message = "latitude deve estar entre -90 e 90") BigDecimal latitude,
            @Schema(example = "-46.657400")
            @DecimalMin(value = "-180.0", message = "longitude deve estar entre -180 e 180")
            @DecimalMax(value = "180.0", message = "longitude deve estar entre -180 e 180") BigDecimal longitude) {
    }

    public record PacienteResposta(
            Long id,
            Long cuidadorId,
            String nome,
            String cpf,
            String logradouro,
            String numero,
            String bairro,
            String cidade,
            String uf,
            String cep,
            String contatoCuidador,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime dataCadastro,
            @Schema(description = "Aparelhos do paciente") List<EquipamentoDtos.EquipamentoResposta> equipamentos,
            @Schema(description = "Situação do plano de contingência (nulo se ainda não há aparelhos)") StatusPlano statusPlano) {

        public static PacienteResposta de(Paciente p) {
            return new PacienteResposta(p.getId(), p.getCuidador() == null ? null : p.getCuidador().getId(), p.getNome(), p.getCpf(), p.getLogradouro(),
                    p.getNumero(), p.getBairro(), p.getCidade(), p.getUf(), p.getCep(), p.getContatoCuidador(),
                    p.getLatitude(), p.getLongitude(), p.getDataCadastro(),
                    p.getEquipamentos().stream().map(EquipamentoDtos.EquipamentoResposta::de).toList(),
                    p.getPlano() == null ? null : p.getPlano().getStatus());
        }
    }
}
