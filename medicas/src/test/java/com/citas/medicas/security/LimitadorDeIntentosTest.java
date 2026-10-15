package com.citas.medicas.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitadorDeIntentosTest {

    private static final int MAXIMO = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);

    private RelojAjustable reloj;
    private LimitadorDeIntentos limitador;

    @BeforeEach
    void setUp() {
        reloj = new RelojAjustable(Instant.parse("2026-10-12T10:00:00Z"));
        limitador = new LimitadorDeIntentos(MAXIMO, VENTANA, reloj);
    }

    private void agotar(String clave) {
        for (int i = 0; i < MAXIMO; i++) {
            limitador.intentar(clave);
        }
    }

    @Test
    void permiteHastaElMaximoYRechazaElSiguiente() {
        for (int i = 1; i <= MAXIMO; i++) {
            assertTrue(limitador.intentar("1.1.1.1"), "El intento " + i + " debería permitirse");
        }

        assertFalse(limitador.intentar("1.1.1.1"));
    }

    @Test
    void lasClavesNoCompartenContador() {
        agotar("1.1.1.1");

        assertFalse(limitador.intentar("1.1.1.1"));
        assertTrue(limitador.intentar("2.2.2.2"));
    }

    @Test
    void sigueBloqueadoMientrasLaVentanaNoVence() {
        agotar("1.1.1.1");

        reloj.avanzar(VENTANA.minusSeconds(1));

        assertFalse(limitador.intentar("1.1.1.1"));
    }

    @Test
    void alVencerLaVentanaSeVuelveAPermitir() {
        agotar("1.1.1.1");
        assertFalse(limitador.intentar("1.1.1.1"));

        reloj.avanzar(VENTANA);

        assertTrue(limitador.intentar("1.1.1.1"));
    }

    @Test
    void variosHilosALaVezNoPierdenIntentos() throws Exception {
        int hilos = 50;
        ExecutorService corredores = Executors.newFixedThreadPool(hilos);
        CountDownLatch pistolaDeSalida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            resultados.add(corredores.submit(() -> {
                pistolaDeSalida.await();
                return limitador.intentar("1.1.1.1");
            }));
        }
        pistolaDeSalida.countDown();

        int permitidos = 0;
        for (Future<Boolean> resultado : resultados) {
            if (resultado.get(10, TimeUnit.SECONDS)) {
                permitidos++;
            }
        }
        corredores.shutdown();

        assertEquals(MAXIMO, permitidos);
    }

    @Test
    void bloqueadoNoCuentaUnIntentoNuevo() {
        for (int i = 0; i < MAXIMO - 1; i++) {
            limitador.intentar("ana@correo.com");
        }

        assertFalse(limitador.bloqueado("ana@correo.com"));
        assertFalse(limitador.bloqueado("ana@correo.com"), "Consultar no debe gastar intentos");

        limitador.intentar("ana@correo.com");
        assertTrue(limitador.bloqueado("ana@correo.com"));
    }

    @Test
    void bloqueadoTerminaAlVencerLaVentana() {
        agotar("ana@correo.com");

        reloj.avanzar(VENTANA);

        assertFalse(limitador.bloqueado("ana@correo.com"));
    }

    @Test
    void reiniciarBorraElContador() {
        agotar("ana@correo.com");

        limitador.reiniciar("ana@correo.com");

        assertFalse(limitador.bloqueado("ana@correo.com"));
        assertTrue(limitador.intentar("ana@correo.com"));
    }

    @Test
    void tiempoRestanteDescuentaLoQueYaPaso() {
        agotar("1.1.1.1");
        reloj.avanzar(Duration.ofMinutes(10));

        assertEquals(Duration.ofMinutes(5), limitador.tiempoRestante("1.1.1.1"));
        assertEquals(Duration.ZERO, limitador.tiempoRestante("clave-sin-intentos"));
    }

    @Test
    void limpiarBorraSoloLasVentanasVencidas() {
        limitador.intentar("vieja");
        reloj.avanzar(Duration.ofMinutes(10));
        limitador.intentar("nueva");
        reloj.avanzar(Duration.ofMinutes(6));

        int borradas = limitador.limpiarVencidas();

        assertEquals(1, borradas);
        assertEquals(1, limitador.tamanio());
        assertEquals(Duration.ofMinutes(9), limitador.tiempoRestante("nueva"));
    }

    /**
     * Un reloj que el test puede adelantar a voluntad. Clock.fixed no sirve: siempre marca la misma hora,
     * y el limitador guarda la referencia al reloj que recibió en el constructor.
     */
    private static class RelojAjustable extends Clock {

        private Instant ahora;

        RelojAjustable(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }
}
