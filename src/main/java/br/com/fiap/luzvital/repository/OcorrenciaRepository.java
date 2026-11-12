package br.com.fiap.luzvital.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.luzvital.domain.Ocorrencia;
import br.com.fiap.luzvital.domain.StatusOcorrencia;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    @Query("select c.ocorrencia from OcorrenciaControle c where c.chaveIdempotencia = :chave")
    Optional<Ocorrencia> findByChaveIdempotencia(@Param("chave") String chaveIdempotencia);

    Optional<Ocorrencia> findFirstByPacienteIdAndStatusIn(Long pacienteId, Collection<StatusOcorrencia> status);

    boolean existsByPacienteId(Long pacienteId);

    @Query("""
            select o from Ocorrencia o
            where (:cuidadorId is null or o.paciente.cuidador.id = :cuidadorId)
              and (:pacienteId is null or o.paciente.id = :pacienteId)
              and (:status is null or o.status = :status)
            """)
    Page<Ocorrencia> buscar(@Param("cuidadorId") Long cuidadorId, @Param("pacienteId") Long pacienteId,
                            @Param("status") StatusOcorrencia status, Pageable pageable);

    /** Ocorrências ativas com paciente já carregado: base da fila do gestor. */
    @EntityGraph(attributePaths = {"paciente"})
    List<Ocorrencia> findByStatusIn(Collection<StatusOcorrencia> status);

    /** Ocorrências que o consumidor ainda não priorizou (ex.: RabbitMQ fora do ar na abertura). */
    @Query("""
            select o from Ocorrencia o left join o.controle c
            where o.status in :status and o.dataInicio < :limite
              and ((c.id is null and o.prioridadeScore = 0) or (c.id is not null and c.dataPriorizacao is null))
            """)
    List<Ocorrencia> buscarNaoPriorizadas(@Param("status") Collection<StatusOcorrencia> status,
                                          @Param("limite") LocalDateTime limite);
}
