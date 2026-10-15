package com.citas.medicas.service;

import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.LimiteDeReservasException;
import com.citas.medicas.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Component
public class LimiteDeReservasValidator {

    private static final List<EstadoCita> ACTIVAS = List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA);

    private final CitaRepository citaRepository;
    private final int maxActivas;
    private final Clock reloj;

    public LimiteDeReservasValidator(CitaRepository citaRepository,
                                     @Value("${citas.reservas.max-activas-por-paciente}") int maxActivas,
                                     Clock reloj) {
        this.citaRepository = citaRepository;
        this.maxActivas = maxActivas;
        this.reloj = reloj;
    }

    public void validar(UUID pacienteId, UUID doctorId) {
        LocalDateTime ahora = LocalDateTime.now(reloj);

        if (citaRepository.existsByPacienteIdAndDoctorIdAndEstadoInAndFechaHoraAfter(pacienteId, doctorId, ACTIVAS, ahora)) {
            throw new LimiteDeReservasException(
                    "Ya tienes una cita activa con este doctor. Cancélala o espera a que pase para reservar otra.");
        }
        if (citaRepository.countByPacienteIdAndEstadoInAndFechaHoraAfter(pacienteId, ACTIVAS, ahora) >= maxActivas) {
            throw new LimiteDeReservasException("Ya tienes " + maxActivas
                    + " citas activas, el máximo permitido. Cancela alguna o espera a que pase para reservar otra.");
        }
    }
}
