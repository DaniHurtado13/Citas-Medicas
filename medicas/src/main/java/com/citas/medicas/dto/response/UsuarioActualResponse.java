package com.citas.medicas.dto.response;

import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.Usuario;

import java.util.UUID;

public record UsuarioActualResponse(
        UUID id,
        String email,
        Rol rol,
        String nombre,
        UUID pacienteId,
        UUID doctorId
) {
    public static UsuarioActualResponse dePaciente(Paciente paciente) {
        Usuario usuario = paciente.getUsuario();
        return new UsuarioActualResponse(usuario.getId(), usuario.getEmail(), usuario.getRol(),
                paciente.getNombre(), paciente.getId(), null);
    }

    public static UsuarioActualResponse deDoctor(Doctor doctor) {
        Usuario usuario = doctor.getUsuario();
        return new UsuarioActualResponse(usuario.getId(), usuario.getEmail(), usuario.getRol(),
                doctor.getNombre(), null, doctor.getId());
    }

    public static UsuarioActualResponse deAdmin(Usuario usuario) {
        return new UsuarioActualResponse(usuario.getId(), usuario.getEmail(), usuario.getRol(), null, null, null);
    }
}
