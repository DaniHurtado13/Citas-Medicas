package com.citas.medicas.almacenamiento;

/**
 * Un archivo que ya pasó las validaciones y todavía no se escribió en disco.
 * Lleva el nombre con el que se guardará, para poder registrarlo en la base antes de escribirlo.
 */
public record ArchivoValidado(
        String nombreAlmacenado,
        String nombreOriginal,
        String tipoContenido,
        byte[] contenido
) {
}
