package com.citas.medicas.service;

import com.citas.medicas.TestcontainersConfiguration;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.Especialidad;
import com.citas.medicas.entity.HorarioAtencion;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.CitaConflictException;
import com.citas.medicas.exception.HorarioNoDisponibleException;
import com.citas.medicas.exception.LimiteDeReservasException;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.EspecialidadRepository;
import com.citas.medicas.repository.HorarioAtencionRepository;
import com.citas.medicas.repository.PacienteRepository;
import com.citas.medicas.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "jwt.secret=b3RyYS1jbGF2ZS1kaWZlcmVudGUtZGUtMzItYnl0ZXM=")
class ReservaConcurrenteTest {

    private static final int HILOS = 10;

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
    HorarioAtencionRepository horarioAtencionRepository;

    @Autowired
    PacienteRepository pacienteRepository;

    private final List<UUID> usuariosPacientes = new ArrayList<>();
    private final List<UUID> pacientes = new ArrayList<>();
    private UUID doctorId;
    private LocalDate fecha;
    private LocalDateTime fechaHora;

    @BeforeEach
    void prepararDatos() {
        // Dentro de una semana siempre es futuro, y cae en un día que el doctor atiende
        fecha = LocalDate.now().plusDays(7);
        fechaHora = fecha.atTime(9, 0);

        Especialidad especialidad = new Especialidad();
        especialidad.setNombre("Medicina general");
        especialidad.setDescripcion("Consulta general");
        especialidadRepository.save(especialidad);

        Doctor doctor = new Doctor();
        doctor.setUsuario(crearUsuario("doctor@test.dev", "1000000001", Rol.DOCTOR));
        doctor.setEspecialidad(especialidad);
        doctor.setNombre("Doctora de prueba");
        doctor.setDuracionConsultaMin(30);
        doctor.setConsultorio("Consultorio 1");
        doctorRepository.save(doctor);
        doctorId = doctor.getId();

        HorarioAtencion horario = new HorarioAtencion();
        horario.setDoctor(doctor);
        horario.setDiaSemana(fecha.getDayOfWeek());
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(12, 0));
        horarioAtencionRepository.save(horario);

        // Un paciente por hilo: el doble booking realista es entre pacientes distintos.
        // Con uno solo, el bloqueo por paciente los pondría en fila y nunca se pondría a prueba el índice único.
        for (int i = 0; i < HILOS; i++) {
            Usuario usuarioPaciente = crearUsuario("paciente" + i + "@test.dev", "200000000" + i, Rol.PACIENTE);
            Paciente paciente = new Paciente();
            paciente.setUsuario(usuarioPaciente);
            paciente.setNombre("Paciente " + i);
            paciente.setTelefono("3000000000");
            paciente.setFechaNacimiento(LocalDate.of(1990, 1, 1));
            pacienteRepository.save(paciente);
            usuariosPacientes.add(usuarioPaciente.getId());
            pacientes.add(paciente.getId());
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
        // Orden inverso al de creación: primero los hijos, luego los padres
        citaRepository.deleteAll();
        horarioAtencionRepository.deleteAll();
        pacienteRepository.deleteAll();
        doctorRepository.deleteAll();
        usuarioRepository.deleteAll();
        especialidadRepository.deleteAll();
    }

    private List<Cita> citasActivasEnElHorario() {
        return citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNot(
                doctorId, fechaHora, fechaHora, EstadoCita.CANCELADA);
    }

    /**
     * Lanza todas las tareas a la vez (pistola de salida) y devuelve, por cada una, la excepción que lanzó
     * o null si terminó bien.
     */
    private List<Throwable> correrALaVez(List<Callable<Object>> tareas) throws Exception {
        ExecutorService corredores = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch pistolaDeSalida = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();

        for (Callable<Object> tarea : tareas) {
            futuros.add(corredores.submit(() -> {
                pistolaDeSalida.await();
                return tarea.call();
            }));
        }
        pistolaDeSalida.countDown();

        List<Throwable> resultados = new ArrayList<>();
        for (Future<Object> futuro : futuros) {
            try {
                futuro.get(30, TimeUnit.SECONDS);
                resultados.add(null);
            } catch (ExecutionException ex) {
                resultados.add(ex.getCause());
            }
        }
        corredores.shutdown();
        return resultados;
    }

    private long contar(List<Throwable> resultados, Class<?> tipo) {
        return resultados.stream().filter(tipo::isInstance).count();
    }

    @Test
    void reservaIndividualSeGuarda() {
        citaService.reservarCita(usuariosPacientes.getFirst(), doctorId, fechaHora, "Reserva de prueba");

        assertEquals(1, citasActivasEnElHorario().size());
    }

    @Test
    void soloUnaReservaGanaElMismoHorario() throws Exception {
        List<Callable<Object>> tareas = new ArrayList<>();
        for (UUID usuarioPaciente : usuariosPacientes) {
            tareas.add(() -> citaService.reservarCita(usuarioPaciente, doctorId, fechaHora, "Reserva concurrente"));
        }

        List<Throwable> resultados = correrALaVez(tareas);

        long exitos = resultados.stream().filter(r -> r == null).count();
        long conflictos = contar(resultados, CitaConflictException.class);
        long noDisponibles = contar(resultados, HorarioNoDisponibleException.class);
        System.out.printf("Carrera de %d pacientes: %d éxito, %d CitaConflict (índice), %d HorarioNoDisponible (validación)%n",
                HILOS, exitos, conflictos, noDisponibles);

        assertEquals(HILOS, exitos + conflictos + noDisponibles, "Fallos inesperados: " + resultados);
        assertEquals(1, exitos, "Debe ganar exactamente una reserva");
        assertEquals(1, citasActivasEnElHorario().size(), "Debe quedar una sola cita activa en el horario");
    }

    @Test
    void unPacienteNoPuedeAcapararLosTurnosDeUnDoctorConReservasSimultaneas() throws Exception {
        UUID usuarioPaciente = usuariosPacientes.getFirst();
        // Los 8 turnos de la mañana (8:00 a 11:30), todos libres y todos del mismo doctor
        List<Callable<Object>> tareas = new ArrayList<>();
        for (LocalDateTime turno = fecha.atTime(8, 0); turno.isBefore(fecha.atTime(12, 0)); turno = turno.plusMinutes(30)) {
            LocalDateTime hora = turno;
            tareas.add(() -> citaService.reservarCita(usuarioPaciente, doctorId, hora, "Acaparar"));
        }

        List<Throwable> resultados = correrALaVez(tareas);

        long exitos = resultados.stream().filter(r -> r == null).count();
        long limitados = contar(resultados, LimiteDeReservasException.class);
        System.out.printf("Mismo paciente, %d turnos a la vez: %d éxito, %d LimiteDeReservas%n",
                tareas.size(), exitos, limitados);

        assertEquals(1, exitos, "Solo una cita activa con el mismo doctor");
        assertEquals(tareas.size() - 1, limitados, "Fallos inesperados: " + resultados);
        assertEquals(1, citaRepository.countByPacienteIdAndEstadoInAndFechaHoraAfter(pacientes.getFirst(),
                List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA), LocalDateTime.now()));
    }

    @Test
    void conDoctoresDistintosPermiteTresCitasPeroNoLaCuarta() {
        // El máximo total se prueba con doctores distintos (con uno solo, actúa antes el límite por doctor)
        UUID usuarioPaciente = usuariosPacientes.getFirst();
        List<UUID> otrosDoctores = new ArrayList<>(List.of(doctorId));
        for (int i = 1; i <= 3; i++) {
            otrosDoctores.add(crearDoctorConHorario(i));
        }

        for (int i = 0; i < 3; i++) {
            citaService.reservarCita(usuarioPaciente, otrosDoctores.get(i), fechaHora, "Cita " + i);
        }

        assertThrows(LimiteDeReservasException.class,
                () -> citaService.reservarCita(usuarioPaciente, otrosDoctores.get(3), fechaHora, "La cuarta"));
    }

    private UUID crearDoctorConHorario(int numero) {
        Doctor doctor = new Doctor();
        doctor.setUsuario(crearUsuario("doctor" + numero + "@test.dev", "100000000" + (numero + 1), Rol.DOCTOR));
        doctor.setEspecialidad(especialidadRepository.findAll().getFirst());
        doctor.setNombre("Doctor " + numero);
        doctor.setDuracionConsultaMin(30);
        doctor.setConsultorio("Consultorio " + (numero + 1));
        doctorRepository.save(doctor);

        HorarioAtencion horario = new HorarioAtencion();
        horario.setDoctor(doctor);
        horario.setDiaSemana(fecha.getDayOfWeek());
        horario.setHoraInicio(LocalTime.of(8, 0));
        horario.setHoraFin(LocalTime.of(12, 0));
        horarioAtencionRepository.save(horario);
        return doctor.getId();
    }
}
