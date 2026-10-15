package com.citas.medicas.notification;

import com.citas.medicas.evento.RecuperacionPasswordSolicitadaEvent;
import com.citas.medicas.evento.VerificacionEmailSolicitadaEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class CorreosDeCuenta {

    private final EmailService emailService;
    private final String frontUrl;
    private final Duration vigenciaVerificacion;
    private final Duration vigenciaRecuperacion;

    public CorreosDeCuenta(EmailService emailService,
                           @Value("${citas.front.url}") String frontUrl,
                           @Value("${citas.verificacion.vigencia}") Duration vigenciaVerificacion,
                           @Value("${citas.recuperacion.vigencia}") Duration vigenciaRecuperacion) {
        this.emailService = emailService;
        this.frontUrl = frontUrl.endsWith("/") ? frontUrl.substring(0, frontUrl.length() - 1) : frontUrl;
        this.vigenciaVerificacion = vigenciaVerificacion;
        this.vigenciaRecuperacion = vigenciaRecuperacion;
    }

    @TransactionalEventListener
    @Async
    public void alSolicitarVerificacion(VerificacionEmailSolicitadaEvent evento) {
        // Sin el nombre: lo escribe quien se registra, y podría usarlo para meter texto engañoso en un correo nuestro
        String cuerpo = """
                Hola,

                Para activar tu cuenta en Citas Médicas, confirma tu correo entrando a este enlace:

                %s

                El enlace vence en %d horas y sirve una sola vez.
                Si no creaste esta cuenta, ignora este mensaje.
                """.formatted(enlace("/verificar-email", evento.token()), vigenciaVerificacion.toHours());

        enviar(evento.usuarioId(), evento.email(), "Confirma tu correo", cuerpo);
    }

    @TransactionalEventListener
    @Async
    public void alSolicitarRecuperacion(RecuperacionPasswordSolicitadaEvent evento) {
        String cuerpo = """
                Hola,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta en Citas Médicas.
                Para elegir una nueva, entra a este enlace:

                %s

                El enlace vence en %d minutos y sirve una sola vez. Al cambiarla se cerrarán todas tus sesiones.
                Si no lo pediste tú, ignora este mensaje: tu contraseña no cambiará.
                """.formatted(enlace("/restablecer-password", evento.token()), vigenciaRecuperacion.toMinutes());

        enviar(evento.usuarioId(), evento.email(), "Restablece tu contraseña", cuerpo);
    }

    private String enlace(String pagina, String token) {
        return frontUrl + pagina + "?token=" + token;
    }

    private void enviar(UUID usuarioId, String para, String asunto, String cuerpo) {
        try {
            emailService.enviarTexto(para, asunto, cuerpo);
        } catch (MailException ex) {
            log.error("No se pudo enviar el correo '{}' del usuario {}", asunto, usuarioId, ex);
        }
    }
}
