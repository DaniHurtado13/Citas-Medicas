package com.citas.medicas.notification;

import com.citas.medicas.evento.RecuperacionPasswordSolicitadaEvent;
import com.citas.medicas.evento.VerificacionEmailSolicitadaEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CorreosDeCuentaTest {

    @Mock
    private EmailService emailService;

    private CorreosDeCuenta correos(String frontUrl) {
        return new CorreosDeCuenta(emailService, frontUrl, Duration.ofHours(24), Duration.ofMinutes(30));
    }

    private String cuerpoEnviado(String asunto) {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarTexto(eq("ana@correo.com"), eq(asunto), cuerpo.capture());
        return cuerpo.getValue();
    }

    @Test
    void elCorreoDeVerificacionLlevaElEnlaceAlFrontConElToken() {
        correos("https://citas.duckdns.org/").alSolicitarVerificacion(
                new VerificacionEmailSolicitadaEvent(UUID.randomUUID(), "ana@correo.com", "abc_123-XYZ"));

        String cuerpo = cuerpoEnviado("Confirma tu correo");
        assertTrue(cuerpo.contains("https://citas.duckdns.org/verificar-email?token=abc_123-XYZ"));
        assertTrue(cuerpo.startsWith("Hola,"));
        assertTrue(cuerpo.contains("24 horas"));
    }

    @Test
    void elCorreoDeRecuperacionLlevaSuPropioEnlaceYVence30Minutos() {
        correos("https://citas.duckdns.org").alSolicitarRecuperacion(
                new RecuperacionPasswordSolicitadaEvent(UUID.randomUUID(), "ana@correo.com", "abc_123-XYZ"));

        String cuerpo = cuerpoEnviado("Restablece tu contraseña");
        assertTrue(cuerpo.contains("https://citas.duckdns.org/restablecer-password?token=abc_123-XYZ"));
        assertTrue(cuerpo.contains("30 minutos"));
    }

    @Test
    void siElServidorDeCorreoFallaNoRompeNada() {
        doThrow(new MailSendException("SMTP caído")).when(emailService).enviarTexto(any(), any(), any());

        assertDoesNotThrow(() -> correos("http://localhost:4200").alSolicitarRecuperacion(
                new RecuperacionPasswordSolicitadaEvent(UUID.randomUUID(), "ana@correo.com", "abc")));
    }
}
