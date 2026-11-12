package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.Comparator;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.Ocorrencia;

/**
 * Regra de prioridade do Luz Vital, idêntica ao pacote Oracle PKG_T_GS_EQUIPAMENTO:
 * <ul>
 *   <li>Minutos restantes (FN_T_GS_MINUTOS_RESTANTES): menor autonomia entre os aparelhos de suporte à vida,
 *       descontado o tempo desde que a bateria foi informada.</li>
 *   <li>Score (FN_T_GS_CALCULA_PRIORIDADE): 0 min = 1000; até 60 min = 800 + (60 - min);
 *       até 180 min = 500 + (180 - min); acima = 100; +200 se o paciente usa aparelho de suporte à vida.</li>
 *   <li>Fila do gestor: menos minutos primeiro; no empate, ventilador antes de concentrador; depois maior score
 *       e ordem de chegada.</li>
 * </ul>
 * É uma regra inicial, a validar com um profissional de saúde (decisão de projeto, não protocolo clínico).
 */
public final class RegraPrioridade {

    public static final int BONUS_SUPORTE_VIDA = 200;

    private RegraPrioridade() {
    }

    public static int calcularScore(int minutosRestantes, boolean suporteVida) {
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
        if (suporteVida) {
            score += BONUS_SUPORTE_VIDA;
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
