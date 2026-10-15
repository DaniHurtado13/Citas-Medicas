package com.citas.medicas.repository;

import com.citas.medicas.entity.HorarioAtencion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, UUID> {

    List<HorarioAtencion> findByDoctorId(UUID doctorId);
    List<HorarioAtencion> findByDoctorIdAndDiaSemana(UUID doctorId, DayOfWeek diaSemana);
}
