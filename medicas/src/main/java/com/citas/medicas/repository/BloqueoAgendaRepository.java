package com.citas.medicas.repository;

import com.citas.medicas.entity.BloqueoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BloqueoAgendaRepository extends JpaRepository<BloqueoAgenda, UUID> {

    List<BloqueoAgenda> findByDoctorIdAndFechaInicioLessThanAndFechaFinGreaterThan(UUID doctorId, LocalDateTime finDelDia, LocalDateTime inicioDelDia);

    List<BloqueoAgenda> findByDoctorIdAndFechaFinAfterOrderByFechaInicioAsc(UUID doctorId, LocalDateTime ahora);
}
