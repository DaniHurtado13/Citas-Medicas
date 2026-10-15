package com.citas.medicas.evento;

import java.util.UUID;

public record CitaCanceladaPorConsultorioEvent(
        UUID citaId
) {
}
