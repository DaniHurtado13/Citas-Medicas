package com.citas.medicas.dto.response;

import com.citas.medicas.entity.HorarioAtencion;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record HorarioResponse(
        UUID id,
        DayOfWeek diaSemana,
        LocalTime horaInicio,
        LocalTime horaFin
) {

    public static HorarioResponse desde(HorarioAtencion horario) {
        return new HorarioResponse(
                horario.getId(),
                horario.getDiaSemana(),
                horario.getHoraInicio(),
                horario.getHoraFin()
        );
    }
}
