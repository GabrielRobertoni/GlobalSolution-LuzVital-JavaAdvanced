package br.com.fiap.luzvital.dto;

import java.time.LocalDateTime;
import java.util.List;

import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.StatusPlano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PacienteDtos {

    private PacienteDtos() {
    }

    @Schema(description = "Paciente eletrodependente (T_GS_PACIENTE). Use dados fictícios: saúde e endereço são dados sensíveis")
    public record PacienteRequest(
            @Schema(example = "Maria Aparecida Rocha") @NotBlank @Size(max = 120) String nome,
            @Schema(example = "987.654.321-02", description = "Formato 000.000.000-00")
            @NotBlank @Pattern(regexp = "^\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}$", message = "deve estar no formato 000.000.000-00")
            String cpf,
            @Schema(example = "Av. Brigadeiro Luís Antônio, 2100") @NotBlank @Size(max = 200) String endereco,
            @Schema(example = "Bela Vista") @NotBlank @Size(max = 80) String bairro,
            @Schema(example = "São Paulo") @NotBlank @Size(max = 80) String cidade,
            @Schema(example = "SP") @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "deve ter 2 letras") String uf,
            @Schema(example = "01318-000") @NotBlank
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "deve estar no formato 00000-000") String cep,
            @Schema(example = "(11) 97777-2222", description = "Telefone de contato em emergência") @NotBlank
            @Pattern(regexp = AuthDtos.REGEX_TELEFONE, message = "deve ser um telefone válido, ex.: (11) 98888-1111")
            String telefone) {
    }

    public record PacienteResposta(
            Long id,
            Long cuidadorId,
            String nome,
            String cpf,
            String endereco,
            String bairro,
            String cidade,
            String uf,
            String cep,
            String telefone,
            LocalDateTime dataCriacao,
            @Schema(description = "Aparelhos do paciente") List<EquipamentoDtos.EquipamentoResposta> equipamentos,
            @Schema(description = "Situação do plano de contingência (nulo se ainda não há aparelhos)") StatusPlano statusPlano) {

        public static PacienteResposta de(Paciente p, StatusPlano statusPlano) {
            return new PacienteResposta(p.getId(), p.getCuidador().getId(), p.getNome(), p.getCpf(), p.getEndereco(),
                    p.getBairro(), p.getCidade(), p.getUf(), p.getCep(), p.getTelefone(), p.getDataCriacao(),
                    p.getEquipamentos().stream().map(EquipamentoDtos.EquipamentoResposta::de).toList(),
                    statusPlano);
        }
    }
}
