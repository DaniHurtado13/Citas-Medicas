package com.citas.medicas.dto.request;

import com.citas.medicas.entity.TipoDocumento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegistroPacienteRequest(
        @NotBlank
        @Size(max = 255)
        String nombre,

        @NotNull
        TipoDocumento tipoDocumento,

        @NotBlank
        @Size(max = 255)
        String numeroDocumento,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(max = 10)
        String telefono,

        @NotNull
        @Past
        LocalDate fechaNacimiento
) {
}
