package com.citas.medicas.service;

import com.citas.medicas.entity.PropositoToken;
import com.citas.medicas.entity.TokenVerificacion;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.TokenInvalidoException;
import com.citas.medicas.repository.TokenVerificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenVerificacionServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 3, 2, 10, 0);

    @Mock
    private TokenVerificacionRepository tokenRepository;

    @Captor
    private ArgumentCaptor<TokenVerificacion> guardado;

    private TokenVerificacionService service;

    private final Usuario usuario = new Usuario();

    @BeforeEach
    void setUp() {
        service = new TokenVerificacionService(tokenRepository, RELOJ, Duration.ofHours(24), Duration.ofMinutes(30));
        usuario.setId(UUID.randomUUID());
    }

    @Test
    void guardaSoloElHashDelTokenYVenceEn24Horas() {
        String token = service.generar(usuario, PropositoToken.VERIFICAR_EMAIL);

        verify(tokenRepository).save(guardado.capture());
        TokenVerificacion registro = guardado.getValue();
        assertNotEquals(token, registro.getHash());
        assertEquals(TokenVerificacionService.hash(token), registro.getHash());
        assertEquals(AHORA.plusHours(24), registro.getExpiraEn());
        assertSame(usuario, registro.getUsuario());
    }

    @Test
    void elTokenParaRecuperarLaContrasenaVenceEn30Minutos() {
        service.generar(usuario, PropositoToken.RECUPERAR_PASSWORD);

        verify(tokenRepository).save(guardado.capture());
        assertEquals(PropositoToken.RECUPERAR_PASSWORD, guardado.getValue().getProposito());
        assertEquals(AHORA.plusMinutes(30), guardado.getValue().getExpiraEn());
    }

    @Test
    void generarUnTokenNuevoInvalidaLosAnterioresDelUsuario() {
        service.generar(usuario, PropositoToken.VERIFICAR_EMAIL);

        verify(tokenRepository).borrarDelUsuario(usuario.getId(), PropositoToken.VERIFICAR_EMAIL);
    }

    @Test
    void cadaTokenEsDistintoYSeguroParaUnaUrl() {
        String primero = service.generar(usuario, PropositoToken.VERIFICAR_EMAIL);
        String segundo = service.generar(usuario, PropositoToken.VERIFICAR_EMAIL);

        assertNotEquals(primero, segundo);
        assertTrue(primero.matches("[A-Za-z0-9_-]{43}"), "32 bytes en Base64 URL sin relleno: " + primero);
    }

    private TokenVerificacion registroQueVence(LocalDateTime expiraEn) {
        TokenVerificacion registro = new TokenVerificacion();
        registro.setId(UUID.randomUUID());
        registro.setUsuario(usuario);
        registro.setExpiraEn(expiraEn);
        when(tokenRepository.buscarConUsuario(TokenVerificacionService.hash("token"), PropositoToken.VERIFICAR_EMAIL))
                .thenReturn(Optional.of(registro));
        return registro;
    }

    @Test
    void consumirUnTokenVigenteDevuelveElUsuarioYLoBorra() {
        TokenVerificacion registro = registroQueVence(AHORA.plusMinutes(1));
        when(tokenRepository.borrarPorId(registro.getId())).thenReturn(1);

        assertSame(usuario, service.consumir("token", PropositoToken.VERIFICAR_EMAIL));
        verify(tokenRepository).borrarPorId(registro.getId());
    }

    @Test
    void consumirUnTokenVencidoFalla() {
        registroQueVence(AHORA.minusMinutes(1));

        assertThrows(TokenInvalidoException.class, () -> service.consumir("token", PropositoToken.VERIFICAR_EMAIL));
        verify(tokenRepository, never()).borrarPorId(any());
    }

    @Test
    void consumirUnTokenInexistenteFalla() {
        when(tokenRepository.buscarConUsuario(any(), any())).thenReturn(Optional.empty());

        assertThrows(TokenInvalidoException.class, () -> service.consumir("inventado", PropositoToken.VERIFICAR_EMAIL));
    }

    @Test
    void siOtraPeticionLoUsoPrimeroFalla() {
        TokenVerificacion registro = registroQueVence(AHORA.plusHours(1));
        when(tokenRepository.borrarPorId(registro.getId())).thenReturn(0);

        assertThrows(TokenInvalidoException.class, () -> service.consumir("token", PropositoToken.VERIFICAR_EMAIL));
    }
}
