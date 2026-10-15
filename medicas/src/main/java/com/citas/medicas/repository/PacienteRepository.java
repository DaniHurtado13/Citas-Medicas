package com.citas.medicas.repository;

import com.citas.medicas.entity.Paciente;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PacienteRepository extends JpaRepository<Paciente, UUID> {

    Optional<Paciente> findByUsuarioId(UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Paciente> findConBloqueoByUsuarioId(UUID usuarioId);
}
