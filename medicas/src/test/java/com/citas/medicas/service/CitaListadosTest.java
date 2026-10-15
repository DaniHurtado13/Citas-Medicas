package com.citas.medicas.service;

import com.citas.medicas.TestcontainersConfiguration;
import com.citas.medicas.dto.response.CitaResponse;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Los listados de citas deben traer el nombre del paciente y del doctor en la misma consulta (sin N+1).
 * Las respuestas se arman fuera de una transacción: si los nombres no vinieran cargados, fallaría con
 * LazyInitializationException.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "jwt.secret=b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class CitaListadosTest {

    private static final int PACIENTES = 3;

    @Autowired
    CitaService citaService;

    @Autowired
    CitaRepository citaRepository;

    @Autowired
    EspecialidadRepository especialidadRepository;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    DoctorRepository doctorRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private UUID usuarioDoctorId;
    private UUID usuarioPrimerPacienteId;
    private LocalDate fecha;

    @BeforeEach
    void prepararDatos() {
        fecha = LocalDate.now().plusDays(7);

        Especialidad especialidad = new Especialidad();
        especialidad.setNombre("Medicina general");
        especialidad.setDescripcion("Consulta general");
        especialidadRepository.save(especialidad);

        Usuario usuarioDoctor = crearUsuario("doctor@listados.dev", "1000000001", Rol.DOCTOR);
        Doctor doctor = new Doctor();
        doctor.setUsuario(usuarioDoctor);
        doctor.setEspecialidad(especialidad);
        doctor.setNombre("Doctora Agenda");
        doctor.setDuracionConsultaMin(30);
        doctor.setConsultorio("Consultorio 1");
        doctorRepository.save(doctor);
        usuarioDoctorId = usuarioDoctor.getId();

        // Un paciente distinto por cita: es el caso en que el N+1 se nota
        for (int i = 1; i <= PACIENTES; i++) {
            Usuario usuarioPaciente = crearUsuario("paciente" + i + "@listados.dev", "200000000" + i, Rol.PACIENTE);
            Paciente paciente = new Paciente();
            paciente.setUsuario(usuarioPaciente);
            paciente.setNombre("Paciente " + i);
            paciente.setTelefono("3000000000");
            paciente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            pacienteRepository.save(paciente);
            if (i == 1) {
                usuarioPrimerPacienteId = usuarioPaciente.getId();
            }

            Cita cita = new Cita();
            cita.setPaciente(paciente);
            cita.setDoctor(doctor);
            cita.setFechaHora(fecha.atTime(8 + i, 0));
            cita.setEstado(EstadoCita.CONFIRMADA);
            cita.setMotivoConsulta("Control " + i);
            citaRepository.save(cita);
        }
    }

    private Usuario crearUsuario(String email, String documento, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setTipoDocumento(TipoDocumento.CC);
        usuario.setNumeroDocumento(documento);
        usuario.setEmail(email);
        usuario.setPassword("no-se-usa-en-este-test");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setEmailVerificado(true);
        return usuarioRepository.save(usuario);
    }

    @AfterEach
    void limpiar() {
        citaRepository.deleteAll();
        pacienteRepository.deleteAll();
        doctorRepository.deleteAll();
        usuarioRepository.deleteAll();
        especialidadRepository.deleteAll();
    }

    private Statistics estadisticas() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        return estadisticas;
    }

    @Test
    void agendaDelDoctorTraeLosNombresSinConsultasExtra() {
        Statistics estadisticas = estadisticas();

        List<CitaResponse> agenda = citaService.listarAgendaDoctor(usuarioDoctorId, fecha, fecha).stream()
                .map(CitaResponse::desde)
                .toList();

        assertEquals(List.of("Paciente 1", "Paciente 2", "Paciente 3"),
                agenda.stream().map(CitaResponse::pacienteNombre).toList());
        assertEquals("Doctora Agenda", agenda.getFirst().doctorNombre());
        // 1 para encontrar al doctor del token + 1 para la agenda con sus pacientes, sin importar cuántas citas haya
        assertEquals(2, estadisticas.getPrepareStatementCount());
    }

    @Test
    void misCitasDelPacienteTraeLosNombres() {
        Statistics estadisticas = estadisticas();

        List<CitaResponse> misCitas = citaService.listarCitasPaciente(usuarioPrimerPacienteId, PageRequest.of(0, 10))
                .map(CitaResponse::desde)
                .getContent();

        assertEquals(1, misCitas.size());
        assertEquals("Paciente 1", misCitas.getFirst().pacienteNombre());
        assertEquals("Doctora Agenda", misCitas.getFirst().doctorNombre());
        // 1 para encontrar al paciente del token + 1 para sus citas con paciente y doctor
        assertEquals(2, estadisticas.getPrepareStatementCount());
    }
}
