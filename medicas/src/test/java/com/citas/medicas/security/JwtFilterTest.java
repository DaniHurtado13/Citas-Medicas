package com.citas.medicas.security;

import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.repository.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    private static final String SECRETO = "LVdvL4P88oKw5C0I6pvepYh0yInLFEefqvgSNTtlAbk=";

    @Mock
    private UsuarioRepository usuarioRepository;

    private final JwtService jwtService = new JwtService(SECRETO, 60);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setRol(Rol.PACIENTE);
        usuario.setActivo(true);
        return usuario;
    }

    private Authentication filtrarCon(String encabezado) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        if (encabezado != null) {
            peticion.addHeader("Authorization", encabezado);
        }
        new JwtFilter(jwtService, usuarioRepository).doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void tokenConLaVersionActualAutentica() throws Exception {
        Usuario usuario = usuario();
        String token = jwtService.generarToken(usuario);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        Authentication autenticacion = filtrarCon("Bearer " + token);

        assertNotNull(autenticacion);
        assertEquals(usuario.getId(), autenticacion.getPrincipal());
        assertEquals("ROLE_PACIENTE", autenticacion.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void trasInvalidarLasSesionesElTokenAnteriorNoAutentica() throws Exception {
        Usuario usuario = usuario();
        String tokenAnterior = jwtService.generarToken(usuario);
        usuario.invalidarSesiones();
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertNull(filtrarCon("Bearer " + tokenAnterior));
    }

    @Test
    void unaCuentaDesactivadaNoAutenticaAunqueElTokenSeaValido() throws Exception {
        Usuario usuario = usuario();
        String token = jwtService.generarToken(usuario);
        usuario.setActivo(false);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.of(usuario));

        assertNull(filtrarCon("Bearer " + token));
    }

    @Test
    void unUsuarioQueYaNoExisteNoAutentica() throws Exception {
        Usuario usuario = usuario();
        String token = jwtService.generarToken(usuario);
        when(usuarioRepository.findById(usuario.getId())).thenReturn(Optional.empty());

        assertNull(filtrarCon("Bearer " + token));
    }

    @Test
    void unTokenSinVersionNoAutenticaNiConsultaLaBase() throws Exception {
        String tokenAntiguo = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("rol", "PACIENTE")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRETO)))
                .compact();

        assertNull(filtrarCon("Bearer " + tokenAntiguo));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void sinTokenOConTokenFalsoNoSeConsultaLaBase() throws Exception {
        assertNull(filtrarCon(null));
        assertNull(filtrarCon("Bearer no-es-un-jwt"));
        verifyNoInteractions(usuarioRepository);
    }
}
