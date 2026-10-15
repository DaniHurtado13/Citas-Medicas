package com.citas.medicas.repository;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.TipoNotificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CitaRepository extends JpaRepository<Cita, UUID> {

    List<Cita> findByDoctorIdAndFechaHoraBetweenAndEstadoNot(
            UUID doctorId, LocalDateTime inicioDelDia, LocalDateTime finDelDia, EstadoCita estado);

    @EntityGraph(attributePaths = {"paciente", "doctor"})
    Page<Cita> findByPacienteId(UUID pacienteId, Pageable pageable);

    List<Cita> findByDoctorIdAndEstadoInAndFechaHoraAfter(
            UUID doctorId, Collection<EstadoCita> estados, LocalDateTime desde);

    long countByPacienteIdAndEstadoInAndFechaHoraAfter(
            UUID pacienteId, Collection<EstadoCita> estados, LocalDateTime desde);

    boolean existsByPacienteIdAndDoctorIdAndEstadoInAndFechaHoraAfter(
            UUID pacienteId, UUID doctorId, Collection<EstadoCita> estados, LocalDateTime desde);

    @EntityGraph(attributePaths = {"paciente", "doctor"})
    List<Cita> findByDoctorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
            UUID doctorId, LocalDateTime desde, LocalDateTime hasta, EstadoCita estado);

    @Query("""
            SELECT c FROM Cita c
            WHERE c.estado = :estado
              AND c.fechaHora BETWEEN :desde AND :hasta
              AND NOT EXISTS (
                  SELECT n FROM Notificacion n
                  WHERE n.cita = c AND n.tipo = :tipo
              )
            """)
    List<Cita> buscarSinNotificacion(@Param("estado") EstadoCita estado,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta,
                                     @Param("tipo") TipoNotificacion tipo);
}

