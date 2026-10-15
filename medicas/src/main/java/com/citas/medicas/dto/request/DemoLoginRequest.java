package com.citas.medicas.dto.request;

import com.citas.medicas.entity.Rol;
import jakarta.validation.constraints.NotNull;

public record DemoLoginRequest(
        @NotNull
        Rol rol
) {
}
