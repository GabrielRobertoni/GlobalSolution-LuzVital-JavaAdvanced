package br.com.fiap.luzvital.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.luzvital.domain.Atendimento;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    List<Atendimento> findByOcorrenciaIdOrderByDataAtendimentoAsc(Long ocorrenciaId);

    boolean existsByPontoEnergiaId(Long pontoId);
}
