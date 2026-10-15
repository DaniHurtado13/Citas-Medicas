package com.citas.medicas.service;

import com.citas.medicas.dto.request.LoginRequest;
import com.citas.medicas.dto.request.RegistroPacienteRequest;
import com.citas.medicas.dto.response.LoginResponse;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.PropositoToken;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.evento.RecuperacionPasswordSolicitadaEvent;
import com.citas.medicas.evento.VerificacionEmailSolicitadaEvent;
import com.citas.medicas.exception.CredencialesInvalidasException;
import com.citas.medicas.exception.DemasiadosIntentosException;
import com.citas.medicas.exception.EmailNoVerificadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import com.citas.medicas.security.JwtService;
import com.citas.medicas.security.LimitadorDeIntentos;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    // Cuentas de los botones de demo: los mismos IDs en db/dev y en la demo de producción
    private static final Map<Rol, UUID> CUENTAS_DEMO = Map.of(
            Rol.DOCTOR, UUID.fromString("22222222-2222-2222-2222-222222222201"),
            Rol.PACIENTE, UUID.fromString("22222222-2222-2222-2222-222222222202"),
            Rol.ADMIN, UUID.fromString("22222222-2222-2222-2222-222222222203"));

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UsuarioService usuarioService;
    private final LimitadorDeIntentos limitadorFallosPorCuenta;
    private final TokenVerificacionService tokenVerificacionService;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(UsuarioRepository usuarioRepository,
                       PacienteRepository pacienteRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UsuarioService usuarioService,
                       @Qualifier("limitadorFallosPorCuenta") LimitadorDeIntentos limitadorFallosPorCuenta,
                       TokenVerificacionService tokenVerificacionService,
                       ApplicationEventPublisher eventPublisher) {
        this.usuarioRepository = usuarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.usuarioService = usuarioService;
        this.limitadorFallosPorCuenta = limitadorFallosPorCuenta;
        this.tokenVerificacionService = tokenVerificacionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        String email = Usuario.normalizarEmail(request.email());
        if (limitadorFallosPorCuenta.bloqueado(email)) {
            throw new DemasiadosIntentosException("Demasiados intentos fallidos para esta cuenta.",
                    limitadorFallosPorCuenta.tiempoRestante(email));
        }

        Usuario usuario = usuarioRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElse(null);
        if (usuario == null) {
            limitadorFallosPorCuenta.intentar(email);
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }
        limitadorFallosPorCuenta.reiniciar(email);

        if (!usuario.getActivo()) {
            throw new CredencialesInvalidasException("La cuenta está desactivada");
        }
        // Solo después de validar la contraseña: así no revela a un extraño qué cuentas existen
        if (!usuario.getEmailVerificado()) {
            throw new EmailNoVerificadoException(
                    "Debes confirmar tu correo antes de iniciar sesión. Revisa tu bandeja de entrada o pide un nuevo enlace.");
        }

        return emitirToken(usuario);
    }

    // Sin contraseña a propósito: solo sirve para estas cuentas, y solo si están marcadas como demo
    @Transactional(readOnly = true)
    public LoginResponse entrarComoDemo(Rol rol) {
        Usuario usuario = usuarioRepository.findById(CUENTAS_DEMO.get(rol))
                .filter(u -> u.getDemo() && u.getActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No hay una cuenta de demostración disponible para ese rol"));
        return emitirToken(usuario);
    }

    private LoginResponse emitirToken(Usuario usuario) {
        String token = jwtService.generarToken(usuario);
        return new LoginResponse(token, "Bearer", jwtService.getDuracionEnSegundos(), usuario.getRol());
    }

    @Transactional
    public Paciente registrarPaciente(RegistroPacienteRequest request) {
        Usuario usuario = usuarioService.crearUsuario(request.tipoDocumento(), request.numeroDocumento(),
                request.email(), request.password(), Rol.PACIENTE);

        Paciente paciente = new Paciente();
        paciente.setUsuario(usuario);
        paciente.setNombre(request.nombre());
        paciente.setTelefono(request.telefono());
        paciente.setFechaNacimiento(request.fechaNacimiento());

        Paciente guardado = pacienteRepository.save(paciente);
        solicitarVerificacion(usuario);
        return guardado;
    }

    @Transactional
    public void verificarEmail(String token) {
        Usuario usuario = tokenVerificacionService.consumir(token, PropositoToken.VERIFICAR_EMAIL);
        usuario.setEmailVerificado(true);
    }

    @Transactional
    public void reenviarVerificacion(String email) {
        usuarioRepository.findByEmail(Usuario.normalizarEmail(email))
                .filter(usuario -> !usuario.getEmailVerificado())
                .ifPresent(this::solicitarVerificacion);
    }

    @Transactional
    public void solicitarRecuperacion(String email) {
        usuarioRepository.findByEmail(Usuario.normalizarEmail(email))
                .filter(usuario -> usuario.getActivo() && !usuario.getDemo())
                .ifPresent(usuario -> {
                    String token = tokenVerificacionService.generar(usuario, PropositoToken.RECUPERAR_PASSWORD);
                    eventPublisher.publishEvent(
                            new RecuperacionPasswordSolicitadaEvent(usuario.getId(), usuario.getEmail(), token));
                });
    }

    @Transactional
    public void restablecerPassword(String token, String nuevaContrasena) {
        Usuario usuario = tokenVerificacionService.consumir(token, PropositoToken.RECUPERAR_PASSWORD);
        usuario.setPassword(passwordEncoder.encode(nuevaContrasena));
        usuario.invalidarSesiones();
        // Usar el enlace demuestra que controla el correo: confirma el email y libera la cuenta si estaba bloqueada
        usuario.setEmailVerificado(true);
        limitadorFallosPorCuenta.reiniciar(usuario.getEmail());
    }

    private void solicitarVerificacion(Usuario usuario) {
        String token = tokenVerificacionService.generar(usuario, PropositoToken.VERIFICAR_EMAIL);
        eventPublisher.publishEvent(new VerificacionEmailSolicitadaEvent(usuario.getId(), usuario.getEmail(), token));
    }
}
