package com.citas.medicas.almacenamiento;

import org.springframework.core.io.Resource;

public record ArchivoDescarga(
        Resource contenido,
        String nombre,
        String tipoContenido
) {
}
