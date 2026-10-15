package com.citas.medicas.demo;

import com.citas.medicas.TestcontainersConfiguration;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Con la demo habilitada, la clínica de ejemplo se crea al arrancar y el reinicio borra todo lo que hicieron
 * los visitantes, salvo los administradores reales. Usa su propio contexto (y su propio PostgreSQL), así que
 * no toca los datos de los demás tests de integración.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jwt.secret=b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=",
        "citas.demo.habilitada=true",
        "citas.certificados.directorio=target/certificados-demo-test"})
class ReinicioDemoTest {

    private static final String DOCTORA_DEMO = "22222222-2222-2222-2222-222222222201";

    @Autowired
    ReinicioDemo reinicioDemo;

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    JdbcTemplate jdbc;

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void comprobarClinicaDeDemo() {
        assertEquals(4, contar("SELECT count(*) FROM especialidades"));
        assertEquals(4, contar("SELECT count(*) FROM doctores"));
        assertEquals(7, contar("SELECT count(*) FROM pacientes"));
        assertEquals(15, contar("SELECT count(*) FROM citas"));
        assertEquals(1, contar("SELECT count(*) FROM bloqueos_agenda"));
        assertEquals(12, contar("SELECT count(*) FROM usuarios WHERE demo"));
        assertEquals(6, contar("SELECT count(*) FROM citas WHERE fecha_hora > now() AT TIME ZONE 'America/Bogota'"));
        assertEquals(0, contar("SELECT count(*) FROM citas WHERE extract(ISODOW FROM fecha_hora) > 5"));
    }

    @Test
    void alArrancarCreaLaClinicaDeDemo() {
        comprobarClinicaDeDemo();
    }

    @Test
    void reiniciarBorraLoDeLosVisitantesYConservaAlAdminReal() throws Exception {
        usuarioService.crearUsuario(TipoDocumento.CC, "5000000001", "admin@clinica.com", "clave12345", Rol.ADMIN);
        usuarioService.crearUsuario(TipoDocumento.CC, "5000000002", "visitante@correo.com", "clave12345", Rol.PACIENTE);
        jdbc.update("UPDATE usuarios SET activo = FALSE WHERE id = ?::uuid", DOCTORA_DEMO);
        jdbc.update("DELETE FROM bloqueos_agenda");
        Path certificado = Path.of("target/certificados-demo-test/subido-por-un-visitante.pdf");
        Files.createDirectories(certificado.getParent());
        Files.writeString(certificado, "%PDF");

        reinicioDemo.reiniciar();

        comprobarClinicaDeDemo();
        assertEquals(1, contar("SELECT count(*) FROM usuarios WHERE email = 'admin@clinica.com'"));
        assertEquals(0, contar("SELECT count(*) FROM usuarios WHERE email = 'visitante@correo.com'"));
        assertTrue(jdbc.queryForObject("SELECT activo FROM usuarios WHERE id = ?::uuid", Boolean.class, DOCTORA_DEMO));
        assertFalse(Files.exists(certificado));
    }
}
