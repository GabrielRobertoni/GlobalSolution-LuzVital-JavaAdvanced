package br.com.fiap.luzvital.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import br.com.fiap.luzvital.service.PlanoContingenciaService;
import br.com.fiap.luzvital.service.PriorizacaoService;

/**
 * Consumidores assíncronos. Erro no processamento: o Spring AMQP tenta 3 vezes (application.yml)
 * e depois manda a mensagem para a fila .dlq, sem ficar em loop.
 */
@Component
public class EventoConsumidor {

    private static final Logger log = LoggerFactory.getLogger(EventoConsumidor.class);

    private final PriorizacaoService priorizacaoService;
    private final PlanoContingenciaService planoService;

    public EventoConsumidor(PriorizacaoService priorizacaoService, PlanoContingenciaService planoService) {
        this.priorizacaoService = priorizacaoService;
        this.planoService = planoService;
    }

    /** energia.caiu: calcula a prioridade da ocorrência, busca pontos de energia próximos e grava as notificações. */
    @RabbitListener(queues = RabbitConfig.FILA_ENERGIA_CAIU)
    public void energiaCaiu(EnergiaCaiuEvento evento) {
        log.info("Consumindo energia.caiu da ocorrência {} (bairro {})", evento.ocorrenciaId(), evento.bairro());
        priorizacaoService.priorizar(evento.ocorrenciaId());
    }

    /** plano.solicitado: gera o plano de contingência com Spring AI (ou o plano padrão, se a IA estiver indisponível). */
    @RabbitListener(queues = RabbitConfig.FILA_PLANO)
    public void planoSolicitado(PlanoSolicitadoEvento evento) {
        log.info("Consumindo plano.solicitado do paciente {} ({})", evento.pacienteId(), evento.motivo());
        planoService.gerar(evento.pacienteId());
    }
}
