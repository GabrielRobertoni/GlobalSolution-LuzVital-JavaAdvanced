package br.com.fiap.luzvital.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.luzvital.domain.NotificacaoPonto;

public interface NotificacaoPontoRepository extends JpaRepository<NotificacaoPonto, Long> {

    boolean existsByOcorrenciaIdAndPontoEnergiaId(Long ocorrenciaId, Long pontoId);

    List<NotificacaoPonto> findByOcorrenciaId(Long ocorrenciaId);

    long countByOcorrenciaId(Long ocorrenciaId);

    boolean existsByPontoEnergiaId(Long pontoId);
}
