package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.EstadoCita;

import java.time.LocalDateTime;
import java.util.UUID;

public record CitaResponse(
        UUID id,
        UUID pacienteId,
        String pacienteNombre,
        UUID doctorId,
        String doctorNombre,
        UUID citaOrigenId,
        LocalDateTime fechaHora,
        EstadoCita estado,
        String motivoConsulta
) {

    public static CitaResponse desde(Cita cita) {
        return new CitaResponse(
                cita.getId(),
                cita.getPaciente().getId(),
                cita.getPaciente().getNombre(),
                cita.getDoctor().getId(),
                cita.getDoctor().getNombre(),
                cita.getCitaOrigen() != null ? cita.getCitaOrigen().getId() : null,
                cita.getFechaHora(),
                cita.getEstado(),
                cita.getMotivoConsulta()
        );
    }
}
