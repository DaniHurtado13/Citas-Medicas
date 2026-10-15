package com.citas.medicas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OlvidePasswordRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}
