package br.com.fiap.luzvital.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import br.com.fiap.luzvital.domain.Ocorrencia;

/**
 * Estado operacional das ocorrências que não faz parte do modelo do banco da disciplina (as tabelas T_GS_*
 * são usadas sem nenhuma alteração). Fica em memória da API:
 * <ul>
 *   <li>chave de idempotência enviada pelo app ("Estou sem luz" reenviado devolve a mesma ocorrência);</li>
 *   <li>momento em que a bateria foi informada (contagem regressiva após atualizar a bateria);</li>
 *   <li>avisos aos pontos de energia feitos pelo consumidor do evento energia.caiu.</li>
 * </ul>
 * Os dados de negócio (ocorrência, bateria, score, atendimentos) ficam no banco. Se a API reiniciar, a contagem
 * volta a usar dh_inicio e a regra "uma ocorrência ativa por paciente" continua impedindo duplicidade.
 */
@Component
public class EstadoOcorrencias {

    private final Map<String, Long> chaves = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> bateriaInformadaEm = new ConcurrentHashMap<>();
    private final Map<Long, List<AvisoPonto>> avisos = new ConcurrentHashMap<>();

    public Optional<Long> ocorrenciaDaChave(String chave) {
        return Optional.ofNullable(chave == null ? null : chaves.get(chave));
    }

    public void registrarChave(String chave, Long ocorrenciaId) {
        if (chave != null) {
            chaves.put(chave, ocorrenciaId);
        }
    }

    /** Momento em que qt_bateria_min foi informada: a última atualização ou, se não houver, dh_inicio. */
    public LocalDateTime referenciaBateria(Ocorrencia o) {
        LocalDateTime informadaEm = o.getId() == null ? null : bateriaInformadaEm.get(o.getId());
        return informadaEm != null ? informadaEm : o.getDataInicio();
    }

    public void registrarBateria(Long ocorrenciaId, LocalDateTime agora) {
        bateriaInformadaEm.put(ocorrenciaId, agora);
    }

    public int minutosRestantes(Ocorrencia o, LocalDateTime agora) {
        return o.minutosRestantes(referenciaBateria(o), agora);
    }

    public LocalDateTime previsaoEsgotamento(Ocorrencia o) {
        return o.previsaoEsgotamento(referenciaBateria(o));
    }

    public List<AvisoPonto> avisos(Long ocorrenciaId) {
        return List.copyOf(avisos.getOrDefault(ocorrenciaId, List.of()));
    }

    /** Registra o aviso; devolve false se o ponto já tinha sido avisado (consumidor idempotente). */
    public boolean registrarAviso(Long ocorrenciaId, AvisoPonto aviso) {
        List<AvisoPonto> lista = avisos.computeIfAbsent(ocorrenciaId, id -> new ArrayList<>());
        synchronized (lista) {
            if (lista.stream().anyMatch(a -> a.pontoEnergiaId().equals(aviso.pontoEnergiaId()))) {
                return false;
            }
            lista.add(aviso);
            return true;
        }
    }

    public record AvisoPonto(Long pontoEnergiaId, String nomeLocal, String telefone, String bairro,
                             boolean possuiGerador, String mensagem, LocalDateTime dataEnvio) {
    }
}
