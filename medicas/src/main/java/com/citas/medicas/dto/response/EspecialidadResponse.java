package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Especialidad;

import java.util.UUID;

public record EspecialidadResponse(
        UUID id,
        String nombre,
        String descripcion
) {

    public static EspecialidadResponse desde(Especialidad especialidad) {
        return new EspecialidadResponse(
                especialidad.getId(),
                especialidad.getNombre(),
                especialidad.getDescripcion()
        );
    }
}
