package com.citas.medicas.controller;

import com.citas.medicas.exception.OrdenamientoInvalidoException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidadorOrdenamientoTest {

    private final List<String> permitidos = List.of("fechaHora", "estado");

    @Test
    void ordenarPorUnCampoPermitidoNoLanzaExcepcion() {
        assertDoesNotThrow(() -> ValidadorOrdenamiento.validar(
                PageRequest.of(0, 10, Sort.by("fechaHora").descending()), permitidos));
    }

    @Test
    void sinOrdenNoLanzaExcepcion() {
        assertDoesNotThrow(() -> ValidadorOrdenamiento.validar(PageRequest.of(0, 10), permitidos));
    }

    @Test
    void ordenarPorUnCampoNoPermitidoLanzaExcepcion() {
        assertThrows(OrdenamientoInvalidoException.class, () -> ValidadorOrdenamiento.validar(
                PageRequest.of(0, 10, Sort.by("4")), permitidos));
    }
}
