package com.citas.medicas.notification;

import com.citas.medicas.entity.Cita;
import com.citas.medicas.entity.Doctor;
import com.citas.medicas.entity.EstadoCita;
import com.citas.medicas.entity.EstadoNotificacion;
import com.citas.medicas.entity.Notificacion;
import com.citas.medicas.entity.Paciente;
import com.citas.medicas.entity.TipoNotificacion;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.evento.CitaCanceladaPorConsultorioEvent;
import com.citas.medicas.evento.CitaConfirmadaEvent;
import com.citas.medicas.repository.CitaRepository;
import com.citas.medicas.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private NotificacionRepository notificacionRepository;

    @Mock
    private CitaRepository citaRepository;

    private NotificacionService notificacionService;

    @BeforeEach
    void crearServicio() {
        notificacionService = new NotificacionService(emailService, notificacionRepository, citaRepository, Clock.systemDefaultZone());
    }

    @Captor
    private ArgumentCaptor<Notificacion> notificacionCaptor;

    @Captor
    private ArgumentCaptor<String> cuerpoCaptor;

    private final UUID idCita = UUID.randomUUID();
    private final CitaConfirmadaEvent evento = new CitaConfirmadaEvent(idCita);

    private Cita crearCitaConfirmada() {
        Usuario usuario = new Usuario();
        usuario.setEmail("paciente@citas.dev");

        Paciente paciente = new Paciente();
        paciente.setNombre("Carlos Pérez");
        paciente.setUsuario(usuario);

        Doctor doctor = new Doctor();
        doctor.setNombre("Laura Gómez");
        doctor.setConsultorio("Consultorio 101");

        Cita cita = new Cita();
        cita.setId(idCita);
        cita.setPaciente(paciente);
        cita.setDoctor(doctor);
        cita.setEstado(EstadoCita.CONFIRMADA);
        cita.setFechaHora(LocalDateTime.of(2026, 10, 6, 11, 0));
        return cita;
    }

    @Test
    void confirmacionEnviadaQuedaComoEnviada() {
        Cita cita = crearCitaConfirmada();
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        notificacionService.alConfirmarCita(evento);

        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        Notificacion guardada = notificacionCaptor.getValue();
        assertSame(cita, guardada.getCita());
        assertEquals(TipoNotificacion.CONFIRMACION, guardada.getTipo());
        assertEquals(EstadoNotificacion.ENVIADA, guardada.getEstado());
    }

    @Test
    void elCorreoLlevaLosDatosDeLaCita() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(crearCitaConfirmada()));

        notificacionService.alConfirmarCita(evento);

        verify(emailService).enviarTexto(eq("paciente@citas.dev"), anyString(), cuerpoCaptor.capture());
        String cuerpo = cuerpoCaptor.getValue();
        assertTrue(cuerpo.contains("Carlos Pérez"));
        assertTrue(cuerpo.contains("Laura Gómez"));
        assertTrue(cuerpo.contains("Consultorio 101"));
        assertTrue(cuerpo.contains("martes 6 de octubre de 2026 a las 11:00"));
    }

    @Test
    void siElCorreoFallaQuedaComoFallidaSinLanzarExcepcion() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(crearCitaConfirmada()));
        doThrow(new MailSendException("SMTP caído"))
                .when(emailService).enviarTexto(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificacionService.alConfirmarCita(evento));

        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        assertEquals(EstadoNotificacion.FALLIDA, notificacionCaptor.getValue().getEstado());
    }

    @Test
    void aUnPacienteDeDemoNoSeLeEnviaYQuedaOmitida() {
        Cita cita = crearCitaConfirmada();
        cita.getPaciente().getUsuario().setDemo(true);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        notificacionService.alConfirmarCita(evento);

        verify(emailService, never()).enviarTexto(any(), any(), any());
        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        assertEquals(EstadoNotificacion.OMITIDA, notificacionCaptor.getValue().getEstado());
    }

    @Test
    void citaInexistenteNoEnviaNiGuardaNada() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        notificacionService.alConfirmarCita(evento);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }

    // ---------- Recordatorios ----------

    @Test
    void recordatorioDeCitaConfirmadaQuedaEnviado() {
        Cita cita = crearCitaConfirmada();
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        notificacionService.enviarRecordatorio(idCita);

        verify(emailService).enviarTexto(eq("paciente@citas.dev"), eq("Recordatorio de tu cita médica"), anyString());
        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        Notificacion guardada = notificacionCaptor.getValue();
        assertEquals(TipoNotificacion.RECORDATORIO, guardada.getTipo());
        assertEquals(EstadoNotificacion.ENVIADA, guardada.getEstado());
        assertEquals(cita.getFechaHora().minusHours(24), guardada.getFechaProgramada());
    }

    @Test
    void recordatorioDeCitaYaNoConfirmadaNoSeEnvia() {
        Cita cita = crearCitaConfirmada();
        cita.setEstado(EstadoCita.CANCELADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        notificacionService.enviarRecordatorio(idCita);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void recordatorioDeCitaInexistenteNoSeEnvia() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        notificacionService.enviarRecordatorio(idCita);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void siElRecordatorioFallaQuedaComoFallido() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(crearCitaConfirmada()));
        doThrow(new MailSendException("SMTP caído"))
                .when(emailService).enviarTexto(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> notificacionService.enviarRecordatorio(idCita));

        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        assertEquals(EstadoNotificacion.FALLIDA, notificacionCaptor.getValue().getEstado());
    }

    // ---------- Reintentos ----------

    private final UUID idNotificacion = UUID.randomUUID();

    private Notificacion crearFallida(TipoNotificacion tipo, Cita cita) {
        Notificacion notificacion = new Notificacion();
        notificacion.setId(idNotificacion);
        notificacion.setCita(cita);
        notificacion.setTipo(tipo);
        notificacion.setEstado(EstadoNotificacion.FALLIDA);
        notificacion.setIntentos(1);
        return notificacion;
    }

    private Cita crearCitaFutura() {
        Cita cita = crearCitaConfirmada();
        cita.setFechaHora(LocalDateTime.now().plusDays(1));
        return cita;
    }

    @Test
    void reintentoExitosoQuedaEnviadoYSumaUnIntento() {
        Notificacion fallida = crearFallida(TipoNotificacion.CONFIRMACION, crearCitaFutura());
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService).enviarTexto(eq("paciente@citas.dev"), eq("Tu cita médica fue confirmada"), anyString());
        assertEquals(EstadoNotificacion.ENVIADA, fallida.getEstado());
        assertEquals(2, fallida.getIntentos());
        verify(notificacionRepository).save(fallida);
    }

    @Test
    void reintentoArmaElCorreoSegunElTipo() {
        Notificacion fallida = crearFallida(TipoNotificacion.RECORDATORIO, crearCitaFutura());
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService).enviarTexto(anyString(), eq("Recordatorio de tu cita médica"), anyString());
    }

    @Test
    void reintentoQueVuelveAFallarSigueFallidoConUnIntentoMas() {
        Notificacion fallida = crearFallida(TipoNotificacion.CONFIRMACION, crearCitaFutura());
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));
        doThrow(new MailSendException("SMTP caído"))
                .when(emailService).enviarTexto(anyString(), anyString(), anyString());

        notificacionService.reintentar(idNotificacion);

        assertEquals(EstadoNotificacion.FALLIDA, fallida.getEstado());
        assertEquals(2, fallida.getIntentos());
    }

    @Test
    void reintentoDeCitaCanceladaQuedaOmitidoSinEnviar() {
        Cita cita = crearCitaFutura();
        cita.setEstado(EstadoCita.CANCELADA);
        Notificacion fallida = crearFallida(TipoNotificacion.CONFIRMACION, cita);
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        assertEquals(EstadoNotificacion.OMITIDA, fallida.getEstado());
        assertEquals(1, fallida.getIntentos());
    }

    @Test
    void reintentoDeCitaQueYaPasoQuedaOmitido() {
        Cita cita = crearCitaConfirmada();
        cita.setFechaHora(LocalDateTime.now().minusHours(1));
        Notificacion fallida = crearFallida(TipoNotificacion.RECORDATORIO, cita);
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        assertEquals(EstadoNotificacion.OMITIDA, fallida.getEstado());
    }

    @Test
    void reintentoDeAvisoDeCancelacionDeCitaCanceladaSeReenvia() {
        Cita cita = crearCitaFutura();
        cita.setEstado(EstadoCita.CANCELADA);
        Notificacion fallida = crearFallida(TipoNotificacion.CANCELACION, cita);
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService).enviarTexto(anyString(), eq("Tu cita médica fue cancelada"), anyString());
        assertEquals(EstadoNotificacion.ENVIADA, fallida.getEstado());
        assertEquals(2, fallida.getIntentos());
    }

    @Test
    void reintentoDeAvisoDeCancelacionDeCitaQueYaPasoQuedaOmitido() {
        Cita cita = crearCitaConfirmada();
        cita.setEstado(EstadoCita.CANCELADA);
        cita.setFechaHora(LocalDateTime.now().minusHours(1));
        Notificacion fallida = crearFallida(TipoNotificacion.CANCELACION, cita);
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(fallida));

        notificacionService.reintentar(idNotificacion);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        assertEquals(EstadoNotificacion.OMITIDA, fallida.getEstado());
    }

    // ---------- Cancelación por el doctor ----------

    @Test
    void avisoDeCancelacionQuedaEnviadoConSuTipoYTexto() {
        Cita cita = crearCitaConfirmada();
        cita.setEstado(EstadoCita.CANCELADA);
        when(citaRepository.findById(idCita)).thenReturn(Optional.of(cita));

        notificacionService.alCancelarCitaPorConsultorio(new CitaCanceladaPorConsultorioEvent(idCita));

        verify(emailService).enviarTexto(eq("paciente@citas.dev"), eq("Tu cita médica fue cancelada"),
                cuerpoCaptor.capture());
        assertTrue(cuerpoCaptor.getValue().contains("fue cancelada por el consultorio"));
        assertTrue(cuerpoCaptor.getValue().contains("martes 6 de octubre de 2026 a las 11:00"));
        verify(notificacionRepository, times(2)).save(notificacionCaptor.capture());
        assertEquals(TipoNotificacion.CANCELACION, notificacionCaptor.getValue().getTipo());
        assertEquals(EstadoNotificacion.ENVIADA, notificacionCaptor.getValue().getEstado());
    }

    @Test
    void avisoDeCancelacionDeCitaInexistenteNoEnviaNada() {
        when(citaRepository.findById(idCita)).thenReturn(Optional.empty());

        notificacionService.alCancelarCitaPorConsultorio(new CitaCanceladaPorConsultorioEvent(idCita));

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    void reintentoDeNotificacionQueYaNoEstaFallidaNoHaceNada() {
        Notificacion enviada = crearFallida(TipoNotificacion.CONFIRMACION, crearCitaFutura());
        enviada.setEstado(EstadoNotificacion.ENVIADA);
        when(notificacionRepository.findById(idNotificacion)).thenReturn(Optional.of(enviada));

        notificacionService.reintentar(idNotificacion);

        verify(emailService, never()).enviarTexto(anyString(), anyString(), anyString());
        verify(notificacionRepository, never()).save(any());
    }
}
