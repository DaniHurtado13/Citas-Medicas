package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Rol;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        Rol rol
) {
}
