package com.citas.medicas.evento;

import java.util.UUID;

public record VerificacionEmailSolicitadaEvent(
        UUID usuarioId,
        String email,
        String token
) {
}
