package com.citas.medicas.service;

import com.citas.medicas.dto.request.BloqueoRequest;
import com.citas.medicas.entity.BloqueoAgenda;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.exception.FechaInvalidaException;
import com.citas.medicas.exception.HorarioSolapadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.BloqueoAgendaRepository;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class BloqueoService {

    private final BloqueoAgendaRepository bloqueoRepository;
    private final DoctorRepository doctorRepository;
    private final CitaRepository citaRepository;
    private final Clock reloj;

    public BloqueoService(BloqueoAgendaRepository bloqueoRepository,
                          DoctorRepository doctorRepository,
                          CitaRepository citaRepository,
                          Clock reloj) {
        this.bloqueoRepository = bloqueoRepository;
        this.doctorRepository = doctorRepository;
        this.citaRepository = citaRepository;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<BloqueoAgenda> listarVigentes(UUID usuarioId) {
        Doctor doctor = buscarDoctor(usuarioId);
        return bloqueoRepository.findByDoctorIdAndFechaFinAfterOrderByFechaInicioAsc(doctor.getId(), LocalDateTime.now(reloj));
    }

    @Transactional
    public BloqueoAgenda crear(UUID usuarioId, BloqueoRequest request) {
        Doctor doctor = buscarDoctor(usuarioId);
        validarRango(request);
        validarSinCitasActivas(doctor, request);

        BloqueoAgenda bloqueo = new BloqueoAgenda();
        bloqueo.setDoctor(doctor);
        aplicar(bloqueo, request);
        return bloqueoRepository.save(bloqueo);
    }

    @Transactional
    public BloqueoAgenda actualizar(UUID usuarioId, UUID bloqueoId, BloqueoRequest request) {
        Doctor doctor = buscarDoctor(usuarioId);
        BloqueoAgenda bloqueo = buscarBloqueoDelDoctor(bloqueoId, doctor);
        validarRango(request);
        validarSinCitasActivas(doctor, request);

        aplicar(bloqueo, request);
        return bloqueoRepository.save(bloqueo);
    }

    @Transactional
    public void eliminar(UUID usuarioId, UUID bloqueoId) {
        Doctor doctor = buscarDoctor(usuarioId);
        bloqueoRepository.delete(buscarBloqueoDelDoctor(bloqueoId, doctor));
    }

    private Doctor buscarDoctor(UUID usuarioId) {
        return doctorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));
    }

    private BloqueoAgenda buscarBloqueoDelDoctor(UUID bloqueoId, Doctor doctor) {
        BloqueoAgenda bloqueo = bloqueoRepository.findById(bloqueoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Bloqueo no encontrado"));

        if (!bloqueo.getDoctor().getId().equals(doctor.getId())) {
            throw new AccesoDenegadoException("Este bloqueo no es tuyo");
        }
        return bloqueo;
    }

    private void validarRango(BloqueoRequest request) {
        if (!request.fechaInicio().isBefore(request.fechaFin())) {
            throw new FechaInvalidaException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
        if (!request.fechaFin().isAfter(LocalDateTime.now(reloj))) {
            throw new FechaInvalidaException("El bloqueo debe terminar en el futuro");
        }
    }

    private void validarSinCitasActivas(Doctor doctor, BloqueoRequest request) {
        Duration duracion = Duration.ofMinutes(doctor.getDuracionConsultaMin());

        long citasEnElRango = citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNot(
                        doctor.getId(), request.fechaInicio().minus(duracion), request.fechaFin(), EstadoCita.CANCELADA)
                .stream()
                .filter(cita -> cita.getEstado() == EstadoCita.PENDIENTE || cita.getEstado() == EstadoCita.CONFIRMADA)
                .filter(cita -> cita.getFechaHora().isBefore(request.fechaFin())
                        && request.fechaInicio().isBefore(cita.getFechaHora().plus(duracion)))
                .count();

        if (citasEnElRango > 0) {
            throw new HorarioSolapadoException("Hay " + citasEnElRango
                    + " cita(s) activa(s) en ese rango. Cancélalas antes de bloquear la agenda");
        }
    }

    private void aplicar(BloqueoAgenda bloqueo, BloqueoRequest request) {
        bloqueo.setFechaInicio(request.fechaInicio());
        bloqueo.setFechaFin(request.fechaFin());
        bloqueo.setMotivo(request.motivo());
    }
}
