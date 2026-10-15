package com.citas.medicas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ActualizarDoctorRequest(
        @NotBlank
        @Size(max = 255)
        String nombre,

        @NotNull
        UUID especialidadId,

        @NotNull
        @Positive
        Integer duracionConsultaMin,

        @NotBlank
        @Size(max = 255)
        String consultorio
) {
}
