package com.citas.medicas.service;

import com.citas.medicas.dto.response.UsuarioActualResponse;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.ContrasenaIncorrectaException;
import com.citas.medicas.exception.RecursoDuplicadoException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PacienteRepository pacienteRepository;
    private final DoctorRepository doctorRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          PacienteRepository pacienteRepository, DoctorRepository doctorRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.pacienteRepository = pacienteRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public UsuarioActualResponse obtenerActual(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        return switch (usuario.getRol()) {
            case PACIENTE -> pacienteRepository.findByUsuarioId(usuarioId)
                    .map(UsuarioActualResponse::dePaciente)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Perfil de paciente no encontrado"));
            case DOCTOR -> doctorRepository.findByUsuarioId(usuarioId)
                    .map(UsuarioActualResponse::deDoctor)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Perfil de doctor no encontrado"));
            case ADMIN -> UsuarioActualResponse.deAdmin(usuario);
        };
    }

    @Transactional
    public Usuario crearUsuario(TipoDocumento tipoDocumento, String numeroDocumento, String email,
                                String password, Rol rol) {
        String emailNormalizado = Usuario.normalizarEmail(email);
        if (usuarioRepository.existsByEmail(emailNormalizado)) {
            throw new RecursoDuplicadoException("Ya existe una cuenta con ese email");
        }
        if (usuarioRepository.existsByNumeroDocumento(numeroDocumento)) {
            throw new RecursoDuplicadoException("Ya existe una cuenta con ese número de documento");
        }

        Usuario usuario = new Usuario();
        usuario.setTipoDocumento(tipoDocumento);
        usuario.setNumeroDocumento(numeroDocumento);
        usuario.setEmail(emailNormalizado);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setEmailVerificado(rol != Rol.PACIENTE);

        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new RecursoDuplicadoException("Ya existe una cuenta con ese email o número de documento");
        }
    }

    @Transactional
    public void cambiarPassword(UUID usuarioId, String actual, String nueva) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        if (!passwordEncoder.matches(actual, usuario.getPassword())) {
            throw new ContrasenaIncorrectaException("La contraseña actual no es correcta");
        }

        usuario.setPassword(passwordEncoder.encode(nueva));
        usuario.invalidarSesiones();
        usuarioRepository.save(usuario);
    }
}
