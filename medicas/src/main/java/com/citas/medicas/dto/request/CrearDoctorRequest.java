package com.citas.medicas.dto.request;

import com.citas.medicas.entity.TipoDocumento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public record CrearDoctorRequest(
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
        String passwordProvisional,

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
        String consultorio,

        @Schema(description = "Título o certificado profesional: PDF, JPG o PNG, máximo 5 MB")
        @NotNull
        MultipartFile certificado
) {
}
