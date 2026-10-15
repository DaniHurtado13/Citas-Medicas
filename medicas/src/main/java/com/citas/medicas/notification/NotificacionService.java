package com.citas.medicas.notification;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.EstadoNotificacion;
import com.citas.medicas.entity.Notificacion;
import com.citas.medicas.entity.TipoNotificacion;
import com.citas.medicas.evento.CitaCanceladaPorConsultorioEvent;
import com.citas.medicas.evento.CitaConfirmadaEvent;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.NotificacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class NotificacionService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy 'a las' HH:mm", Locale.of("es"));

    private static final int HORAS_ANTES_RECORDATORIO = 24;

    private final EmailService emailService;
    private final NotificacionRepository notificacionRepository;
    private final CitaRepository citaRepository;
    private final Clock reloj;

    public NotificacionService(EmailService emailService,
                               NotificacionRepository notificacionRepository,
                               CitaRepository citaRepository,
                               Clock reloj) {
        this.emailService = emailService;
        this.notificacionRepository = notificacionRepository;
        this.citaRepository = citaRepository;
        this.reloj = reloj;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async
    public void alConfirmarCita(CitaConfirmadaEvent evento) {
        log.info("Procesando confirmación de la cita {}", evento.citaId());

        Optional<Cita> encontrada = citaRepository.findById(evento.citaId());
        if (encontrada.isEmpty()) {
            log.warn("No se envió la confirmación: la cita {} no existe", evento.citaId());
            return;
        }

        registrarYEnviar(encontrada.get(), TipoNotificacion.CONFIRMACION, LocalDateTime.now(reloj));
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async
    public void alCancelarCitaPorConsultorio(CitaCanceladaPorConsultorioEvent evento) {
        log.info("Procesando aviso de cancelación de la cita {}", evento.citaId());

        Optional<Cita> encontrada = citaRepository.findById(evento.citaId());
        if (encontrada.isEmpty()) {
            log.warn("No se envió el aviso de cancelación: la cita {} no existe", evento.citaId());
            return;
        }

        registrarYEnviar(encontrada.get(), TipoNotificacion.CANCELACION, LocalDateTime.now(reloj));
    }

    @Transactional
    public void enviarRecordatorio(UUID citaId) {
        Optional<Cita> encontrada = citaRepository.findById(citaId);
        if (encontrada.isEmpty() || encontrada.get().getEstado() != EstadoCita.CONFIRMADA) {
            log.warn("No se envió el recordatorio: la cita {} no existe o ya no está confirmada", citaId);
            return;
        }
        Cita cita = encontrada.get();
        registrarYEnviar(cita, TipoNotificacion.RECORDATORIO,
                cita.getFechaHora().minusHours(HORAS_ANTES_RECORDATORIO));
    }

    @Transactional
    public void reintentar(UUID notificacionId) {
        Optional<Notificacion> encontrada = notificacionRepository.findById(notificacionId);
        if (encontrada.isEmpty() || encontrada.get().getEstado() != EstadoNotificacion.FALLIDA) {
            return;
        }
        Notificacion notificacion = encontrada.get();
        Cita cita = notificacion.getCita();

        if (!sigueVigente(notificacion.getTipo(), cita)) {
            log.info("Notificación {} ({}) omitida: la cita {} cambió de estado o ya pasó",
                    notificacion.getId(), notificacion.getTipo(), cita.getId());
            notificacion.setEstado(EstadoNotificacion.OMITIDA);
            notificacionRepository.save(notificacion);
            return;
        }

        notificacion.setIntentos(notificacion.getIntentos() + 1);
        intentarEnvio(notificacion);
    }


    private boolean sigueVigente(TipoNotificacion tipo, Cita cita) {
        EstadoCita estadoEsperado = switch (tipo) {
            case CONFIRMACION, RECORDATORIO -> EstadoCita.CONFIRMADA;
            case CANCELACION -> EstadoCita.CANCELADA;
        };
        return cita.getEstado() == estadoEsperado && cita.getFechaHora().isAfter(LocalDateTime.now(reloj));
    }

    private void registrarYEnviar(Cita cita, TipoNotificacion tipo, LocalDateTime fechaProgramada) {
        Notificacion notificacion = new Notificacion();
        notificacion.setCita(cita);
        notificacion.setTipo(tipo);
        notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        notificacion.setFechaProgramada(fechaProgramada);
        notificacion.setIntentos(1);
        notificacionRepository.save(notificacion);

        intentarEnvio(notificacion);
    }

    private void intentarEnvio(Notificacion notificacion) {
        Cita cita = notificacion.getCita();
        // Nadie lee la bandeja de una cuenta de demo: enviarle solo generaría rebotes
        if (cita.getPaciente().getUsuario().getDemo()) {
            notificacion.setEstado(EstadoNotificacion.OMITIDA);
            notificacionRepository.save(notificacion);
            return;
        }

        Correo correo = switch (notificacion.getTipo()) {
            case CONFIRMACION -> armarConfirmacion(cita);
            case RECORDATORIO -> armarRecordatorio(cita);
            case CANCELACION -> armarCancelacion(cita);
        };

        try {
            emailService.enviarTexto(cita.getPaciente().getUsuario().getEmail(), correo.asunto(), correo.cuerpo());
            notificacion.setEstado(EstadoNotificacion.ENVIADA);
        } catch (MailException ex) {
            log.error("No se pudo enviar la notificación {} de la cita {} (intento {})",
                    notificacion.getTipo(), cita.getId(), notificacion.getIntentos(), ex);
            notificacion.setEstado(EstadoNotificacion.FALLIDA);
        }

        notificacionRepository.save(notificacion);
    }

    private Correo armarConfirmacion(Cita cita) {
        Doctor doctor = cita.getDoctor();
        String cuerpo = """
                Hola %s,

                Tu cita con %s fue confirmada.

                Fecha: %s
                Consultorio: %s

                Si no puedes asistir, recuerda cancelarla con anticipación.
                """.formatted(
                cita.getPaciente().getNombre(),
                doctor.getNombre(),
                cita.getFechaHora().format(FORMATO_FECHA),
                doctor.getConsultorio());

        return new Correo("Tu cita médica fue confirmada", cuerpo);
    }

    private Correo armarRecordatorio(Cita cita) {
        Doctor doctor = cita.getDoctor();
        String cuerpo = """
                Hola %s,

                Te recordamos tu cita con %s.

                Fecha: %s
                Consultorio: %s

                Te esperamos.
                """.formatted(
                cita.getPaciente().getNombre(),
                doctor.getNombre(),
                cita.getFechaHora().format(FORMATO_FECHA),
                doctor.getConsultorio());

        return new Correo("Recordatorio de tu cita médica", cuerpo);
    }

    private Correo armarCancelacion(Cita cita) {
        Doctor doctor = cita.getDoctor();
        String cuerpo = """
                Hola %s,

                Lamentamos informarte que tu cita con %s fue cancelada por el consultorio.

                Fecha: %s
                Consultorio: %s

                Puedes reservar un nuevo horario cuando quieras.
                """.formatted(
                cita.getPaciente().getNombre(),
                doctor.getNombre(),
                cita.getFechaHora().format(FORMATO_FECHA),
                doctor.getConsultorio());

        return new Correo("Tu cita médica fue cancelada", cuerpo);
    }

    private record Correo(String asunto, String cuerpo) {
    }
}
