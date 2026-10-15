package com.citas.medicas.service;

import com.citas.medicas.entity.BloqueoAgenda;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.HorarioAtencion;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.BloqueoAgendaRepository;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.HorarioAtencionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DisponibilidadService {

    private final BloqueoAgendaRepository bloqueoAgendaRepository;
    private final CitaRepository citaRepository;
    private final HorarioAtencionRepository horarioAtencionRepository;
    private final DoctorRepository doctorRepository;
    private final Clock reloj;

    public DisponibilidadService(BloqueoAgendaRepository bloqueoAgendaRepository,
                                 CitaRepository citaRepository,
                                 HorarioAtencionRepository horarioAtencionRepository,
                                 DoctorRepository doctorRepository,
                                 Clock reloj) {
        this.bloqueoAgendaRepository = bloqueoAgendaRepository;
        this.citaRepository = citaRepository;
        this.horarioAtencionRepository = horarioAtencionRepository;
        this.doctorRepository = doctorRepository;
        this.reloj = reloj;
    }

    public List<LocalDateTime> calcularDisponibilidad(UUID doctorId, LocalDate fecha) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));
        if (!doctor.getUsuario().getActivo()) {
            return List.of();
        }

        LocalDateTime inicioDelDia = fecha.atStartOfDay();
        LocalDateTime finDelDia = fecha.plusDays(1).atStartOfDay();

        List<HorarioAtencion> horarios = horarioAtencionRepository
                .findByDoctorIdAndDiaSemana(doctorId, fecha.getDayOfWeek());
        List<BloqueoAgenda> bloqueos = bloqueoAgendaRepository
                .findByDoctorIdAndFechaInicioLessThanAndFechaFinGreaterThan(doctorId, finDelDia, inicioDelDia);
        List<Cita> citas = citaRepository
                .findByDoctorIdAndFechaHoraBetweenAndEstadoNot(doctorId, inicioDelDia, finDelDia, EstadoCita.CANCELADA);

        Duration duracion = Duration.ofMinutes(doctor.getDuracionConsultaMin());
        LocalDateTime ahora = LocalDateTime.now(reloj);

        List<LocalDateTime> disponibles = new ArrayList<>();
        for (LocalDateTime candidato : generarCandidatos(fecha, horarios, duracion)) {
            LocalDateTime finCandidato = candidato.plus(duracion);

            boolean bloqueado = bloqueos.stream().anyMatch(bloqueo ->
                    seSolapan(candidato, finCandidato, bloqueo.getFechaInicio(), bloqueo.getFechaFin()));
            boolean ocupado = citas.stream().anyMatch(cita ->
                    seSolapan(candidato, finCandidato, cita.getFechaHora(), cita.getFechaHora().plus(duracion)));
            boolean yaPaso = candidato.isBefore(ahora);

            if (!bloqueado && !ocupado && !yaPaso) {
                disponibles.add(candidato);
            }
        }
        return disponibles;
    }

    private List<LocalDateTime> generarCandidatos(LocalDate fecha, List<HorarioAtencion> horarios, Duration duracion) {
        List<LocalDateTime> candidatos = new ArrayList<>();
        for (HorarioAtencion horario : horarios) {
            LocalDateTime inicio = fecha.atTime(horario.getHoraInicio());
            LocalDateTime fin = fecha.atTime(horario.getHoraFin());
            while (!inicio.plus(duracion).isAfter(fin)) {
                candidatos.add(inicio);
                inicio = inicio.plus(duracion);
            }
        }
        candidatos.sort(Comparator.naturalOrder());
        return candidatos;
    }

    private boolean seSolapan(LocalDateTime inicioA, LocalDateTime finA, LocalDateTime inicioB, LocalDateTime finB) {
        return inicioA.isBefore(finB) && inicioB.isBefore(finA);
    }
}
