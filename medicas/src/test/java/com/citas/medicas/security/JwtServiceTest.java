package com.citas.medicas.security;

import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String SECRETO = "LVdvL4P88oKw5C0I6pvepYh0yInLFEefqvgSNTtlAbk=";
    private static final String OTRO_SECRETO = "b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=";

    private final JwtService jwtService = new JwtService(SECRETO, 60);

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setRol(Rol.PACIENTE);
        return usuario;
    }

    @Test
    void tokenGeneradoContieneIdYRolDelUsuario() {
        Usuario usuario = usuario();

        Claims claims = jwtService.validarYLeer(jwtService.generarToken(usuario));

        assertEquals(usuario.getId().toString(), claims.getSubject());
        assertEquals("PACIENTE", claims.get("rol", String.class));
    }

    @Test
    void tokenLlevaLaVersionDeSesionDelUsuario() {
        Usuario usuario = usuario();
        usuario.setVersionSesion(3);

        Claims claims = jwtService.validarYLeer(jwtService.generarToken(usuario));

        assertEquals(3, claims.get("ver", Integer.class));
    }

    @Test
    void tokenModificadoEsRechazado() {
        String token = jwtService.generarToken(usuario());
        String[] partes = token.split("\\.");
        String contenidoFalso = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"otro\",\"rol\":\"ADMIN\"}".getBytes());
        String tokenAlterado = partes[0] + "." + contenidoFalso + "." + partes[2];

        assertThrows(JwtException.class, () -> jwtService.validarYLeer(tokenAlterado));
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() {
        JwtService otroServidor = new JwtService(OTRO_SECRETO, 60);
        String tokenAjeno = otroServidor.generarToken(usuario());

        assertThrows(JwtException.class, () -> jwtService.validarYLeer(tokenAjeno));
    }

    @Test
    void tokenVencidoEsRechazado() {
        JwtService conTokensYaVencidos = new JwtService(SECRETO, -1);
        String tokenVencido = conTokensYaVencidos.generarToken(usuario());

        assertThrows(ExpiredJwtException.class, () -> jwtService.validarYLeer(tokenVencido));
    }
}
