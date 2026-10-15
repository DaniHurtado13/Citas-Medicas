package com.citas.medicas.repository;

import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByNumeroDocumento(String document);
    boolean  existsByEmail(String email);
    boolean existsByNumeroDocumento(String document);
    boolean existsByRolAndDemoFalse(Rol rol);}
