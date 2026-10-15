package com.citas.medicas.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;


public class LimitadorDeIntentos {

    private final int maxIntentos;
    private final Duration ventana;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public LimitadorDeIntentos(int maxIntentos, Duration ventana, Clock reloj) {
        this.maxIntentos = maxIntentos;
        this.ventana = ventana;
        this.reloj = reloj;
    }

    /** Registra un intento y dice si todavía está dentro del límite. */
    public boolean intentar(String clave) {
        Instant ahora = reloj.instant();

        // compute lee, calcula y guarda de forma atómica para esta clave:
        // dos peticiones simultáneas de la misma IP no pueden pisarse el contador
        Ventana actual = ventanas.compute(clave, (k, anterior) -> {
            if (anterior == null || anterior.vencida(ahora, ventana)) {
                return new Ventana(ahora, 1);
            }
            return new Ventana(anterior.inicio(), anterior.intentos() + 1);
        });

        return actual.intentos() <= maxIntentos;
    }

    /** Dice si la clave ya agotó sus intentos, sin contar uno nuevo. */
    public boolean bloqueado(String clave) {
        Ventana actual = ventanas.get(clave);
        return actual != null
                && !actual.vencida(reloj.instant(), ventana)
                && actual.intentos() >= maxIntentos;
    }

    public void reiniciar(String clave) {
        ventanas.remove(clave);
    }

    /** Cuánto falta para que venza la ventana de la clave (cero si no tiene). */
    public Duration tiempoRestante(String clave) {
        Ventana actual = ventanas.get(clave);
        if (actual == null) {
            return Duration.ZERO;
        }
        Duration restante = Duration.between(reloj.instant(), actual.inicio().plus(ventana));
        return restante.isNegative() ? Duration.ZERO : restante;
    }

    /** Borra las ventanas vencidas para que el mapa no crezca sin límite. Devuelve cuántas borró. */
    public int limpiarVencidas() {
        Instant ahora = reloj.instant();
        int antes = ventanas.size();
        // removeIf sobre la vista de valores de un ConcurrentHashMap borra de forma condicional:
        // si otro hilo acaba de renovar la ventana de esa clave, no la borra
        ventanas.values().removeIf(v -> v.vencida(ahora, ventana));
        return antes - ventanas.size();
    }

    int tamanio() {
        return ventanas.size();
    }

    private record Ventana(Instant inicio, int intentos) {

        boolean vencida(Instant ahora, Duration duracion) {
            return !ahora.isBefore(inicio.plus(duracion));
        }
    }
}
