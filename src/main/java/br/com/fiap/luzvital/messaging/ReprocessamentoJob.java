package br.com.fiap.luzvital.messaging;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.StatusOcorrencia;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.security.TokensRevogados;
import br.com.fiap.luzvital.service.PlanoContingenciaService;

/**
 * Rede de segurança da mensageria: se o RabbitMQ estava fora do ar quando a ocorrência foi aberta
 * (ou o plano solicitado), o evento é publicado de novo. Só atua em ocorrências abertas pela API que ainda
 * estão com score 0; os registros da carga do banco não são tocados. Também limpa tokens revogados expirados.
 */
@Component
public class ReprocessamentoJob {

    private static final Logger log = LoggerFactory.getLogger(ReprocessamentoJob.class);
    private static final List<StatusOcorrencia> ATIVAS = List.of(StatusOcorrencia.EM_ABERTO, StatusOcorrencia.EM_ATENDIMENTO);

    private final OcorrenciaRepository ocorrencias;
    private final PlanoContingenciaService planos;
    private final TokensRevogados revogados;
    private final EventoPublisher publisher;

    public ReprocessamentoJob(OcorrenciaRepository ocorrencias, PlanoContingenciaService planos,
                              TokensRevogados revogados, EventoPublisher publisher) {
        this.ocorrencias = ocorrencias;
        this.planos = planos;
        this.revogados = revogados;
        this.publisher = publisher;
    }

    @Scheduled(initialDelayString = "${luzvital.reprocessamento.atraso-inicial:60s}",
            fixedDelayString = "${luzvital.reprocessamento.intervalo:60s}")
    @Transactional(readOnly = true)
    public void reprocessar() {
        LocalDateTime agora = LocalDateTime.now();
        for (Ocorrencia o : ocorrencias.buscarNaoPriorizadas(ATIVAS, agora.minusSeconds(30))) {
            log.warn("Ocorrência {} ainda sem prioridade: publicando energia.caiu de novo", o.getId());
            publisher.enviar(RabbitConfig.RK_ENERGIA_CAIU,
                    new EnergiaCaiuEvento(o.getId(), o.getPaciente().getId(), o.getPaciente().getBairro(), o.getDataInicio()),
                    "reprocessamento da ocorrência " + o.getId());
        }
        for (Long pacienteId : planos.pendentesDesde(agora.minusMinutes(10))) {
            log.warn("Plano do paciente {} parado em GERANDO: publicando plano.solicitado de novo", pacienteId);
            publisher.enviar(RabbitConfig.RK_PLANO_SOLICITADO,
                    new PlanoSolicitadoEvento(pacienteId, "reprocessamento", planos.solicitadoEm(pacienteId)),
                    "reprocessamento do plano do paciente " + pacienteId);
        }
        int removidos = revogados.removerExpirados();
        if (removidos > 0) {
            log.info("{} token(s) revogado(s) expirado(s) removido(s)", removidos);
        }
    }
}
