package com.citas.medicas.service;

import com.citas.medicas.dto.response.UsuarioActualResponse;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.ContrasenaIncorrectaException;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario crear(Rol rol) {
        return usuarioService.crearUsuario(TipoDocumento.CC, "1000000099", "ana@correo.com", "clave12345", rol);
    }

    @Test
    void creaUsuarioActivoConPasswordHasheadaYElRolIndicado() {
        when(passwordEncoder.encode("clave12345")).thenReturn("hash-bcrypt");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = crear(Rol.DOCTOR);

        assertEquals("hash-bcrypt", usuario.getPassword());
        assertEquals(Rol.DOCTOR, usuario.getRol());
        assertTrue(usuario.getActivo());
        assertEquals("ana@correo.com", usuario.getEmail());
    }

    @Test
    void elEmailSeGuardaEnMinusculasYSinEspacios() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario usuario = usuarioService.crearUsuario(TipoDocumento.CC, "1000000099", "  Ana@Correo.COM ",
                "clave12345", Rol.PACIENTE);

        assertEquals("ana@correo.com", usuario.getEmail());
        verify(usuarioRepository).existsByEmail("ana@correo.com");
    }

    @Test
    void soloElPacienteNaceConElEmailSinConfirmar() {
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        assertFalse(crear(Rol.PACIENTE).getEmailVerificado());
        assertTrue(crear(Rol.DOCTOR).getEmailVerificado());
        assertTrue(crear(Rol.ADMIN).getEmailVerificado());
    }

    @Test
    void emailRepetidoLanzaExcepcion() {
        when(usuarioRepository.existsByEmail("ana@correo.com")).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class, () -> crear(Rol.PACIENTE));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void documentoRepetidoLanzaExcepcion() {
        when(usuarioRepository.existsByNumeroDocumento("1000000099")).thenReturn(true);

        assertThrows(RecursoDuplicadoException.class, () -> crear(Rol.PACIENTE));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void creacionSimultaneaConMismoEmailLanzaExcepcion() {
        when(passwordEncoder.encode("clave12345")).thenReturn("hash-bcrypt");
        when(usuarioRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Violacion de UNIQUE"));

        assertThrows(RecursoDuplicadoException.class, () -> crear(Rol.PACIENTE));
    }

    // ---------- Cambiar contraseña ----------

    private final UUID usuarioId = UUID.randomUUID();

    private Usuario usuarioConHash(String hash) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setPassword(hash);
        return usuario;
    }

    @Test
    void cambiarPasswordConLaActualCorrectaGuardaElNuevoHash() {
        Usuario usuario = usuarioConHash("hash-viejo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave123", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.encode("nuevaClave456")).thenReturn("hash-nuevo");

        usuarioService.cambiarPassword(usuarioId, "clave123", "nuevaClave456");

        assertEquals("hash-nuevo", usuario.getPassword());
        assertEquals(1, usuario.getVersionSesion());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void cambiarPasswordConLaActualIncorrectaNoGuardaNada() {
        Usuario usuario = usuarioConHash("hash-viejo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("equivocada", "hash-viejo")).thenReturn(false);

        assertThrows(ContrasenaIncorrectaException.class,
                () -> usuarioService.cambiarPassword(usuarioId, "equivocada", "nuevaClave456"));
        assertEquals("hash-viejo", usuario.getPassword());
        assertEquals(0, usuario.getVersionSesion());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarPasswordDeUsuarioInexistenteLanzaExcepcion() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.cambiarPassword(usuarioId, "clave123", "nuevaClave456"));
    }

    // ---------- Usuario actual ----------

    private Usuario usuarioCon(Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setEmail("ana@correo.com");
        usuario.setRol(rol);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    void elPacienteRecibeSuNombreYSuIdDePaciente() {
        Paciente paciente = new Paciente();
        paciente.setId(UUID.randomUUID());
        paciente.setNombre("Ana Ruiz");
        paciente.setUsuario(usuarioCon(Rol.PACIENTE));
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));

        UsuarioActualResponse yo = usuarioService.obtenerActual(usuarioId);

        assertEquals(new UsuarioActualResponse(usuarioId, "ana@correo.com", Rol.PACIENTE, "Ana Ruiz",
                paciente.getId(), null), yo);
    }

    @Test
    void elDoctorRecibeSuNombreYSuIdDeDoctor() {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setNombre("Laura Gómez");
        doctor.setUsuario(usuarioCon(Rol.DOCTOR));
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));

        UsuarioActualResponse yo = usuarioService.obtenerActual(usuarioId);

        assertEquals(new UsuarioActualResponse(usuarioId, "ana@correo.com", Rol.DOCTOR, "Laura Gómez",
                null, doctor.getId()), yo);
    }

    @Test
    void elAdminNoTienePerfilNiNombre() {
        usuarioCon(Rol.ADMIN);

        UsuarioActualResponse yo = usuarioService.obtenerActual(usuarioId);

        assertEquals(Rol.ADMIN, yo.rol());
        assertNull(yo.nombre());
        verify(pacienteRepository, never()).findByUsuarioId(any());
        verify(doctorRepository, never()).findByUsuarioId(any());
    }

    @Test
    void usuarioInexistenteLanzaExcepcion() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> usuarioService.obtenerActual(usuarioId));
    }

    @Test
    void pacienteSinPerfilLanzaExcepcion() {
        usuarioCon(Rol.PACIENTE);
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> usuarioService.obtenerActual(usuarioId));
    }
}
