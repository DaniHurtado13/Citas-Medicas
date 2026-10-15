package com.citas.medicas.repository;

import com.citas.medicas.entity.Especialidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);
}
