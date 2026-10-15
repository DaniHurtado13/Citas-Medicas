package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Paciente;

import java.time.LocalDate;
import java.util.UUID;

public record PacienteResponse(
        UUID id,
        String nombre,
        String email,
        String telefono,
        LocalDate fechaNacimiento
) {

    public static PacienteResponse desde(Paciente paciente) {
        return new PacienteResponse(
                paciente.getId(),
                paciente.getNombre(),
                paciente.getUsuario().getEmail(),
                paciente.getTelefono(),
                paciente.getFechaNacimiento()
        );
    }
}
