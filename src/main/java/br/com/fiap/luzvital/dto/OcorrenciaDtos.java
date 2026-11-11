package br.com.fiap.luzvital.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import br.com.fiap.luzvital.domain.Atendimento;
import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.NotificacaoPonto;
import br.com.fiap.luzvital.domain.StatusOcorrencia;
import br.com.fiap.luzvital.domain.TipoAparelho;
import br.com.fiap.luzvital.domain.TipoAtendimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class OcorrenciaDtos {

    private OcorrenciaDtos() {
    }

    public static final String AVISO_EMERGENCIA = "O Luz Vital não substitui o SAMU (192), os Bombeiros (193) nem o "
            + "canal da distribuidora de energia e não dá orientação clínica. Em risco imediato, ligue 192.";

    @Schema(description = "Botão \"Estou sem luz\"")
    public record AberturaOcorrenciaRequest(
            @Schema(example = "1") @NotNull @Positive Long pacienteId,
            @Schema(example = "Queda de energia no bairro desde 21h") @Size(max = 250) String observacao) {
    }

    @Schema(description = "Bateria que resta agora no aparelho de suporte à vida, informada pelo cuidador ou pelo gestor")
    public record AtualizacaoBateriaRequest(
            @Schema(example = "180") @NotNull @PositiveOrZero @Max(99999) Integer minutosRestantes) {
    }

    public record EncerramentoRequest(
            @Schema(example = "Gerador comunitário entregue e aparelho funcionando") @NotBlank @Size(max = 200)
            String resultado) {
    }

    public record CancelamentoRequest(
            @Schema(example = "A energia voltou") @NotBlank @Size(max = 200) String motivo) {
    }

    public record AtendimentoRequest(
            @Schema(example = "GERADOR") @NotNull TipoAtendimento tipo,
            @Schema(example = "Gerador do Condomínio Edifício Paulista a caminho") @NotBlank @Size(max = 500)
            String descricao,
            @Schema(example = "1", description = "Opcional: ponto de energia acionado") @Positive Long pontoEnergiaId) {
    }

    public record AparelhoCriticoResposta(Long equipamentoId, TipoAparelho tipoAparelho, String fabricante,
                                          String modelo, Integer autonomiaMinutos) {
        public static AparelhoCriticoResposta de(Equipamento e) {
            return e == null ? null : new AparelhoCriticoResposta(e.getId(), e.getTipoAparelho(), e.getFabricante(),
                    e.getModelo(), e.getAutonomiaMinutos());
        }
    }

    public record AtendimentoResposta(Long id, Long ocorrenciaId, TipoAtendimento tipo, String descricao,
                                      Long pontoEnergiaId, String pontoEnergiaNome, Long gestorId, String gestorNome,
                                      LocalDateTime dataRegistro) {
        public static AtendimentoResposta de(Atendimento a) {
            return new AtendimentoResposta(a.getId(), a.getOcorrencia().getId(), a.getTipo(), a.getDescricao(),
                    a.getPontoEnergia() == null ? null : a.getPontoEnergia().getId(),
                    a.getPontoEnergia() == null ? null : a.getPontoEnergia().getNomeLocal(),
                    a.getGestor().getId(), a.getGestor().getNome(), a.getDataRegistro());
        }
    }

    public record NotificacaoResposta(Long id, Long pontoEnergiaId, String nomeLocal, String telefone, String bairro,
                                      boolean possuiGerador, BigDecimal distanciaKm, String criterio, String mensagem,
                                      LocalDateTime dataEnvio) {
        public static NotificacaoResposta de(NotificacaoPonto n) {
            var p = n.getPontoEnergia();
            return new NotificacaoResposta(n.getId(), p.getId(), p.getNomeLocal(), p.getTelefone(), p.getBairro(),
                    p.isPossuiGerador(), n.getDistanciaKm(), n.getCriterio(), n.getMensagem(), n.getDataEnvio());
        }
    }

    public record OcorrenciaResposta(
            Long id,
            Long pacienteId,
            String pacienteNome,
            String bairro,
            StatusOcorrencia status,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            @Schema(description = "Minutos de bateria informados em dataBateria") Integer bateriaInformadaMin,
            LocalDateTime dataBateria,
            @Schema(description = "Contagem regressiva calculada agora") int minutosRestantes,
            LocalDateTime previsaoEsgotamento,
            Integer prioridadeScore,
            @Schema(description = "false até o consumidor do evento energia.caiu calcular a prioridade") boolean priorizada,
            String observacao,
            AparelhoCriticoResposta aparelhoCritico,
            List<AtendimentoResposta> atendimentos,
            List<NotificacaoResposta> pontosNotificados,
            String aviso) {
    }

    @Schema(description = "Item da fila do gestor, ordenada pelos minutos de bateria que restam")
    public record FilaItemResposta(
            int posicao,
            Long ocorrenciaId,
            Long pacienteId,
            String pacienteNome,
            String bairro,
            String cidade,
            String contatoCuidador,
            StatusOcorrencia status,
            LocalDateTime dataInicio,
            int minutosRestantes,
            LocalDateTime previsaoEsgotamento,
            AparelhoCriticoResposta aparelhoCritico,
            boolean possuiFonteReserva,
            Integer prioridadeScore,
            boolean priorizada,
            int quantidadeAtendimentos,
            int quantidadePontosNotificados) {
    }

    public record IndicadoresResposta(
            long totalOcorrencias,
            long ocorrenciasAtivas,
            long ocorrenciasAtendidas,
            long ocorrenciasCanceladas,
            @Schema(description = "Média entre abertura e encerramento das ocorrências atendidas") Double tempoMedioAtendimentoMin,
            @Schema(description = "Atendidas antes do fim previsto da bateria / total de atendidas") Double percentualResolvidasAntesFimBateria,
            long pacientesCadastrados,
            Map<String, Long> pontosDisponiveisPorBairro) {
    }
}
