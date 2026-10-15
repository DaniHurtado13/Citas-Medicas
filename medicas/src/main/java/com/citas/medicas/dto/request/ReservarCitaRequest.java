package com.citas.medicas.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservarCitaRequest(
        @NotNull
        UUID doctorId,

        @NotNull
        @Future
        LocalDateTime fechaHora,

        @NotBlank
        @Size(max = 255)
        String motivoConsulta
) {
}
