package com.citas.medicas.service;

import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.TransicionInvalidaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransicionEstadoCitaTest {

    @Test
    void transicionValidaNoLanzaExcepcion() {
        assertDoesNotThrow(() ->
                TransicionEstadoCita.validar(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA)
        );
    }

    @Test
    void citaPendienteSePuedeCancelar() {
        assertDoesNotThrow(() ->
                TransicionEstadoCita.validar(EstadoCita.PENDIENTE, EstadoCita.CANCELADA)
        );
    }

    @Test
    void transicionInvalidaLanzaExcepcion() {
        assertThrows(TransicionInvalidaException.class, () ->
                TransicionEstadoCita.validar(EstadoCita.COMPLETADA, EstadoCita.PENDIENTE)
        );
    }
}
