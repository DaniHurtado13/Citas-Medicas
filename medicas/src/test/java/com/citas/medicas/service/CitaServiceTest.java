package com.citas.medicas.service;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.exception.CancelacionNoPermitidaException;
import com.citas.medicas.exception.CitaConflictException;
import com.citas.medicas.exception.FechaInvalidaException;
import com.citas.medicas.exception.HorarioNoDisponibleException;
import com.citas.medicas.exception.LimiteDeReservasException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.evento.CitaCanceladaPorConsultorioEvent;
import com.citas.medicas.evento.CitaConfirmadaEvent;
import com.citas.medicas.exception.TransicionInvalidaException;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private VentanaCancelacionValidator ventanaCancelacionValidator;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DisponibilidadService disponibilidadService;

    @Mock
    private AutorizacionService autorizacionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private LimiteDeReservasValidator limiteDeReservasValidator;

    private CitaService citaService;

    @BeforeEach
    void crearServicio() {
        citaService = new CitaService(citaRepository, ventanaCancelacionValidator, pacienteRepository, doctorRepository,
                disponibilidadService, autorizacionService, eventPublisher, limiteDeReservasValidator,
                Clock.systemDefaultZone());
    }

    private final UUID idCita = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    private Cita crearCita(EstadoCita estado) {
        Cita cita = new Cita();
        cita.setId(idCita);
        cita.setEstado(estado);
        cita.setFechaHora(LocalDateTime.now().plusDays(1));
        return cita;
    }

    // ---------- Cancelar ----------

    private void comoPacienteDeLaCita(Cita cita) {
        when(autorizacionService.esPacienteDeLaCita(usuarioId, cita)).thenReturn(true);
    }

    private void comoDoctorDeLaCita(Cita cita) {
        when(autorizacionService.esPacienteDeLaCita(usuarioId, cita)).thenReturn(false);
        when(autorizacionService.esDoctorDeLaCita(usuarioId, cita)).thenReturn(true);
    }

    @Test
    void pacienteCancelaCitaConfirmadaYQuedaCancelada() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        comoPacienteDeLaCita(cita);

        citaService.cancelarCita(idCita, usuarioId);

        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(citaRepository).save(cita);
        verify(eventPublisher, never()).publishEvent(any(CitaCanceladaPorConsultorioEvent.class));
    }

    @Test
    void cancelarCitaInexistenteLanzaExcepcion() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> citaService.cancelarCita(idCita, usuarioId));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void pacienteNoPuedeCancelarCitaPendiente() {
        Cita cita = crearCita(EstadoCita.PENDIENTE);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        comoPacienteDeLaCita(cita);

        assertThrows(CancelacionNoPermitidaException.class, () -> citaService.cancelarCita(idCita, usuarioId));
        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void cancelarCitaCompletadaLanzaExcepcion() {
        Cita cita = crearCita(EstadoCita.COMPLETADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        comoPacienteDeLaCita(cita);

        assertThrows(TransicionInvalidaException.class, () -> citaService.cancelarCita(idCita, usuarioId));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void pacienteNoPuedeCancelarFueraDeLaVentana() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        comoPacienteDeLaCita(cita);
        doThrow(new CancelacionNoPermitidaException("Fuera de la ventana"))
                .when(ventanaCancelacionValidator).validar(cita.getFechaHora());

        assertThrows(CancelacionNoPermitidaException.class, () -> citaService.cancelarCita(idCita, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void doctorPuedeCancelarCitaPendienteSinVentana() {
        Cita cita = crearCita(EstadoCita.PENDIENTE);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        comoDoctorDeLaCita(cita);

        citaService.cancelarCita(idCita, usuarioId);

        assertEquals(EstadoCita.CANCELADA, cita.getEstado());
        verify(ventanaCancelacionValidator, never()).validar(any());
        verify(citaRepository).save(cita);
        verify(eventPublisher).publishEvent(new CitaCanceladaPorConsultorioEvent(idCita));
    }

    @Test
    void usuarioAjenoALaCitaNoPuedeCancelarla() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        when(autorizacionService.esPacienteDeLaCita(usuarioId, cita)).thenReturn(false);
        when(autorizacionService.esDoctorDeLaCita(usuarioId, cita)).thenReturn(false);

        assertThrows(AccesoDenegadoException.class, () -> citaService.cancelarCita(idCita, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(CitaCanceladaPorConsultorioEvent.class));
    }

    // ---------- Cancelación masiva al desactivar un doctor ----------

    @Test
    void cancelarCitasFuturasDelDoctorCancelaCadaUnaYAvisaASuPaciente() {
        UUID idDoctor = UUID.randomUUID();
        Cita pendiente = crearCita(EstadoCita.PENDIENTE);
        pendiente.setId(UUID.randomUUID());
        Cita confirmada = crearCita(EstadoCita.CONFIRMADA);
        confirmada.setId(UUID.randomUUID());
        when(citaRepository.findByDoctorIdAndEstadoInAndFechaHoraAfter(
                eq(idDoctor), eq(List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA)), any()))
                .thenReturn(List.of(pendiente, confirmada));

        int canceladas = citaService.cancelarCitasFuturasDelDoctor(idDoctor);

        assertEquals(2, canceladas);
        assertEquals(EstadoCita.CANCELADA, pendiente.getEstado());
        assertEquals(EstadoCita.CANCELADA, confirmada.getEstado());
        verify(eventPublisher).publishEvent(new CitaCanceladaPorConsultorioEvent(pendiente.getId()));
        verify(eventPublisher).publishEvent(new CitaCanceladaPorConsultorioEvent(confirmada.getId()));
    }

    @Test
    void cancelarCitasFuturasSinCitasNoAvisaANadie() {
        when(citaRepository.findByDoctorIdAndEstadoInAndFechaHoraAfter(any(), any(), any())).thenReturn(List.of());

        assertEquals(0, citaService.cancelarCitasFuturasDelDoctor(UUID.randomUUID()));
        verify(eventPublisher, never()).publishEvent(any(CitaCanceladaPorConsultorioEvent.class));
    }

    // ---------- Reservar ----------

    private final UUID doctorId = UUID.randomUUID();
    private final LocalDateTime manana = LocalDateTime.now().plusDays(1);

    private void marcarDisponible(LocalDateTime fechaHora) {
        when(disponibilidadService.calcularDisponibilidad(doctorId, fechaHora.toLocalDate()))
                .thenReturn(List.of(fechaHora));
    }

    @Test
    void reservarCitaExitosaUsaElPacienteDelToken() {
        Paciente paciente = new Paciente();
        Doctor doctor = new Doctor();
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        marcarDisponible(manana);

        Cita cita = citaService.reservarCita(usuarioId, doctorId, manana, "Control");

        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        assertSame(paciente, cita.getPaciente());
        assertSame(doctor, cita.getDoctor());
        verify(citaRepository).saveAndFlush(cita);
    }

    @Test
    void reservarSinPacienteAsociadoLanzaExcepcion() {
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, manana, "Control"));
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservarConDoctorInexistenteLanzaExcepcion() {
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(new Paciente()));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, manana, "Control"));
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservarEnFechaPasadaLanzaExcepcion() {
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(new Paciente()));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(new Doctor()));
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);

        assertThrows(FechaInvalidaException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, ayer, "Control"));
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservarEnHorarioNoDisponibleLanzaExcepcion() {
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(new Paciente()));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(new Doctor()));
        when(disponibilidadService.calcularDisponibilidad(doctorId, manana.toLocalDate()))
                .thenReturn(List.of());

        assertThrows(HorarioNoDisponibleException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, manana, "Control"));
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reservarSiOtroPacienteLaTomaPrimeroLanzaCitaConflictException() {
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(new Paciente()));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(new Doctor()));
        marcarDisponible(manana);
        when(citaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Violacion de UNIQUE"));

        assertThrows(CitaConflictException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, manana, "Control"));
    }

    @Test
    void reservarValidaElLimiteConElPacienteDelTokenYElDoctorPedido() {
        Paciente paciente = new Paciente();
        paciente.setId(UUID.randomUUID());
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(new Doctor()));
        marcarDisponible(manana);

        citaService.reservarCita(usuarioId, doctorId, manana, "Control");

        verify(limiteDeReservasValidator).validar(paciente.getId(), doctorId);
    }

    @Test
    void reservarConElLimiteAlcanzadoNoGuardaNiConsultaLaDisponibilidad() {
        Paciente paciente = new Paciente();
        paciente.setId(UUID.randomUUID());
        when(pacienteRepository.findConBloqueoByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(new Doctor()));
        doThrow(new LimiteDeReservasException("Ya tienes 3 citas activas"))
                .when(limiteDeReservasValidator).validar(paciente.getId(), doctorId);

        assertThrows(LimiteDeReservasException.class,
                () -> citaService.reservarCita(usuarioId, doctorId, manana, "Control"));
        verifyNoInteractions(disponibilidadService);
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarNoAplicaElLimiteDeReservas() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        marcarDisponible(nuevaFecha);

        citaService.reprogramarCita(idCita, nuevaFecha, usuarioId);

        verifyNoInteractions(limiteDeReservasValidator);
    }

    // ---------- Reprogramar ----------

    private final LocalDateTime nuevaFecha = LocalDateTime.now().plusDays(3);

    private Cita crearCitaOriginal(EstadoCita estado) {
        Cita original = crearCita(estado);
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        original.setPaciente(new Paciente());
        original.setDoctor(doctor);
        original.setMotivoConsulta("Control");
        return original;
    }

    @Test
    void reprogramarCitaConfirmadaCreaNuevaEnlazadaALaOriginal() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        marcarDisponible(nuevaFecha);

        Cita nueva = citaService.reprogramarCita(idCita, nuevaFecha, usuarioId);

        assertEquals(EstadoCita.CANCELADA, original.getEstado());
        assertEquals(EstadoCita.PENDIENTE, nueva.getEstado());
        assertEquals(nuevaFecha, nueva.getFechaHora());
        assertSame(original, nueva.getCitaOrigen());
        assertSame(original.getPaciente(), nueva.getPaciente());
        assertSame(original.getDoctor(), nueva.getDoctor());
        assertEquals("Control", nueva.getMotivoConsulta());
        verify(autorizacionService).validarPacienteDeLaCita(usuarioId, original);
        verify(eventPublisher, never()).publishEvent(any(CitaCanceladaPorConsultorioEvent.class));
        verify(citaRepository).save(original);
        verify(citaRepository).saveAndFlush(nueva);
    }

    @Test
    void reprogramarCitaInexistenteLanzaExcepcion() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarCitaAjenaLanzaExcepcion() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        doThrow(new AccesoDenegadoException("Esta cita no te pertenece"))
                .when(autorizacionService).validarPacienteDeLaCita(usuarioId, original);

        assertThrows(AccesoDenegadoException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, original.getEstado());
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarCitaPendienteCreaNuevaEnlazadaALaOriginal() {
        Cita original = crearCitaOriginal(EstadoCita.PENDIENTE);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        marcarDisponible(nuevaFecha);

        Cita nueva = citaService.reprogramarCita(idCita, nuevaFecha, usuarioId);

        assertEquals(EstadoCita.CANCELADA, original.getEstado());
        assertEquals(EstadoCita.PENDIENTE, nueva.getEstado());
        assertSame(original, nueva.getCitaOrigen());
    }

    @Test
    void reprogramarCitaCompletadaLanzaExcepcion() {
        Cita original = crearCitaOriginal(EstadoCita.COMPLETADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));

        assertThrows(TransicionInvalidaException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
        assertEquals(EstadoCita.COMPLETADA, original.getEstado());
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarFueraDeVentanaLanzaExcepcion() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        doThrow(new CancelacionNoPermitidaException("Fuera de la ventana"))
                .when(ventanaCancelacionValidator).validar(original.getFechaHora());

        assertThrows(CancelacionNoPermitidaException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, original.getEstado());
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarAFechaPasadaNoCancelaLaOriginal() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);

        assertThrows(FechaInvalidaException.class,
                () -> citaService.reprogramarCita(idCita, ayer, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, original.getEstado());
        verify(citaRepository, never()).save(any());
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarAHorarioNoDisponibleNoCancelaLaOriginal() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        when(disponibilidadService.calcularDisponibilidad(doctorId, nuevaFecha.toLocalDate()))
                .thenReturn(List.of());

        assertThrows(HorarioNoDisponibleException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, original.getEstado());
        verify(citaRepository, never()).save(any());
        verify(citaRepository, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarSiOtroPacienteLaTomaPrimeroLanzaCitaConflictException() {
        Cita original = crearCitaOriginal(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(original));
        marcarDisponible(nuevaFecha);
        when(citaRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("Violacion de UNIQUE"));

        assertThrows(CitaConflictException.class,
                () -> citaService.reprogramarCita(idCita, nuevaFecha, usuarioId));
    }

    // ---------- Acciones del doctor ----------

    @Test
    void confirmarCitaPendienteLaDejaConfirmada() {
        Cita cita = crearCita(EstadoCita.PENDIENTE);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        Cita confirmada = citaService.confirmarCita(idCita, usuarioId);

        assertEquals(EstadoCita.CONFIRMADA, confirmada.getEstado());
        verify(autorizacionService).validarDoctorDeLaCita(usuarioId, cita);
        verify(citaRepository).save(cita);
        verify(eventPublisher).publishEvent(new CitaConfirmadaEvent(cita.getId()));
    }

    @Test
    void doctorAjenoNoPuedeConfirmarLaCita() {
        Cita cita = crearCita(EstadoCita.PENDIENTE);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        doThrow(new AccesoDenegadoException("Esta cita no pertenece a tu agenda"))
                .when(autorizacionService).validarDoctorDeLaCita(usuarioId, cita);

        assertThrows(AccesoDenegadoException.class, () -> citaService.confirmarCita(idCita, usuarioId));
        assertEquals(EstadoCita.PENDIENTE, cita.getEstado());
        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(CitaConfirmadaEvent.class));
    }

    @Test
    void confirmarCitaYaConfirmadaLanzaExcepcion() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        assertThrows(TransicionInvalidaException.class, () -> citaService.confirmarCita(idCita, usuarioId));
        verify(citaRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(CitaConfirmadaEvent.class));
    }

    @Test
    void confirmarCitaInexistenteLanzaExcepcion() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> citaService.confirmarCita(idCita, usuarioId));
    }

    private Cita crearCitaQueYaOcurrio() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        cita.setFechaHora(LocalDateTime.now().minusHours(1));
        return cita;
    }

    @Test
    void completarCitaQueYaOcurrioLaDejaCompletada() {
        Cita cita = crearCitaQueYaOcurrio();
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        Cita completada = citaService.completarCita(idCita, usuarioId);

        assertEquals(EstadoCita.COMPLETADA, completada.getEstado());
        verify(citaRepository).save(cita);
    }

    @Test
    void completarCitaQueAunNoOcurreLanzaExcepcion() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        assertThrows(TransicionInvalidaException.class, () -> citaService.completarCita(idCita, usuarioId));
        assertEquals(EstadoCita.CONFIRMADA, cita.getEstado());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void doctorAjenoNoPuedeCompletarLaCita() {
        Cita cita = crearCitaQueYaOcurrio();
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));
        doThrow(new AccesoDenegadoException("Esta cita no pertenece a tu agenda"))
                .when(autorizacionService).validarDoctorDeLaCita(usuarioId, cita);

        assertThrows(AccesoDenegadoException.class, () -> citaService.completarCita(idCita, usuarioId));
        verify(citaRepository, never()).save(any());
    }

    @Test
    void marcarNoAsistioEnCitaQueYaOcurrioLaDejaNoAsistio() {
        Cita cita = crearCitaQueYaOcurrio();
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        Cita noAsistida = citaService.marcarNoAsistio(idCita, usuarioId);

        assertEquals(EstadoCita.NO_ASISTIO, noAsistida.getEstado());
    }

    @Test
    void marcarNoAsistioEnCitaQueAunNoOcurreLanzaExcepcion() {
        Cita cita = crearCita(EstadoCita.CONFIRMADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        assertThrows(TransicionInvalidaException.class, () -> citaService.marcarNoAsistio(idCita, usuarioId));
        verify(citaRepository, never()).save(any());
    }

    // ---------- Mis citas ----------

    @Test
    void listarCitasPacienteDevuelveLaPaginaDeSuPaciente() {
        Paciente paciente = new Paciente();
        paciente.setId(UUID.randomUUID());
        Pageable pageable = PageRequest.of(0, 10);
        Page<Cita> pagina = new PageImpl<>(List.of(crearCita(EstadoCita.PENDIENTE)), pageable, 1);
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(paciente));
        when(citaRepository.findByPacienteId(paciente.getId(), pageable)).thenReturn(pagina);

        Page<Cita> resultado = citaService.listarCitasPaciente(usuarioId, pageable);

        assertSame(pagina, resultado);
    }

    @Test
    void listarCitasSinPacienteAsociadoLanzaExcepcion() {
        when(pacienteRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.listarCitasPaciente(usuarioId, PageRequest.of(0, 10)));
    }

    // ---------- Agenda del doctor ----------

    private final LocalDate lunes = LocalDate.of(2026, 10, 5);

    @Test
    void agendaDevuelveLasCitasDelDoctorEnElRango() {
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        List<Cita> citas = List.of(crearCita(EstadoCita.CONFIRMADA));
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));
        when(citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                doctorId, lunes.atStartOfDay(), lunes.plusDays(4).atTime(LocalTime.MAX), EstadoCita.CANCELADA))
                .thenReturn(citas);

        List<Cita> agenda = citaService.listarAgendaDoctor(usuarioId, lunes, lunes.plusDays(4));

        assertSame(citas, agenda);
    }

    @Test
    void agendaConRangoInvertidoLanzaExcepcion() {
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));

        assertThrows(FechaInvalidaException.class,
                () -> citaService.listarAgendaDoctor(usuarioId, lunes, lunes.minusDays(1)));
    }

    @Test
    void agendaConRangoMayorA31DiasLanzaExcepcion() {
        Doctor doctor = new Doctor();
        doctor.setId(doctorId);
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(doctor));

        assertThrows(FechaInvalidaException.class,
                () -> citaService.listarAgendaDoctor(usuarioId, lunes, lunes.plusDays(31)));
    }

    @Test
    void agendaDeUsuarioQueNoEsDoctorLanzaExcepcion() {
        when(doctorRepository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> citaService.listarAgendaDoctor(usuarioId, lunes, lunes));
    }
}
