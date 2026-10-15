package com.citas.medicas.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReprogramarCitaRequest(
        @Future
        @NotNull
        LocalDateTime nuevaFechaHora
) {
}
