package br.com.fiap.luzvital.messaging;

import java.time.LocalDateTime;

/** Evento publicado com a routing key plano.solicitado quando os aparelhos de um paciente mudam. */
public record PlanoSolicitadoEvento(Long pacienteId, String motivo, LocalDateTime solicitadoEm) {
}
