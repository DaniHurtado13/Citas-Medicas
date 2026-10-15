package com.citas.medicas.repository;

import com.citas.medicas.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    Optional<Doctor> findByUsuarioId(UUID usuarioId);

    boolean existsByEspecialidadId(UUID especialidadId);

    // El usuario se trae junto con el doctor porque DoctorResponse muestra si está activo (evita el N+1)
    @EntityGraph(attributePaths = {"especialidad", "usuario"})
    Page<Doctor> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"especialidad", "usuario"})
    Page<Doctor> findByEspecialidadId(UUID especialidadId, Pageable pageable);

    @EntityGraph(attributePaths = {"especialidad", "usuario"})
    Page<Doctor> findByUsuarioActivoTrue(Pageable pageable);

    @EntityGraph(attributePaths = {"especialidad", "usuario"})
    Page<Doctor> findByEspecialidadIdAndUsuarioActivoTrue(UUID especialidadId, Pageable pageable);

    @EntityGraph(attributePaths = {"especialidad", "usuario"})
    Optional<Doctor> findConEspecialidadById(UUID id);
}
