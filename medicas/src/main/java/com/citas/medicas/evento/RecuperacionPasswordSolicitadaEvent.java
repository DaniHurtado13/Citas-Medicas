package com.citas.medicas.evento;

import java.util.UUID;

public record RecuperacionPasswordSolicitadaEvent(
        UUID usuarioId,
        String email,
        String token
) {
}
