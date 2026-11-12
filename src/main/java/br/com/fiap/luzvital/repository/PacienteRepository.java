package br.com.fiap.luzvital.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.luzvital.domain.Paciente;
import jakarta.persistence.LockModeType;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    /** Lista paginada; cuidadorId nulo = todos (gestor). Filtros opcionais por nome (parcial) e bairro. */
    @Query("""
            select p from Paciente p
            where (:cuidadorId is null or p.cuidador.id = :cuidadorId)
              and (:nome is null or lower(p.nome) like lower(concat('%', cast(:nome as string), '%')))
              and (:bairro is null or lower(p.bairro) = lower(cast(:bairro as string)))
            """)
    Page<Paciente> buscar(@Param("cuidadorId") Long cuidadorId, @Param("nome") String nome,
                          @Param("bairro") String bairro, Pageable pageable);

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    /** Trava a linha do paciente: dois toques simultâneos em "Estou sem luz" são processados em fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Paciente p where p.id = :id")
    Optional<Paciente> findByIdParaAtualizacao(@Param("id") Long id);
}
