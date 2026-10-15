package com.citas.medicas.almacenamiento;

import com.citas.medicas.exception.ArchivoInvalidoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlmacenamientoServiceTest {

    private static final byte[] PDF = "%PDF-1.7 contenido de prueba".getBytes();
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
    private static final byte[] EXE = {0x4D, 0x5A, (byte) 0x90, 0x00}; // "MZ": cabecera de un ejecutable de Windows

    @TempDir
    Path directorio;

    private AlmacenamientoService almacenamiento;

    @BeforeEach
    void setUp() {
        almacenamiento = new AlmacenamientoService(directorio.toString());
    }

    private MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("certificado", nombre, "application/octet-stream", contenido);
    }

    @Test
    void pdfValidoSeAceptaConNombreGeneradoYTipoDeContenido() {
        ArchivoValidado validado = almacenamiento.validar(archivo("titulo.pdf", PDF));

        assertTrue(validado.nombreAlmacenado().matches("[0-9a-f-]{36}\\.pdf"));
        assertEquals("titulo.pdf", validado.nombreOriginal());
        assertEquals("application/pdf", validado.tipoContenido());
    }

    @Test
    void pngYJpegSeAceptan() {
        assertEquals("image/png", almacenamiento.validar(archivo("titulo.PNG", PNG)).tipoContenido());
        assertEquals("image/jpeg", almacenamiento.validar(archivo("titulo.jpeg", JPG)).tipoContenido());
    }

    @Test
    void ejecutableRenombradoAPdfSeRechaza() {
        assertThrows(ArchivoInvalidoException.class, () -> almacenamiento.validar(archivo("titulo.pdf", EXE)));
    }

    @Test
    void extensionNoPermitidaSeRechaza() {
        assertThrows(ArchivoInvalidoException.class, () -> almacenamiento.validar(archivo("programa.exe", EXE)));
        assertThrows(ArchivoInvalidoException.class, () -> almacenamiento.validar(archivo("sin-extension", PDF)));
    }

    @Test
    void archivoVacioOAusenteSeRechaza() {
        assertThrows(ArchivoInvalidoException.class, () -> almacenamiento.validar(archivo("titulo.pdf", new byte[0])));
        assertThrows(ArchivoInvalidoException.class, () -> almacenamiento.validar(null));
    }

    @Test
    void validarNoEscribeNadaEnDisco() {
        ArchivoValidado validado = almacenamiento.validar(archivo("titulo.pdf", PDF));

        assertThrows(RecursoNoEncontradoException.class, () -> almacenamiento.cargar(validado.nombreAlmacenado()));
    }

    @Test
    void loEscritoSePuedeCargarConElMismoContenido() throws Exception {
        ArchivoValidado validado = almacenamiento.validar(archivo("titulo.pdf", PDF));

        almacenamiento.escribir(validado);
        Resource cargado = almacenamiento.cargar(validado.nombreAlmacenado());

        assertArrayEquals(PDF, cargado.getContentAsByteArray());
    }

    @Test
    void elTipoDeContenidoSeDeduceDelNombreGuardado() {
        assertEquals("application/pdf", almacenamiento.tipoContenido("abc.pdf"));
        assertEquals("image/jpeg", almacenamiento.tipoContenido("abc.jpg"));
        assertEquals("image/png", almacenamiento.tipoContenido("abc.png"));
    }

    @Test
    void noSePuedeCargarFueraDeLaCarpeta() {
        assertThrows(RecursoNoEncontradoException.class, () -> almacenamiento.cargar("../secreto.txt"));
    }
}
