package com.citas.medicas.controller;

import com.citas.medicas.exception.OrdenamientoInvalidoException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

final class ValidadorOrdenamiento {

    private ValidadorOrdenamiento() {
    }

    static void validar(Pageable pageable, List<String> camposPermitidos) {
        for (Sort.Order orden : pageable.getSort()) {
            if (!camposPermitidos.contains(orden.getProperty())) {
                throw new OrdenamientoInvalidoException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", camposPermitidos));
            }
        }
    }
}
