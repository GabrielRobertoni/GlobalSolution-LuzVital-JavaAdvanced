package br.com.fiap.luzvital.dto;

import java.time.LocalDateTime;

import br.com.fiap.luzvital.domain.OrigemPlano;
import br.com.fiap.luzvital.domain.StatusPlano;
import br.com.fiap.luzvital.ia.PlanoGerado;
import io.swagger.v3.oas.annotations.media.Schema;

public final class PlanoDtos {

    private PlanoDtos() {
    }

    public static final String AVISO_PLANO = "Plano de apoio gerado a partir dos aparelhos cadastrados. Não é orientação "
            + "médica e não substitui o SAMU (192), os Bombeiros (193) nem a distribuidora de energia. "
            + "Confira os dados com a equipe de saúde.";

    @Schema(description = "Plano de contingência do paciente. Com status GERANDO, consulte de novo em alguns segundos")
    public record PlanoContingenciaResposta(
            Long pacienteId,
            StatusPlano status,
            @Schema(description = "IA (gerado pelo LLM) ou REGRAS (plano padrão, quando a IA está desligada ou falhou)")
            OrigemPlano origem,
            String modeloIa,
            LocalDateTime dataSolicitacao,
            LocalDateTime dataGeracao,
            PlanoGerado plano,
            @Schema(description = "Motivo do uso do plano padrão ou do erro, quando houver") String observacao,
            String aviso) {
    }
}
