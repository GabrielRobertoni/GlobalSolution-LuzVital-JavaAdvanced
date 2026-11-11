package br.com.fiap.luzvital.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fiap.luzvital.domain.PlanoContingencia;
import br.com.fiap.luzvital.domain.StatusPlano;

public interface PlanoContingenciaRepository extends JpaRepository<PlanoContingencia, Long> {

    Optional<PlanoContingencia> findByPacienteId(Long pacienteId);

    List<PlanoContingencia> findByStatusAndDataSolicitacaoBefore(StatusPlano status, LocalDateTime limite);
}
