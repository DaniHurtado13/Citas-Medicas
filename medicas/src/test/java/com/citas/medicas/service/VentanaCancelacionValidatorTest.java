package com.citas.medicas.service;

import com.citas.medicas.exception.CancelacionNoPermitidaException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VentanaCancelacionValidatorTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime CITA_A_LAS_14 = LocalDateTime.of(2026, 3, 2, 14, 0);
    private static final Instant LAS_11_59_EN_BOGOTA = Instant.parse("2026-03-02T16:59:00Z");
    private static final Instant LAS_12_01_EN_BOGOTA = Instant.parse("2026-03-02T17:01:00Z");

    private VentanaCancelacionValidator validadorA(Instant instante, ZoneId zona) {
        return new VentanaCancelacionValidator(2, Clock.fixed(instante, zona));
    }

    @Test
    void cancelarConSuficienteAnticipacionNoLanzaExcepcion() {
        VentanaCancelacionValidator validator = validadorA(LAS_11_59_EN_BOGOTA, BOGOTA);

        assertDoesNotThrow(() -> validator.validar(CITA_A_LAS_14));
    }

    @Test
    void cancelarDentroDeLaVentanaLanzaExcepcion() {
        VentanaCancelacionValidator validator = validadorA(LAS_12_01_EN_BOGOTA, BOGOTA);

        assertThrows(CancelacionNoPermitidaException.class, () -> validator.validar(CITA_A_LAS_14));
    }

    @Test
    void conElRelojEnUtcElMismoInstanteYaQuedaFueraDeLaVentana() {
        VentanaCancelacionValidator validator = validadorA(LAS_11_59_EN_BOGOTA, ZoneOffset.UTC);

        assertThrows(CancelacionNoPermitidaException.class, () -> validator.validar(CITA_A_LAS_14));
    }
}
