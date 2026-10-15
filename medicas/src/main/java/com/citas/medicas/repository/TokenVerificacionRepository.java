package com.citas.medicas.repository;

import com.citas.medicas.entity.PropositoToken;
import com.citas.medicas.entity.TokenVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TokenVerificacionRepository extends JpaRepository<TokenVerificacion, UUID> {

    @Query("select t from TokenVerificacion t join fetch t.usuario where t.hash = :hash and t.proposito = :proposito")
    Optional<TokenVerificacion> buscarConUsuario(@Param("hash") String hash, @Param("proposito") PropositoToken proposito);

    @Modifying
    @Query("delete from TokenVerificacion t where t.id = :id")
    int borrarPorId(@Param("id") UUID id);

    @Modifying
    @Query("delete from TokenVerificacion t where t.usuario.id = :usuarioId and t.proposito = :proposito")
    void borrarDelUsuario(@Param("usuarioId") UUID usuarioId, @Param("proposito") PropositoToken proposito);
}
