package com.citas.medicas.service;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutorizacionServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @InjectMocks
    private AutorizacionService autorizacionService;

    private final UUID usuarioId = UUID.randomUUID();
    private final UUID agendaConsultada = UUID.randomUUID();

    private Doctor doctorConId(UUID id) {
        Doctor doctor = new Doctor();
        doctor.setId(id);
        return doctor;
    }

    private Paciente pacienteConId(UUID id) {
        Paciente paciente = new Paciente();
        paciente.setId(id);
        return paciente;
    }

    private Cita citaDe(Paciente paciente, Doctor doctor) {
        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setDoctor(doctor);
        return cita;
    }

    // ---------- Agenda ----------

    @Test
    void usuarioQueNoEsDoctorPuedeVerCualquierAgenda() {
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> autorizacionService.validarAccesoAAgenda(usuarioId, agendaConsultada));
    }

    @Test
    void doctorPuedeVerSuPropiaAgenda() {
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctorConId(agendaConsultada)));

        assertDoesNotThrow(() -> autorizacionService.validarAccesoAAgenda(usuarioId, agendaConsultada));
    }

    @Test
    void doctorNoPuedeVerLaAgendaDeOtroDoctor() {
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctorConId(UUID.randomUUID())));

        assertThrows(AccesoDenegadoException.class,
                () -> autorizacionService.validarAccesoAAgenda(usuarioId, agendaConsultada));
    }

    // ---------- Paciente de la cita ----------

    @Test
    void pacienteDeLaCitaPasaLaValidacion() {
        Paciente paciente = pacienteConId(UUID.randomUUID());
        Cita cita = citaDe(paciente, doctorConId(UUID.randomUUID()));
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));

        assertDoesNotThrow(() -> autorizacionService.validarPacienteDeLaCita(usuarioId, cita));
    }

    @Test
    void otroPacienteNoPasaLaValidacion() {
        Cita cita = citaDe(pacienteConId(UUID.randomUUID()), doctorConId(UUID.randomUUID()));
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(pacienteConId(UUID.randomUUID())));

        assertThrows(AccesoDenegadoException.class,
                () -> autorizacionService.validarPacienteDeLaCita(usuarioId, cita));
    }

    @Test
    void usuarioSinPacienteNoPasaLaValidacion() {
        Cita cita = citaDe(pacienteConId(UUID.randomUUID()), doctorConId(UUID.randomUUID()));
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThrows(AccesoDenegadoException.class,
                () -> autorizacionService.validarPacienteDeLaCita(usuarioId, cita));
    }

    // ---------- Doctor de la cita ----------

    @Test
    void doctorDeLaCitaPasaLaValidacion() {
        Doctor doctor = doctorConId(UUID.randomUUID());
        Cita cita = citaDe(pacienteConId(UUID.randomUUID()), doctor);
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));

        assertDoesNotThrow(() -> autorizacionService.validarDoctorDeLaCita(usuarioId, cita));
    }

    @Test
    void otroDoctorNoPasaLaValidacion() {
        Cita cita = citaDe(pacienteConId(UUID.randomUUID()), doctorConId(UUID.randomUUID()));
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctorConId(UUID.randomUUID())));

        assertThrows(AccesoDenegadoException.class,
                () -> autorizacionService.validarDoctorDeLaCita(usuarioId, cita));
    }
}
