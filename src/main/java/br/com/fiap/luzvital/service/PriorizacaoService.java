package br.com.fiap.luzvital.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.fiap.luzvital.config.LuzVitalProperties;
import br.com.fiap.luzvital.domain.NotificacaoPonto;
import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.Paciente;
import br.com.fiap.luzvital.domain.PontoEnergia;
import br.com.fiap.luzvital.repository.NotificacaoPontoRepository;
import br.com.fiap.luzvital.repository.OcorrenciaRepository;
import br.com.fiap.luzvital.repository.PontoEnergiaRepository;

/**
 * Lógica do consumidor do evento energia.caiu: calcula a prioridade da ocorrência, encontra os
 * pontos de energia próximos e grava as notificações. É idempotente: reprocessar a mesma mensagem
 * só recalcula o score e não duplica notificações.
 */
@Service
public class PriorizacaoService {

    private static final Logger log = LoggerFactory.getLogger(PriorizacaoService.class);
    static final int MAX_PONTOS_AVISADOS = 5;

    private final OcorrenciaRepository ocorrencias;
    private final PontoEnergiaRepository pontos;
    private final NotificacaoPontoRepository notificacoes;
    private final double raioKm;

    public PriorizacaoService(OcorrenciaRepository ocorrencias, PontoEnergiaRepository pontos,
                              NotificacaoPontoRepository notificacoes, LuzVitalProperties props) {
        this.ocorrencias = ocorrencias;
        this.pontos = pontos;
        this.notificacoes = notificacoes;
        this.raioKm = props.notificacao().raioKm();
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
        for (Candidato c : pontosProximos(paciente)) {
            if (notificacoes.existsByOcorrenciaIdAndPontoEnergiaId(ocorrenciaId, c.ponto().getId())) {
                continue;
            }
            notificacoes.save(new NotificacaoPonto(ocorrencia, c.ponto(), c.distanciaKm(), c.criterio(),
                    mensagem(ocorrencia, paciente, agora), agora));
            novos++;
        }
        log.info("Ocorrência {} priorizada: {} min restantes, score {}, {} ponto(s) de energia avisado(s)",
                ocorrenciaId, ocorrencia.minutosRestantes(agora), ocorrencia.getPrioridadeScore(), novos);
    }

    /** Também usado quando a bateria é atualizada (equivalente ao gatilho TRG_UPDATE_BATERIA_PRIORIDADE). */
    public void recalcularScore(Ocorrencia ocorrencia, LocalDateTime agora) {
        int minutos = ocorrencia.minutosRestantes(agora);
        int score = RegraPrioridade.calcularScore(minutos, ocorrencia.getPaciente().possuiFonteReserva());
        ocorrencia.aplicarPrioridade(score, agora);
    }

    /**
     * Pontos disponíveis no raio configurado (quando paciente e ponto têm coordenadas) ou no mesmo bairro.
     * Pontos com gerador primeiro, depois os mais próximos.
     */
    List<Candidato> pontosProximos(Paciente paciente) {
        List<Candidato> candidatos = new ArrayList<>();
        for (PontoEnergia ponto : pontos.findByDisponivel(true)) {
            if (paciente.possuiCoordenadas() && ponto.possuiCoordenadas()) {
                BigDecimal km = Formatos.distanciaKm(paciente.getLatitude(), paciente.getLongitude(),
                        ponto.getLatitude(), ponto.getLongitude());
                if (km.doubleValue() <= raioKm) {
                    candidatos.add(new Candidato(ponto, km, NotificacaoPonto.CRITERIO_RAIO));
                }
            } else if (Formatos.mesmoBairro(ponto.getBairro(), paciente.getBairro())) {
                candidatos.add(new Candidato(ponto, null, NotificacaoPonto.CRITERIO_BAIRRO));
            }
        }
        return candidatos.stream()
                .sorted(Comparator.comparing((Candidato c) -> !c.ponto().isPossuiGerador())
                        .thenComparing(c -> c.distanciaKm() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : c.distanciaKm()))
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

    record Candidato(PontoEnergia ponto, BigDecimal distanciaKm, String criterio) {
    }
}
