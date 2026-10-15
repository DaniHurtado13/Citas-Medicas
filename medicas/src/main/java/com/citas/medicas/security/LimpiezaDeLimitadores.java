package com.citas.medicas.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class LimpiezaDeLimitadores {

    private final List<LimitadorDeIntentos> limitadores;

    public LimpiezaDeLimitadores(List<LimitadorDeIntentos> limitadores) {
        this.limitadores = limitadores;
    }

    @Scheduled(cron = "${citas.seguridad.limpieza.cron}")
    public void limpiar() {
        int borradas = limitadores.stream().mapToInt(LimitadorDeIntentos::limpiarVencidas).sum();
        if (borradas > 0) {
            log.debug("Limpieza de limitadores: {} ventana(s) vencida(s) borradas", borradas);
        }
    }
}
