package br.com.fiap.luzvital.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica no RabbitMQ só depois do COMMIT da transação que gerou o evento: o consumidor nunca
 * recebe o id de uma ocorrência que ainda não está gravada (ou que sofreu rollback).
 * Se o RabbitMQ estiver fora do ar, a falha é registrada e o {@code ReprocessamentoJob} publica de novo.
 */
@Component
public class EventoPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventoPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoAbrirOcorrencia(EnergiaCaiuEvento evento) {
        enviar(RabbitConfig.RK_ENERGIA_CAIU, evento, "ocorrência " + evento.ocorrenciaId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoSolicitarPlano(PlanoSolicitadoEvento evento) {
        enviar(RabbitConfig.RK_PLANO_SOLICITADO, evento, "plano do paciente " + evento.pacienteId());
    }

    public boolean enviar(String routingKey, Object evento, String descricao) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento);
            log.info("Evento {} publicado ({})", routingKey, descricao);
            return true;
        } catch (AmqpException ex) {
            log.error("Falha ao publicar {} ({}): {}. Será reenviado pelo reprocessamento.", routingKey, descricao,
                    ex.getMessage());
            return false;
        }
    }
}
