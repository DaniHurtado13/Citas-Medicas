package com.citas.medicas.controller;

import com.citas.medicas.dto.request.CambiarPasswordRequest;
import com.citas.medicas.dto.request.DemoLoginRequest;
import com.citas.medicas.dto.request.LoginRequest;
import com.citas.medicas.dto.request.OlvidePasswordRequest;
import com.citas.medicas.dto.request.ReenviarVerificacionRequest;
import com.citas.medicas.dto.request.RegistroPacienteRequest;
import com.citas.medicas.dto.request.RestablecerPasswordRequest;
import com.citas.medicas.dto.request.VerificarEmailRequest;
import com.citas.medicas.dto.response.LoginResponse;
import com.citas.medicas.dto.response.PacienteResponse;
import com.citas.medicas.dto.response.UsuarioActualResponse;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.service.AuthService;
import com.citas.medicas.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Autenticación", description = "Registro de pacientes, inicio de sesión y cambio de contraseña")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    public AuthController(AuthService authService, UsuarioService usuarioService) {
        this.authService = authService;
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Iniciar sesión",
            description = "Devuelve un token JWT válido por 60 minutos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales correctas"),
            @ApiResponse(responseCode = "401", description = "Email o contraseña incorrectos, o cuenta desactivada"),
            @ApiResponse(responseCode = "403", description = "Contraseña correcta, pero el email aún no está confirmado"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP o para esta cuenta; "
                    + "la cabecera Retry-After indica los segundos de espera")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }



    @Operation(summary = "Entrar con una cuenta de demostración",
            description = "Devuelve un token de la cuenta de demo del rol pedido, sin contraseña. "
                    + "Solo existe para las cuentas marcadas como demo; en producción sus datos se reinician cada noche.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión de demo iniciada"),
            @ApiResponse(responseCode = "404", description = "No hay una cuenta de demo activa para ese rol"),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos desde esta IP (comparte el límite del login)")
    })
    @SecurityRequirements
    @PostMapping("/demo")
    public ResponseEntity<LoginResponse> entrarComoDemo(@Valid @RequestBody DemoLoginRequest request) {
        return ResponseEntity.ok(authService.entrarComoDemo(request.rol()));
    }




    @Operation(summary = "Registrar paciente",
            description = "Crea la cuenta de un paciente nuevo y le envía un correo para confirmar su email; "
                    + "hasta confirmarlo no puede iniciar sesión. Los doctores no se registran solos: los crea el admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paciente registrado"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese email o documento"),
            @ApiResponse(responseCode = "429", description = "Demasiados registros desde esta IP; "
                    + "la cabecera Retry-After indica los segundos de espera")
    })
    @SecurityRequirements
    @PostMapping("/registro")
    public ResponseEntity<PacienteResponse> registrarPaciente(@Valid @RequestBody RegistroPacienteRequest request) {
        Paciente paciente = authService.registrarPaciente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PacienteResponse.desde(paciente));
    }



    @Operation(summary = "Confirmar email",
            description = "Recibe el token del enlace enviado por correo. Vence en 24 horas y sirve una sola vez.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Email confirmado: ya puede iniciar sesión"),
            @ApiResponse(responseCode = "400", description = "Enlace inválido, vencido o ya usado")
    })
    @SecurityRequirements
    @PostMapping("/verificar-email")
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest request) {
        authService.verificarEmail(request.token());
        return ResponseEntity.noContent().build();
    }



    @Operation(summary = "Reenviar correo de confirmación",
            description = "Si existe una cuenta de paciente sin confirmar con ese email, le envía un enlace nuevo "
                    + "(el anterior deja de servir). Responde igual exista o no, para no revelar qué cuentas existen.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud recibida"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes desde esta IP; "
                    + "la cabecera Retry-After indica los segundos de espera")
    })
    @SecurityRequirements
    @PostMapping("/reenviar-verificacion")
    public ResponseEntity<Void> reenviarVerificacion(@Valid @RequestBody ReenviarVerificacionRequest request) {
        authService.reenviarVerificacion(request.email());
        return ResponseEntity.accepted().build();
    }



    @Operation(summary = "Olvidé mi contraseña",
            description = "Si existe una cuenta activa con ese email, le envía un enlace para elegir una contraseña nueva. "
                    + "Vence en 30 minutos. Responde igual exista o no, para no revelar qué cuentas existen.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Solicitud recibida"),
            @ApiResponse(responseCode = "429", description = "Demasiadas solicitudes desde esta IP; "
                    + "la cabecera Retry-After indica los segundos de espera")
    })
    @SecurityRequirements
    @PostMapping("/olvide-password")
    public ResponseEntity<Void> olvidePassword(@Valid @RequestBody OlvidePasswordRequest request) {
        authService.solicitarRecuperacion(request.email());
        return ResponseEntity.accepted().build();
    }



    @Operation(summary = "Restablecer contraseña",
            description = "Recibe el token del enlace enviado por correo y la contraseña nueva. "
                    + "Cierra todas las sesiones abiertas: hay que volver a iniciar sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "Enlace inválido, vencido o ya usado, o contraseña no válida")
    })
    @SecurityRequirements
    @PostMapping("/restablecer-password")
    public ResponseEntity<Void> restablecerPassword(@Valid @RequestBody RestablecerPasswordRequest request) {
        authService.restablecerPassword(request.token(), request.nuevaContrasena());
        return ResponseEntity.noContent().build();
    }



    @Operation(summary = "Mis datos",
            description = "Datos básicos del usuario del token: nombre, rol y, según el rol, su id de paciente o de doctor. "
                    + "El nombre es null para el admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos del usuario autenticado"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/yo")
    public ResponseEntity<UsuarioActualResponse> yo(@AuthenticationPrincipal UUID usuarioId) {
        return ResponseEntity.ok(usuarioService.obtenerActual(usuarioId));
    }



    @Operation(summary = "Cambiar mi contraseña",
            description = "Cualquier usuario autenticado cambia su propia contraseña. Exige la contraseña actual. "
                    + "Cierra todas las sesiones, incluida la actual: hay que volver a iniciar sesión.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "La contraseña actual es incorrecta o la nueva no es válida"),
            @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    })
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@AuthenticationPrincipal UUID usuarioId,
                                                @Valid @RequestBody CambiarPasswordRequest request) {
        usuarioService.cambiarPassword(usuarioId, request.contrasenaActual(), request.nuevaContrasena());
        return ResponseEntity.noContent().build();
    }
}
