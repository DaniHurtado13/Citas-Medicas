package com.citas.medicas.service;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AutorizacionService {

    private final DoctorRepository doctorRepository;
    private final PacienteRepository pacienteRepository;

    public AutorizacionService(DoctorRepository doctorRepository, PacienteRepository pacienteRepository) {
        this.doctorRepository = doctorRepository;
        this.pacienteRepository = pacienteRepository;
    }

    public void validarAccesoAAgenda(UUID usuarioId, UUID doctorId) {
        Optional<Doctor> doctorDeUsuario = doctorRepository.findByUsuarioId(usuarioId);

        if (doctorDeUsuario.isPresent() && !doctorDeUsuario.get().getId().equals(doctorId)) {
            throw new AccesoDenegadoException("Solo puedes consultar tu propia agenda");
        }
    }

    public boolean esPacienteDeLaCita(UUID usuarioId, Cita cita) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .map(paciente -> paciente.getId().equals(cita.getPaciente().getId()))
                .orElse(false);
    }

    public boolean esDoctorDeLaCita(UUID usuarioId, Cita cita) {
        return doctorRepository.findByUsuarioId(usuarioId)
                .map(doctor -> doctor.getId().equals(cita.getDoctor().getId()))
                .orElse(false);
    }

    public void validarPacienteDeLaCita(UUID usuarioId, Cita cita) {
        if (!esPacienteDeLaCita(usuarioId, cita)) {
            throw new AccesoDenegadoException("Esta cita no te pertenece");
        }
    }

    public void validarDoctorDeLaCita(UUID usuarioId, Cita cita) {
        if (!esDoctorDeLaCita(usuarioId, cita)) {
            throw new AccesoDenegadoException("Esta cita no pertenece a tu agenda");
        }
    }
}
