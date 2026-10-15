package com.citas.medicas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EspecialidadRequest(
        @NotBlank
        @Size(max = 255)
        String nombre,

        @NotBlank
        @Size(max = 500)
        String descripcion
) {
}
