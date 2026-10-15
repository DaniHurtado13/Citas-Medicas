package com.citas.medicas.scheduler;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.EstadoNotificacion;
import com.citas.medicas.entity.Notificacion;
import com.citas.medicas.entity.TipoNotificacion;
import com.citas.medicas.notification.NotificacionService;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.NotificacionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class NotificacionScheduler {

    private final CitaRepository citaRepository;
    private final NotificacionRepository notificacionRepository;
    private final NotificacionService notificacionService;
    private final int maxIntentos;
    private final Clock reloj;

    public NotificacionScheduler(CitaRepository citaRepository,
                                 NotificacionRepository notificacionRepository,
                                 NotificacionService notificacionService,
                                 @Value("${citas.reintentos.max-intentos}") int maxIntentos,
                                 Clock reloj) {
        this.citaRepository = citaRepository;
        this.notificacionRepository = notificacionRepository;
        this.notificacionService = notificacionService;
        this.maxIntentos = maxIntentos;
        this.reloj = reloj;
    }

    @Scheduled(cron = "${citas.recordatorio.cron}")
    public void revisarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        List<Cita> pendientes = citaRepository.buscarSinNotificacion(
                EstadoCita.CONFIRMADA, ahora, ahora.plusHours(24), TipoNotificacion.RECORDATORIO);

        log.info("Revisando recordatorios: {} cita(s) sin recordatorio en las próximas 24 h", pendientes.size());

        for (Cita cita : pendientes) {
            try {
                notificacionService.enviarRecordatorio(cita.getId());
            } catch (RuntimeException ex) {
                log.error("Error procesando el recordatorio de la cita {}", cita.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "${citas.reintentos.cron}")
    public void reintentarFallidas() {
        List<Notificacion> fallidas = notificacionRepository
                .findByEstadoAndIntentosLessThan(EstadoNotificacion.FALLIDA, maxIntentos);

        log.info("Reintentando notificaciones: {} fallida(s) con menos de {} intentos", fallidas.size(), maxIntentos);

        for (Notificacion notificacion : fallidas) {
            try {
                notificacionService.reintentar(notificacion.getId());
            } catch (RuntimeException ex) {
                log.error("Error reintentando la notificación {}", notificacion.getId(), ex);
            }
        }
    }
}
