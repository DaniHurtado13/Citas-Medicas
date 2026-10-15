package com.citas.medicas.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String remitente;

    public EmailService(JavaMailSender mailSender,
                        @Value("${citas.mail.remitente}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    public void enviarTexto(String para, String asunto, String cuerpo) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(remitente);
        message.setTo(para);
        message.setSubject(asunto);
        message.setText(cuerpo);
        mailSender.send(message);
    }
}
