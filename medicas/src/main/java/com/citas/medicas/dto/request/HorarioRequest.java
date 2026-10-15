package com.citas.medicas.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HorarioRequest(
        @NotNull
        DayOfWeek diaSemana,

        @NotNull
        LocalTime horaInicio,

        @NotNull
        LocalTime horaFin
) {
}
