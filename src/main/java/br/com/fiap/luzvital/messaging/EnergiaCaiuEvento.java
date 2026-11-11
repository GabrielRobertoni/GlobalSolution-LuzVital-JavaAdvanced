package br.com.fiap.luzvital.messaging;

import java.time.LocalDateTime;

/** Evento publicado em luzvital.eventos com a routing key energia.caiu quando uma ocorrência é aberta. */
public record EnergiaCaiuEvento(Long ocorrenciaId, Long pacienteId, String bairro, LocalDateTime dataInicio) {
}
