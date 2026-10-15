package com.citas.medicas.dto.response;

import com.citas.medicas.entity.BloqueoAgenda;

import java.time.LocalDateTime;
import java.util.UUID;

public record BloqueoResponse(
        UUID id,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        String motivo
) {

    public static BloqueoResponse desde(BloqueoAgenda bloqueo) {
        return new BloqueoResponse(
                bloqueo.getId(),
                bloqueo.getFechaInicio(),
                bloqueo.getFechaFin(),
                bloqueo.getMotivo()
        );
    }
}
