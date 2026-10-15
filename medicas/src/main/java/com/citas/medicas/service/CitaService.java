package com.citas.medicas.service;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.evento.CitaCanceladaPorConsultorioEvent;
import com.citas.medicas.evento.CitaConfirmadaEvent;
import com.citas.medicas.exception.AccesoDenegadoException;
import com.citas.medicas.exception.CancelacionNoPermitidaException;
import com.citas.medicas.exception.CitaConflictException;
import com.citas.medicas.exception.FechaInvalidaException;
import com.citas.medicas.exception.HorarioNoDisponibleException;
import com.citas.medicas.exception.RecursoNoEncontradoException;
import com.citas.medicas.exception.TransicionInvalidaException;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.DoctorRepository;
import com.citas.medicas.repository.PacienteRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class CitaService {

    private static final int DIAS_MAXIMOS_AGENDA = 31;

    private final CitaRepository citaRepository;
    private final VentanaCancelacionValidator ventanaCancelacionValidator;
    private final PacienteRepository pacienteRepository;
    private final DoctorRepository doctorRepository;
    private final DisponibilidadService disponibilidadService;
    private final AutorizacionService autorizacionService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LimiteDeReservasValidator limiteDeReservasValidator;
    private final Clock reloj;

    public CitaService(CitaRepository citaRepository,
                       VentanaCancelacionValidator ventanaCancelacionValidator,
                       PacienteRepository pacienteRepository,
                       DoctorRepository doctorRepository,
                       DisponibilidadService disponibilidadService,
                       AutorizacionService autorizacionService,
                       ApplicationEventPublisher applicationEventPublisher,
                       LimiteDeReservasValidator limiteDeReservasValidator,
                       Clock reloj) {
        this.citaRepository = citaRepository;
        this.ventanaCancelacionValidator = ventanaCancelacionValidator;
        this.pacienteRepository = pacienteRepository;
        this.doctorRepository = doctorRepository;
        this.disponibilidadService = disponibilidadService;
        this.autorizacionService = autorizacionService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.limiteDeReservasValidator = limiteDeReservasValidator;
        this.reloj = reloj;
    }

    @Transactional
    public Cita reservarCita(UUID usuarioId, UUID doctorId, LocalDateTime fechaHora, String motivoConsulta) {

        Paciente paciente = pacienteRepository.findConBloqueoByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado"));

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));

        validarFechaFutura(fechaHora);
        limiteDeReservasValidator.validar(paciente.getId(), doctorId);
        validarDisponibilidad(doctorId, fechaHora);

        return guardarNuevaCita(paciente, doctor, fechaHora, motivoConsulta, null);
    }

    @Transactional
    public void cancelarCita(UUID idCita, UUID usuarioId) {
        Cita cita = buscarCita(idCita);

        boolean esPaciente = autorizacionService.esPacienteDeLaCita(usuarioId, cita);
        if (!esPaciente && !autorizacionService.esDoctorDeLaCita(usuarioId, cita)) {
            throw new AccesoDenegadoException("Esta cita no te pertenece");
        }

        TransicionEstadoCita.validar(cita.getEstado(), EstadoCita.CANCELADA);
        if (esPaciente) {
            validarCancelacionPorPaciente(cita);
        }

        cita.setEstado(EstadoCita.CANCELADA);
        citaRepository.save(cita);

        if (!esPaciente) {
            applicationEventPublisher.publishEvent(new CitaCanceladaPorConsultorioEvent(cita.getId()));
        }
    }


    @Transactional
    public int cancelarCitasFuturasDelDoctor(UUID doctorId) {
        List<Cita> futuras = citaRepository.findByDoctorIdAndEstadoInAndFechaHoraAfter(
                doctorId, List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA), LocalDateTime.now(reloj));

        for (Cita cita : futuras) {
            TransicionEstadoCita.validar(cita.getEstado(), EstadoCita.CANCELADA);
            cita.setEstado(EstadoCita.CANCELADA);
            citaRepository.save(cita);
            applicationEventPublisher.publishEvent(new CitaCanceladaPorConsultorioEvent(cita.getId()));
        }
        return futuras.size();
    }

    @Transactional
    public Cita reprogramarCita(UUID citaId, LocalDateTime nuevaFechaHora, UUID usuarioId) {
        Cita original = buscarCita(citaId);

        autorizacionService.validarPacienteDeLaCita(usuarioId, original);
        TransicionEstadoCita.validar(original.getEstado(), EstadoCita.CANCELADA);
        ventanaCancelacionValidator.validar(original.getFechaHora());
        validarFechaFutura(nuevaFechaHora);
        validarDisponibilidad(original.getDoctor().getId(), nuevaFechaHora);

        original.setEstado(EstadoCita.CANCELADA);
        citaRepository.save(original);

        return guardarNuevaCita(original.getPaciente(), original.getDoctor(), nuevaFechaHora,
                original.getMotivoConsulta(), original);
    }

    @Transactional
    public Cita confirmarCita(UUID citaId, UUID usuarioId) {
        Cita cita = buscarCita(citaId);
        autorizacionService.validarDoctorDeLaCita(usuarioId, cita);

        Cita confirmada = cambiarEstado(cita, EstadoCita.CONFIRMADA);
        applicationEventPublisher.publishEvent(new CitaConfirmadaEvent(confirmada.getId()));

        return confirmada;
    }

    @Transactional
    public Cita completarCita(UUID citaId, UUID usuarioId) {
        Cita cita = buscarCita(citaId);
        autorizacionService.validarDoctorDeLaCita(usuarioId, cita);
        validarQueYaOcurrio(cita, EstadoCita.COMPLETADA);
        return cambiarEstado(cita, EstadoCita.COMPLETADA);
    }

    @Transactional
    public Cita marcarNoAsistio(UUID citaId, UUID usuarioId) {
        Cita cita = buscarCita(citaId);
        autorizacionService.validarDoctorDeLaCita(usuarioId, cita);
        validarQueYaOcurrio(cita, EstadoCita.NO_ASISTIO);
        return cambiarEstado(cita, EstadoCita.NO_ASISTIO);
    }

    private void validarCancelacionPorPaciente(Cita cita) {
        if (cita.getEstado() == EstadoCita.PENDIENTE) {
            throw new CancelacionNoPermitidaException(
                    "No puedes cancelar una cita que aún no ha sido confirmada");
        }
        ventanaCancelacionValidator.validar(cita.getFechaHora());
    }

    private void validarFechaFutura(LocalDateTime fechaHora) {
        if (fechaHora.isBefore(LocalDateTime.now(reloj))) {
            throw new FechaInvalidaException("No se puede agendar una cita a una fecha pasada");
        }
    }

    private void validarDisponibilidad(UUID doctorId, LocalDateTime fechaHora) {
        boolean disponible = disponibilidadService
                .calcularDisponibilidad(doctorId, fechaHora.toLocalDate())
                .contains(fechaHora);

        if (!disponible) {
            throw new HorarioNoDisponibleException("El horario solicitado no está disponible para este doctor");
        }
    }

    private Cita guardarNuevaCita(Paciente paciente, Doctor doctor, LocalDateTime fechaHora,
                                  String motivoConsulta, Cita citaOrigen) {
        Cita cita = new Cita();
        cita.setPaciente(paciente);
        cita.setDoctor(doctor);
        cita.setFechaHora(fechaHora);
        cita.setMotivoConsulta(motivoConsulta);
        cita.setCitaOrigen(citaOrigen);
        cita.setEstado(EstadoCita.PENDIENTE);

        try {
            citaRepository.saveAndFlush(cita);
        } catch (DataIntegrityViolationException ex) {
            throw new CitaConflictException("El doctor ya tiene una cita en ese horario");
        }

        return cita;
    }

    private Cita buscarCita(UUID id) {
        return citaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La cita no se encontro"));
    }

    private void validarQueYaOcurrio(Cita cita, EstadoCita nuevoEstado) {
        if (cita.getFechaHora().isAfter(LocalDateTime.now(reloj))) {
            throw new TransicionInvalidaException(
                    "No se puede marcar como " + nuevoEstado + " una cita que aún no ha ocurrido");
        }
    }

    private Cita cambiarEstado(Cita cita, EstadoCita nuevoEstado) {
        TransicionEstadoCita.validar(cita.getEstado(), nuevoEstado);
        cita.setEstado(nuevoEstado);
        citaRepository.save(cita);

        return cita;
    }

    @Transactional(readOnly = true)
    public Page<Cita> listarCitasPaciente(UUID usuarioId, Pageable pageable) {
        Paciente paciente = pacienteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado"));

        return citaRepository.findByPacienteId(paciente.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Cita> listarAgendaDoctor(UUID usuarioId, LocalDate desde, LocalDate hasta) {
        Doctor doctor = doctorRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Doctor no encontrado"));

        if (hasta.isBefore(desde)) {
            throw new FechaInvalidaException("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) >= DIAS_MAXIMOS_AGENDA) {
            throw new FechaInvalidaException("La agenda se puede consultar por máximo " + DIAS_MAXIMOS_AGENDA + " días");
        }

        return citaRepository.findByDoctorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                doctor.getId(), desde.atStartOfDay(), hasta.atTime(LocalTime.MAX), EstadoCita.CANCELADA);
    }
}