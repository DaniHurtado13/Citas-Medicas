package com.citas.medicas.service;

import com.citas.medicas.entity.BloqueoAgenda;
import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.HorarioAtencion;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.repository.BloqueoAgendaRepository;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.HorarioAtencionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock
    private BloqueoAgendaRepository bloqueoAgendaRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private HorarioAtencionRepository horarioAtencionRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private DisponibilidadService disponibilidadService;

    @BeforeEach
    void crearServicio() {
        disponibilidadService = new DisponibilidadService(bloqueoAgendaRepository, citaRepository,
                horarioAtencionRepository, doctorRepository, Clock.systemDefaultZone());
    }

    private final UUID doctorId = UUID.randomUUID();
    private final LocalDate proximoLunes = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private void prepararAgenda(LocalDate fecha, int duracionMin, List<HorarioAtencion> horarios,
                                List<BloqueoAgenda> bloqueos, List<Cita> citas) {
        Doctor doctor = doctorConUsuario(true);
        doctor.setDuracionConsultaMin(duracionMin);
        LocalDateTime inicioDelDia = fecha.atStartOfDay();
        LocalDateTime finDelDia = fecha.plusDays(1).atStartOfDay();

        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(horarioAtencionRepository.findByDoctorIdAndDiaSemana(doctorId, fecha.getDayOfWeek()))
                .thenReturn(horarios);
        when(bloqueoAgendaRepository.findByDoctorIdAndFechaInicioLessThanAndFechaFinGreaterThan(
                doctorId, finDelDia, inicioDelDia)).thenReturn(bloqueos);
        when(citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNot(
                doctorId, inicioDelDia, finDelDia, EstadoCita.CANCELADA)).thenReturn(citas);
    }

    private Doctor doctorConUsuario(boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setActivo(activo);
        Doctor doctor = new Doctor();
        doctor.setUsuario(usuario);
        return doctor;
    }

    @Test
    void doctorInactivoNoTieneDisponibilidad() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctorConUsuario(false)));

        assertTrue(disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes).isEmpty());
        verifyNoInteractions(horarioAtencionRepository, bloqueoAgendaRepository, citaRepository);
    }

    private HorarioAtencion horario(String inicio, String fin) {
        HorarioAtencion horario = new HorarioAtencion();
        horario.setDiaSemana(DayOfWeek.MONDAY);
        horario.setHoraInicio(LocalTime.parse(inicio));
        horario.setHoraFin(LocalTime.parse(fin));
        return horario;
    }

    private BloqueoAgenda bloqueo(LocalDateTime inicio, LocalDateTime fin) {
        BloqueoAgenda bloqueo = new BloqueoAgenda();
        bloqueo.setFechaInicio(inicio);
        bloqueo.setFechaFin(fin);
        return bloqueo;
    }

    private Cita cita(LocalDateTime fechaHora) {
        Cita cita = new Cita();
        cita.setFechaHora(fechaHora);
        cita.setEstado(EstadoCita.CONFIRMADA);
        return cita;
    }

    private LocalDateTime lunesA(String hora) {
        return proximoLunes.atTime(LocalTime.parse(hora));
    }

    private List<LocalDateTime> lunesA(String... horas) {
        return Arrays.stream(horas).map(this::lunesA).toList();
    }

    @Test
    void ejemploDelLunesDevuelveLosHorariosLibres() {
        prepararAgenda(proximoLunes, 30,
                List.of(horario("08:00", "12:00")),
                List.of(bloqueo(lunesA("10:00"), lunesA("11:00"))),
                List.of(cita(lunesA("08:30"))));

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertEquals(lunesA("08:00", "09:00", "09:30", "11:00", "11:30"), disponibles);
    }

    @Test
    void bloqueoParcialDescartaLasConsultasQueToca() {
        prepararAgenda(proximoLunes, 30,
                List.of(horario("09:30", "11:30")),
                List.of(bloqueo(lunesA("10:15"), lunesA("10:45"))),
                List.of());

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertEquals(lunesA("09:30", "11:00"), disponibles);
    }

    @Test
    void bloqueoQueEmpiezaElDiaAnteriorTambienDescarta() {
        LocalDateTime domingoEnLaTarde = proximoLunes.minusDays(1).atTime(18, 0);
        prepararAgenda(proximoLunes, 30,
                List.of(horario("08:00", "10:00")),
                List.of(bloqueo(domingoEnLaTarde, lunesA("09:00"))),
                List.of());

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertEquals(lunesA("09:00", "09:30"), disponibles);
    }

    @Test
    void citaQueSeSolapaSinCoincidirEnLaHoraDescartaLosCandidatos() {
        prepararAgenda(proximoLunes, 20,
                List.of(horario("09:00", "10:20")),
                List.of(),
                List.of(cita(lunesA("09:30"))));

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertEquals(lunesA("09:00", "10:00"), disponibles);
    }

    @Test
    void variosBloquesDeHorarioSeDevuelvenOrdenados() {
        prepararAgenda(proximoLunes, 30,
                List.of(horario("14:00", "15:00"), horario("08:00", "09:00")),
                List.of(),
                List.of());

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertEquals(lunesA("08:00", "08:30", "14:00", "14:30"), disponibles);
    }

    @Test
    void diaSinHorarioNoTieneDisponibilidad() {
        prepararAgenda(proximoLunes, 30, List.of(), List.of(), List.of());

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes);

        assertTrue(disponibles.isEmpty());
    }

    @Test
    void fechaPasadaNoTieneDisponibilidad() {
        LocalDate lunesPasado = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        prepararAgenda(lunesPasado, 30, List.of(horario("08:00", "12:00")), List.of(), List.of());

        List<LocalDateTime> disponibles = disponibilidadService.calcularDisponibilidad(doctorId, lunesPasado);

        assertTrue(disponibles.isEmpty());
    }

    @Test
    void doctorInexistenteLanzaExcepcion() {
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> disponibilidadService.calcularDisponibilidad(doctorId, proximoLunes));
        verifyNoInteractions(horarioAtencionRepository, bloqueoAgendaRepository, citaRepository);
    }
}
