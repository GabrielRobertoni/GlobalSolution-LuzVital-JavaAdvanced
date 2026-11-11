package br.com.fiap.luzvital.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.luzvital.domain.PontoEnergia;

public interface PontoEnergiaRepository extends JpaRepository<PontoEnergia, Long> {

    @Query("""
            select p from PontoEnergia p
            where (:bairro is null or lower(p.bairro) = lower(cast(:bairro as string)))
              and (:disponivel is null or p.disponivel = :disponivel)
              and (:possuiGerador is null or p.possuiGerador = :possuiGerador)
            """)
    Page<PontoEnergia> buscar(@Param("bairro") String bairro, @Param("disponivel") Boolean disponivel,
                              @Param("possuiGerador") Boolean possuiGerador, Pageable pageable);

    List<PontoEnergia> findByDisponivel(Boolean disponivel);
}
