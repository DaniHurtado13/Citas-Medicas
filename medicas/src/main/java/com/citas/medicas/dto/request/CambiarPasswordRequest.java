package com.citas.medicas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarPasswordRequest(

        @NotBlank
        String contrasenaActual,

        @NotBlank
        @Size(min = 8, max = 72)
        String nuevaContrasena
) {
}
