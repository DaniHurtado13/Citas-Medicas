package com.citas.medicas.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record BloqueoRequest(
        @NotNull
        LocalDateTime fechaInicio,

        @NotNull
        LocalDateTime fechaFin,

        @Size(max = 255)
        String motivo
) {
}
