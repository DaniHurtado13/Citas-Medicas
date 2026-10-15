package com.citas.medicas.service;

import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.exception.TransicionInvalidaException;

import java.util.Map;
import java.util.Set;

public class TransicionEstadoCita {

    private static final Map<EstadoCita, Set<EstadoCita>> TRANSICIONES_VALIDAS = Map.of(
            EstadoCita.PENDIENTE, Set.of(EstadoCita.CONFIRMADA, EstadoCita.CANCELADA),
            EstadoCita.CONFIRMADA, Set.of(EstadoCita.COMPLETADA, EstadoCita.CANCELADA, EstadoCita.NO_ASISTIO),
            EstadoCita.COMPLETADA, Set.of(),
            EstadoCita.CANCELADA, Set.of(),
            EstadoCita.NO_ASISTIO, Set.of()
    );

    public static void validar(EstadoCita actual, EstadoCita nuevo) {
        Set<EstadoCita> estadosPermitidos = TRANSICIONES_VALIDAS.get(actual);

        if(!estadosPermitidos.contains(nuevo)) {
            throw new TransicionInvalidaException("No se puede pasar de " + actual + " a " + nuevo);
        }
    }
}
