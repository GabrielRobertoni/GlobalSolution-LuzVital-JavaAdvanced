package br.com.fiap.luzvital.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.fiap.luzvital.domain.TokenRevogado;

public interface TokenRevogadoRepository extends JpaRepository<TokenRevogado, String> {

    @Modifying
    @Query("delete from TokenRevogado t where t.dataExpiracao < :agora")
    int removerExpirados(@Param("agora") Instant agora);
}
