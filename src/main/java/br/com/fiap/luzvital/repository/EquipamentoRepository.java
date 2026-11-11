package br.com.fiap.luzvital.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.luzvital.domain.Equipamento;
import br.com.fiap.luzvital.domain.TipoAparelho;

public interface EquipamentoRepository extends JpaRepository<Equipamento, Long> {

    /** Lista paginada; cuidadorId nulo = todos (gestor). Filtros opcionais por paciente, tipo e suporte à vida. */
    @Query("""
            select e from Equipamento e
            where (:cuidadorId is null or e.paciente.cuidador.id = :cuidadorId)
              and (:pacienteId is null or e.paciente.id = :pacienteId)
              and (:tipo is null or e.tipoAparelho = :tipo)
              and (:suporteVida is null or e.suporteVida = :suporteVida)
            """)
    Page<Equipamento> buscar(@Param("cuidadorId") Long cuidadorId, @Param("pacienteId") Long pacienteId,
                             @Param("tipo") TipoAparelho tipo, @Param("suporteVida") Boolean suporteVida,
                             Pageable pageable);
}
