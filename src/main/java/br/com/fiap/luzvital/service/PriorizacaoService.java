package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.domain.NotificacaoPonto;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.repository.NotificacaoPontoRepository;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;

/**
 * Lógica do consumidor do evento energia.caiu: calcula a prioridade da ocorrência, encontra os pontos de
 * energia disponíveis no bairro do paciente e grava os avisos. É idempotente: reprocessar a mesma mensagem
 * só recalcula o score e não duplica avisos.
 */
@Service
public class PriorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(PriorizacaoService.class);
    static final int MAX_PONTOS_AVISADOS = 5;

    private final OcorrenciaRepository ocorrencias;
    private final PontoEnergiaRepository pontos;
    private final NotificacaoPontoRepository notificacoes;

    public PriorizacaoService(OcorrenciaRepository ocorrencias, PontoEnergiaRepository pontos,
                              NotificacaoPontoRepository notificacoes) {
        this.ocorrencias = ocorrencias;
        this.pontos = pontos;
        this.notificacoes = notificacoes;
    }

    @Transactional
    public void priorizar(Long ocorrenciaId) {
        Ocorrencia ocorrencia = ocorrencias.findById(ocorrenciaId).orElse(null);
        if (ocorrencia == null || !ocorrencia.isAtiva()) {
            log.info("Ocorrência {} inexistente ou já finalizada: nada a priorizar", ocorrenciaId);
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        recalcularScore(ocorrencia, agora);

        Paciente paciente = ocorrencia.getPaciente();
        int novos = 0;
        for (PontoEnergia ponto : pontosDoBairro(paciente)) {
            if (notificacoes.existsByOcorrenciaIdAndPontoEnergiaId(ocorrenciaId, ponto.getId())) {
                continue;
            }
            notificacoes.save(new NotificacaoPonto(ocorrencia, ponto, mensagem(ocorrencia, paciente, agora), agora));
            novos++;
        }
        log.info("Ocorrência {} priorizada: {} min restantes, score {}, {} ponto(s) de energia avisado(s)",
                ocorrenciaId, ocorrencia.minutosRestantes(agora), ocorrencia.getPrioridadeScore(), novos);
    }

    /** Também usado quando a bateria é atualizada (mesmo papel do gatilho TRG_T_GS_UPDATE_BATERIA). */
    public void recalcularScore(Ocorrencia ocorrencia, LocalDateTime agora) {
        int minutos = ocorrencia.minutosRestantes(agora);
        int score = RegraPrioridade.calcularScore(minutos, ocorrencia.getPaciente().possuiSuporteVida());
        ocorrencia.aplicarPrioridade(score, agora);
    }

    /** Pontos disponíveis no mesmo bairro do paciente, com gerador primeiro. */
    List<PontoEnergia> pontosDoBairro(Paciente paciente) {
        return pontos.findByDisponivel(true).stream()
                .filter(p -> Formatos.mesmoBairro(p.getBairro(), paciente.getBairro()))
                .sorted(Comparator.comparing((PontoEnergia p) -> !p.isPossuiGerador()).thenComparing(PontoEnergia::getId))
                .limit(MAX_PONTOS_AVISADOS)
                .toList();
    }

    private static String mensagem(Ocorrencia o, Paciente paciente, LocalDateTime agora) {
        String aparelho = paciente.equipamentoCritico().map(e -> e.getTipoAparelho().name()).orElse("aparelho vital");
        String texto = "Luz Vital: paciente eletrodependente sem energia no bairro " + paciente.getBairro()
                + " (" + aparelho + ", cerca de " + o.minutosRestantes(agora) + " min de bateria). "
                + "O gestor pode acionar este ponto para recarga ou apoio. Ocorrência #" + o.getId() + ".";
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }
}
