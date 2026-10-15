package com.citas.medicas.scheduler;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.EstadoNotificacion;
import com.citas.medicas.entity.Notificacion;
import com.citas.medicas.entity.TipoNotificacion;
import com.citas.medicas.notification.NotificacionService;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionSchedulerTest {

    private static final int MAX_INTENTOS = 3;
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private NotificacionRepository notificacionRepository;

    @Mock
    private NotificacionService notificacionService;

    private NotificacionScheduler scheduler;

    @Captor
    private ArgumentCaptor<LocalDateTime> desdeCaptor;

    @Captor
    private ArgumentCaptor<LocalDateTime> hastaCaptor;

    @BeforeEach
    void setUp() {
        scheduler = new NotificacionScheduler(citaRepository, notificacionRepository, notificacionService, MAX_INTENTOS, RELOJ);
    }

    private Cita crearCita() {
        Cita cita = new Cita();
        cita.setId(UUID.randomUUID());
        return cita;
    }

    private Notificacion crearNotificacion() {
        Notificacion notificacion = new Notificacion();
        notificacion.setId(UUID.randomUUID());
        return notificacion;
    }

    // ---------- Recordatorios ----------

    @Test
    void buscaCitasConfirmadasSinRecordatorioEnLasProximas24Horas() {
        when(citaRepository.buscarSinNotificacion(any(), any(), any(), any())).thenReturn(List.of());

        scheduler.revisarRecordatorios();

        verify(citaRepository).buscarSinNotificacion(eq(EstadoCita.CONFIRMADA), desdeCaptor.capture(),
                hastaCaptor.capture(), eq(TipoNotificacion.RECORDATORIO));
        assertEquals(LocalDateTime.of(2026, 3, 2, 10, 0), desdeCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 3, 3, 10, 0), hastaCaptor.getValue());
    }

    @Test
    void enviaUnRecordatorioPorCadaCitaEncontrada() {
        Cita primera = crearCita();
        Cita segunda = crearCita();
        when(citaRepository.buscarSinNotificacion(any(), any(), any(), any())).thenReturn(List.of(primera, segunda));

        scheduler.revisarRecordatorios();

        verify(notificacionService).enviarRecordatorio(primera.getId());
        verify(notificacionService).enviarRecordatorio(segunda.getId());
    }

    @Test
    void unErrorEnUnaCitaNoDetieneLasDemas() {
        Cita primera = crearCita();
        Cita segunda = crearCita();
        when(citaRepository.buscarSinNotificacion(any(), any(), any(), any())).thenReturn(List.of(primera, segunda));
        doThrow(new RuntimeException("Error inesperado"))
                .when(notificacionService).enviarRecordatorio(primera.getId());

        scheduler.revisarRecordatorios();

        verify(notificacionService).enviarRecordatorio(segunda.getId());
    }

    @Test
    void sinCitasNoEnviaNada() {
        when(citaRepository.buscarSinNotificacion(any(), any(), any(), any())).thenReturn(List.of());

        scheduler.revisarRecordatorios();

        verify(notificacionService, never()).enviarRecordatorio(any());
    }

    // ---------- Reintentos ----------

    @Test
    void buscaFallidasConMenosDelMaximoDeIntentos() {
        when(notificacionRepository.findByEstadoAndIntentosLessThan(any(), eq(MAX_INTENTOS))).thenReturn(List.of());

        scheduler.reintentarFallidas();

        verify(notificacionRepository).findByEstadoAndIntentosLessThan(EstadoNotificacion.FALLIDA, MAX_INTENTOS);
    }

    @Test
    void reintentaCadaNotificacionFallidaAunqueUnaFalle() {
        Notificacion primera = crearNotificacion();
        Notificacion segunda = crearNotificacion();
        when(notificacionRepository.findByEstadoAndIntentosLessThan(EstadoNotificacion.FALLIDA, MAX_INTENTOS))
                .thenReturn(List.of(primera, segunda));
        doThrow(new RuntimeException("Error inesperado"))
                .when(notificacionService).reintentar(primera.getId());

        scheduler.reintentarFallidas();

        verify(notificacionService).reintentar(segunda.getId());
    }
}
