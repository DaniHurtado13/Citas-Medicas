package com.citas.medicas.exception;

import java.time.Duration;

public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosDeEspera;

    public DemasiadosIntentosException(String motivo, Duration espera) {
        super(motivo + " Intenta de nuevo en " + describir(espera) + ".");
        // Se redondea hacia arriba y nunca es cero: "Retry-After: 0" invitaría a reintentar de inmediato
        this.segundosDeEspera = Math.max(1, (espera.toMillis() + 999) / 1000);
    }

    public long getSegundosDeEspera() {
        return segundosDeEspera;
    }

    private static String describir(Duration espera) {
        long minutos = (espera.toSeconds() + 59) / 60;
        if (minutos <= 1) {
            return "1 minuto";
        }
        return minutos + " minutos";
    }
}
