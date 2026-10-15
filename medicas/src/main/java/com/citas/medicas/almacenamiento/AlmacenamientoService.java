package com.citas.medicas.almacenamiento;

import com.citas.medicas.exception.ArchivoInvalidoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class AlmacenamientoService {

    private static final int LARGO_MAXIMO_NOMBRE = 255;

    private final Path directorio;

    public AlmacenamientoService(@Value("${citas.certificados.directorio}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    public ArchivoValidado validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("El certificado es obligatorio");
        }

        String nombreOriginal = StringUtils.getFilename(archivo.getOriginalFilename());
        Formato formato = Formato.porExtension(StringUtils.getFilenameExtension(nombreOriginal));
        if (formato == null) {
            throw new ArchivoInvalidoException("El certificado debe ser un archivo PDF, JPG o PNG");
        }

        byte[] contenido = leer(archivo);
        if (!formato.coincideFirma(contenido)) {
            throw new ArchivoInvalidoException("El contenido del archivo no corresponde a un " + formato.name());
        }

        String nombreAlmacenado = UUID.randomUUID() + "." + formato.extensionPrincipal();
        return new ArchivoValidado(nombreAlmacenado, recortar(nombreOriginal), formato.tipoContenido, contenido);
    }

    public void escribir(ArchivoValidado archivo) {
        try {
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(archivo.nombreAlmacenado()), archivo.contenido(), StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo guardar el certificado", ex);
        }
    }

    /** Borra todos los certificados guardados. Lo usa el reinicio de la demo; devuelve cuántos borró */
    public int vaciar() {
        if (!Files.isDirectory(directorio)) {
            return 0;
        }
        try (Stream<Path> contenido = Files.list(directorio)) {
            List<Path> archivos = contenido.filter(Files::isRegularFile).toList();
            for (Path archivo : archivos) {
                Files.delete(archivo);
            }
            return archivos.size();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudieron borrar los certificados", ex);
        }
    }

    public Resource cargar(String nombreAlmacenado) {
        Path ruta = directorio.resolve(nombreAlmacenado).normalize();
        if (!ruta.startsWith(directorio) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("El certificado no se encontró");
        }
        return new FileSystemResource(ruta);
    }

    public String tipoContenido(String nombreAlmacenado) {
        Formato formato = Formato.porExtension(StringUtils.getFilenameExtension(nombreAlmacenado));
        return formato != null ? formato.tipoContenido : "application/octet-stream";
    }

    private byte[] leer(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el certificado", ex);
        }
    }

    private String recortar(String nombre) {
        return nombre.length() <= LARGO_MAXIMO_NOMBRE ? nombre : nombre.substring(0, LARGO_MAXIMO_NOMBRE);
    }

    /**
     * Formatos permitidos. La firma son los primeros bytes que todo archivo de ese tipo tiene,
     * sin importar cómo se llame: así un .exe renombrado a .pdf se rechaza.
     */
    private enum Formato {
        PDF("application/pdf", firma(0x25, 0x50, 0x44, 0x46), "pdf"),                         // %PDF
        PNG("image/png", firma(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "png"),
        JPG("image/jpeg", firma(0xFF, 0xD8, 0xFF), "jpg", "jpeg");

        private final String tipoContenido;
        private final byte[] firma;
        private final List<String> extensiones;

        Formato(String tipoContenido, byte[] firma, String... extensiones) {
            this.tipoContenido = tipoContenido;
            this.firma = firma;
            this.extensiones = List.of(extensiones);
        }

        static Formato porExtension(String extension) {
            if (extension == null) {
                return null;
            }
            String normalizada = extension.toLowerCase(Locale.ROOT);
            return Arrays.stream(values())
                    .filter(formato -> formato.extensiones.contains(normalizada))
                    .findFirst()
                    .orElse(null);
        }

        boolean coincideFirma(byte[] contenido) {
            return contenido.length >= firma.length
                    && Arrays.equals(contenido, 0, firma.length, firma, 0, firma.length);
        }

        String extensionPrincipal() {
            return extensiones.getFirst();
        }

        private static byte[] firma(int... valores) {
            byte[] bytes = new byte[valores.length];
            for (int i = 0; i < valores.length; i++) {
                bytes[i] = (byte) valores[i];
            }
            return bytes;
        }
    }
}
