package com.citas.medicas.service;

import com.citas.medicas.exception.CancelacionNoPermitidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class VentanaCancelacionValidator {

    private final int horasMinimas;
    private final Clock reloj;

    public VentanaCancelacionValidator(@Value("${citas.cancelacion.horas-minimas}") int horasMinimas,
                                       Clock reloj) {
        this.horasMinimas = horasMinimas;
        this.reloj = reloj;
    }

    public void validar(LocalDateTime fechaHoraCita){
        LocalDateTime momentoLimite = fechaHoraCita.minusHours(horasMinimas);

        if(LocalDateTime.now(reloj).isAfter(momentoLimite)) {
            throw new CancelacionNoPermitidaException("Solo puede cancelar con al menos " + horasMinimas + " horas de anticipación");
        }
    }
}
