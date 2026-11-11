package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.Comparator;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.Ocorrencia;

/**
 * Regra de prioridade do Luz Vital (mesma lógica das funções Oracle FN_MINUTOS_RESTANTES e
 * FN_CALCULA_PRIORIDADE do script de banco).
 * <ul>
 *   <li>Minutos restantes: menor autonomia entre os aparelhos de suporte à vida, menos o tempo desde a queda.</li>
 *   <li>Score: quanto menos minutos, maior a urgência; sem fonte reserva soma 150.</li>
 *   <li>Fila: menos minutos primeiro; no empate, ventilador antes de concentrador; depois maior score e chegada.</li>
 * </ul>
 * É uma regra inicial, a validar com um profissional de saúde (decisão de projeto, não protocolo clínico).
 */
public final class RegraPrioridade {

    public static final int BONUS_SEM_RESERVA = 150;

    private RegraPrioridade() {
    }

    public static int calcularScore(int minutosRestantes, boolean possuiFonteReserva) {
        int score;
        if (minutosRestantes <= 0) {
            score = 1000;
        } else if (minutosRestantes <= 60) {
            score = 800 + (60 - minutosRestantes);
        } else if (minutosRestantes <= 180) {
            score = 500 + (180 - minutosRestantes);
        } else {
            score = 100;
        }
        if (!possuiFonteReserva) {
            score += BONUS_SEM_RESERVA;
        }
        return score;
    }

    /** Ordem da fila do gestor, avaliada no instante {@code agora}. */
    public static Comparator<Ocorrencia> ordemDaFila(LocalDateTime agora) {
        return Comparator.<Ocorrencia>comparingInt(o -> o.minutosRestantes(agora))
                .thenComparingInt(o -> o.getPaciente().equipamentoCritico()
                        .map(Equipamento::getTipoAparelho).map(t -> t.ordemDesempate()).orElse(Integer.MAX_VALUE))
                .thenComparing(Ocorrencia::getPrioridadeScore, Comparator.reverseOrder())
                .thenComparing(Ocorrencia::getDataInicio);
    }
}
