package com.citas.medicas.service;

import com.citas.medicas.TestcontainersConfiguration;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los emails se guardan en minúsculas. Además de la normalización en el código, la base lo exige con un CHECK.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jwt.secret=b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=")
class EmailNormalizadoTest {

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    UsuarioRepository usuarioRepository;

    @AfterEach
    void limpiar() {
        for (String documento : List.of("3000000001", "3000000002", "3000000003")) {
            usuarioRepository.findByNumeroDocumento(documento).ifPresent(usuarioRepository::delete);
        }
    }

    @Test
    void elMismoEmailConOtrasMayusculasEsUnaCuentaRepetida() {
        usuarioService.crearUsuario(TipoDocumento.CC, "3000000001", "ana@correo.com", "clave12345", Rol.DOCTOR);

        assertThrows(RecursoDuplicadoException.class, () -> usuarioService.crearUsuario(
                TipoDocumento.CC, "3000000002", "  ANA@Correo.COM ", "clave12345", Rol.DOCTOR));
    }

    @Test
    void laBaseRechazaUnEmailConMayusculasAunqueElCodigoOlvideNormalizar() {
        Usuario usuario = new Usuario();
        usuario.setTipoDocumento(TipoDocumento.CC);
        usuario.setNumeroDocumento("3000000003");
        usuario.setEmail("Ana@Correo.com");
        usuario.setPassword("no-se-usa-en-este-test");
        usuario.setRol(Rol.DOCTOR);
        usuario.setActivo(true);
        usuario.setEmailVerificado(true);

        assertThrows(DataIntegrityViolationException.class, () -> usuarioRepository.saveAndFlush(usuario));
        assertTrue(usuarioRepository.findByNumeroDocumento("3000000003").isEmpty());
    }
}
