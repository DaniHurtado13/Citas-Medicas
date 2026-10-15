package com.citas.medicas.service;

import com.citas.medicas.dto.request.LoginRequest;
import com.citas.medicas.dto.request.RegistroPacienteRequest;
import com.citas.medicas.dto.response.LoginResponse;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.PropositoToken;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.evento.RecuperacionPasswordSolicitadaEvent;
import com.citas.medicas.evento.VerificacionEmailSolicitadaEvent;
import com.citas.medicas.exception.CredencialesInvalidasException;
import com.citas.medicas.exception.DemasiadosIntentosException;
import com.citas.medicas.exception.EmailNoVerificadoException;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.exception.TokenInvalidoException;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import com.citas.medicas.security.JwtService;
import com.citas.medicas.security.LimitadorDeIntentos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TokenVerificacionService tokenVerificacionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private static final int MAX_FALLOS = 3;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Un limitador real (no un mock): es Java puro y así se prueba el bloqueo de verdad
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(MAX_FALLOS, Duration.ofMinutes(15), Clock.systemUTC());
        authService = new AuthService(usuarioRepository, pacienteRepository, passwordEncoder, jwtService,
                usuarioService, limitador, tokenVerificacionService, eventPublisher);
    }

    private final RegistroPacienteRequest request = new RegistroPacienteRequest(
            "Ana Torres", TipoDocumento.CC, "1000000099", "ana@correo.com", "clave12345",
            "3001112233", LocalDate.of(1995, 3, 20));

    @Test
    void registroCreaUnUsuarioPacienteYSuPaciente() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioService.crearUsuario(TipoDocumento.CC, "1000000099", "ana@correo.com", "clave12345", Rol.PACIENTE))
                .thenReturn(usuario);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Paciente paciente = authService.registrarPaciente(request);

        assertSame(usuario, paciente.getUsuario());
        assertEquals("Ana Torres", paciente.getNombre());
        assertEquals("3001112233", paciente.getTelefono());
    }

    @Test
    void registroGeneraUnTokenYPideEnviarElCorreoDeVerificacion() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any())).thenReturn(usuario);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(tokenVerificacionService.generar(usuario, PropositoToken.VERIFICAR_EMAIL)).thenReturn("token-en-claro");

        authService.registrarPaciente(request);

        verify(eventPublisher).publishEvent(new VerificacionEmailSolicitadaEvent(
                usuario.getId(), "ana@correo.com", "token-en-claro"));
    }

    @Test
    void registroConCuentaRepetidaNoCreaElPaciente() {
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any()))
                .thenThrow(new RecursoDuplicadoException("Ya existe una cuenta con ese email"));

        assertThrows(RecursoDuplicadoException.class, () -> authService.registrarPaciente(request));
        verify(pacienteRepository, never()).save(any());
        verifyNoInteractions(tokenVerificacionService, eventPublisher);
    }

    private final LoginRequest login = new LoginRequest("ana@correo.com", "clave12345");

    private Usuario usuarioGuardado(boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setEmail("ana@correo.com");
        usuario.setPassword("hash-bcrypt");
        usuario.setRol(Rol.PACIENTE);
        usuario.setActivo(activo);
        usuario.setEmailVerificado(true);
        return usuario;
    }

    private Usuario sinConfirmar() {
        Usuario usuario = usuarioGuardado(true);
        usuario.setEmailVerificado(false);
        return usuario;
    }

    @Test
    void loginCorrectoDevuelveToken() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-firmado");
        when(jwtService.getDuracionEnSegundos()).thenReturn(3600L);

        LoginResponse respuesta = authService.login(login);

        assertEquals("token-firmado", respuesta.token());
        assertEquals("Bearer", respuesta.tipo());
        assertEquals(Rol.PACIENTE, respuesta.rol());
    }

    private static final UUID PACIENTE_DEMO = UUID.fromString("22222222-2222-2222-2222-222222222202");

    private Usuario pacienteDemo(boolean demo, boolean activo) {
        Usuario usuario = usuarioGuardado(activo);
        usuario.setId(PACIENTE_DEMO);
        usuario.setDemo(demo);
        return usuario;
    }

    @Test
    void entrarComoDemoDevuelveElTokenDeLaCuentaDemoSinPedirContrasena() {
        Usuario demo = pacienteDemo(true, true);
        when(usuarioRepository.findById(PACIENTE_DEMO)).thenReturn(Optional.of(demo));
        when(jwtService.generarToken(demo)).thenReturn("token-demo");

        LoginResponse respuesta = authService.entrarComoDemo(Rol.PACIENTE);

        assertEquals("token-demo", respuesta.token());
        assertEquals(Rol.PACIENTE, respuesta.rol());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void entrarComoDemoNoSirveSiLaCuentaNoEsDemoEstaDesactivadaONoExiste() {
        when(usuarioRepository.findById(PACIENTE_DEMO))
                .thenReturn(Optional.of(pacienteDemo(false, true)))
                .thenReturn(Optional.of(pacienteDemo(true, false)))
                .thenReturn(Optional.empty());

        for (int i = 0; i < 3; i++) {
            assertThrows(RecursoNoEncontradoException.class, () -> authService.entrarComoDemo(Rol.PACIENTE));
        }
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void olvidePasswordNoEnviaNadaAUnaCuentaDeDemo() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(pacienteDemo(true, true)));

        authService.solicitarRecuperacion("ana@correo.com");

        verifyNoInteractions(tokenVerificacionService, eventPublisher);
    }

    @Test
    void loginEncuentraLaCuentaAunqueElEmailVengaConMayusculasYEspacios() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-firmado");

        LoginResponse respuesta = authService.login(new LoginRequest("  ANA@Correo.COM ", "clave12345"));

        assertEquals("token-firmado", respuesta.token());
    }

    @Test
    void reenviarYOlvidePasswordBuscanElEmailNormalizado() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.empty());

        authService.reenviarVerificacion("Ana@Correo.com");
        authService.solicitarRecuperacion(" ANA@CORREO.COM");

        verify(usuarioRepository, times(2)).findByEmail("ana@correo.com");
    }

    @Test
    void loginConPasswordIncorrectaLanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuarioGuardado(true)));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class, () -> authService.login(login));
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void loginConEmailInexistenteLanzaLaMismaExcepcion() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.empty());

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(login));
        assertEquals("Email o contraseña incorrectos", ex.getMessage());
    }

    @Test
    void loginConCuentaDesactivadaLanzaExcepcion() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuarioGuardado(false)));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);

        assertThrows(CredencialesInvalidasException.class, () -> authService.login(login));
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void loginConEmailSinConfirmarLanzaEmailNoVerificado() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(sinConfirmar()));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);

        assertThrows(EmailNoVerificadoException.class, () -> authService.login(login));
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void sinConfirmarYConPasswordIncorrectaNoRevelaQueLaCuentaExiste() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(sinConfirmar()));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);

        CredencialesInvalidasException ex = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(login));
        assertEquals("Email o contraseña incorrectos", ex.getMessage());
    }

    // ---------- Verificación de email ----------

    @Test
    void verificarEmailConUnTokenValidoConfirmaLaCuenta() {
        Usuario usuario = sinConfirmar();
        when(tokenVerificacionService.consumir("token-en-claro", PropositoToken.VERIFICAR_EMAIL)).thenReturn(usuario);

        authService.verificarEmail("token-en-claro");

        assertTrue(usuario.getEmailVerificado());
    }

    @Test
    void verificarEmailConUnTokenInvalidoPropagaElError() {
        when(tokenVerificacionService.consumir("falso", PropositoToken.VERIFICAR_EMAIL))
                .thenThrow(new TokenInvalidoException("El enlace no es válido o ya venció. Pide uno nuevo."));

        assertThrows(TokenInvalidoException.class, () -> authService.verificarEmail("falso"));
    }

    @Test
    void reenviarAUnPacienteSinConfirmarGeneraUnTokenNuevoYPideElCorreo() {
        Usuario usuario = sinConfirmar();
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(tokenVerificacionService.generar(usuario, PropositoToken.VERIFICAR_EMAIL)).thenReturn("token-nuevo");

        authService.reenviarVerificacion("ana@correo.com");

        verify(eventPublisher).publishEvent(new VerificacionEmailSolicitadaEvent(
                usuario.getId(), "ana@correo.com", "token-nuevo"));
    }

    @Test
    void reenviarAUnaCuentaYaConfirmadaNoHaceNada() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));

        authService.reenviarVerificacion("ana@correo.com");

        assertTrue(usuario.getEmailVerificado());
        verifyNoInteractions(tokenVerificacionService, eventPublisher);
    }

    @Test
    void reenviarAUnEmailInexistenteRespondeIgualSinHacerNada() {
        when(usuarioRepository.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.reenviarVerificacion("nadie@correo.com"));
        verifyNoInteractions(tokenVerificacionService, eventPublisher);
    }

    // ---------- Recuperar contraseña ----------

    @Test
    void olvidePasswordDeUnaCuentaActivaGeneraUnTokenYPideElCorreo() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(tokenVerificacionService.generar(usuario, PropositoToken.RECUPERAR_PASSWORD)).thenReturn("token-recuperacion");

        authService.solicitarRecuperacion("ana@correo.com");

        verify(eventPublisher).publishEvent(
                new RecuperacionPasswordSolicitadaEvent(usuario.getId(), "ana@correo.com", "token-recuperacion"));
    }

    @Test
    void olvidePasswordDeUnEmailInexistenteODeUnaCuentaDesactivadaNoHaceNada() {
        when(usuarioRepository.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuarioGuardado(false)));

        authService.solicitarRecuperacion("nadie@correo.com");
        authService.solicitarRecuperacion("ana@correo.com");

        verifyNoInteractions(tokenVerificacionService, eventPublisher);
    }

    @Test
    void restablecerCambiaLaContrasenaCierraLasSesionesYConfirmaElEmail() {
        Usuario usuario = sinConfirmar();
        when(tokenVerificacionService.consumir("token-recuperacion", PropositoToken.RECUPERAR_PASSWORD)).thenReturn(usuario);
        when(passwordEncoder.encode("NuevaClave789")).thenReturn("hash-nuevo");

        authService.restablecerPassword("token-recuperacion", "NuevaClave789");

        assertEquals("hash-nuevo", usuario.getPassword());
        assertEquals(1, usuario.getVersionSesion());
        assertTrue(usuario.getEmailVerificado());
    }

    @Test
    void restablecerConUnTokenInvalidoNoCambiaNada() {
        when(tokenVerificacionService.consumir("falso", PropositoToken.RECUPERAR_PASSWORD))
                .thenThrow(new TokenInvalidoException("El enlace no es válido o ya venció. Pide uno nuevo."));

        assertThrows(TokenInvalidoException.class, () -> authService.restablecerPassword("falso", "NuevaClave789"));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void restablecerLiberaUnaCuentaBloqueadaPorIntentosFallidos() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);
        fallarLogin(MAX_FALLOS);
        assertThrows(DemasiadosIntentosException.class, () -> authService.login(login));

        when(tokenVerificacionService.consumir("token-recuperacion", PropositoToken.RECUPERAR_PASSWORD)).thenReturn(usuario);
        when(passwordEncoder.encode("clave12345")).thenReturn("hash-bcrypt");
        authService.restablecerPassword("token-recuperacion", "clave12345");

        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-firmado");
        assertEquals("token-firmado", authService.login(login).token());
    }

    // ---------- Límite de fallos por cuenta ----------

    private void fallarLogin(int veces) {
        for (int i = 0; i < veces; i++) {
            assertThrows(CredencialesInvalidasException.class, () -> authService.login(login));
        }
    }

    @Test
    void trasDemasiadosFallosLaCuentaSeBloqueaAunqueLaClaveSeaCorrecta() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);
        fallarLogin(MAX_FALLOS);

        // Ahora el atacante "acierta": igual debe esperar. Es lenient porque el bloqueo corta antes de llegar a BCrypt
        lenient().when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);
        DemasiadosIntentosException ex = assertThrows(DemasiadosIntentosException.class, () -> authService.login(login));

        assertTrue(ex.getSegundosDeEspera() > 0);
        verify(jwtService, never()).generarToken(any());
    }

    @Test
    void unaCuentaBloqueadaNoConsultaNiLaBaseNiBCrypt() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.empty());
        fallarLogin(MAX_FALLOS);
        clearInvocations(usuarioRepository, passwordEncoder);

        assertThrows(DemasiadosIntentosException.class, () -> authService.login(login));

        verifyNoInteractions(usuarioRepository, passwordEncoder);
    }

    @Test
    void unEmailInexistenteTambienSeBloqueaParaNoDelatarQueCuentasExisten() {
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.empty());
        fallarLogin(MAX_FALLOS);

        assertThrows(DemasiadosIntentosException.class, () -> authService.login(login));
    }

    @Test
    void cambiarMayusculasEnElEmailNoSaltaElLimite() {
        when(usuarioRepository.findByEmail(any())).thenReturn(Optional.empty());
        fallarLogin(MAX_FALLOS);

        LoginRequest conMayusculas = new LoginRequest("  ANA@Correo.COM ", "clave12345");
        assertThrows(DemasiadosIntentosException.class, () -> authService.login(conMayusculas));
    }

    @Test
    void unLoginCorrectoReiniciaElContadorDeFallos() {
        Usuario usuario = usuarioGuardado(true);
        when(usuarioRepository.findByEmail("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);
        fallarLogin(MAX_FALLOS - 1);

        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(true);
        when(jwtService.generarToken(usuario)).thenReturn("token-firmado");
        authService.login(login);

        // Con el contador en cero, vuelve a tener todos sus intentos antes de bloquearse
        when(passwordEncoder.matches("clave12345", "hash-bcrypt")).thenReturn(false);
        fallarLogin(MAX_FALLOS);
    }
}
