package com.citas.medicas.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteDePeticionesFilterTest {

    private static final int MAXIMO = 2;

    private LimiteDePeticionesFilter filtro;

    @BeforeEach
    void setUp() {
        LimitadorDeIntentos limitador = new LimitadorDeIntentos(MAXIMO, Duration.ofMinutes(15), Clock.systemUTC());
        filtro = new LimiteDePeticionesFilter(Map.of("/api/auth/login", limitador));
    }

    /** Ejecuta el filtro y devuelve la respuesta; la cadena registra si la petición llegó al controller. */
    private Resultado enviar(String metodo, String ruta, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, ruta);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        filtro.doFilter(request, response, cadena);

        return new Resultado(response, cadena.getRequest() != null);
    }

    private record Resultado(MockHttpServletResponse respuesta, boolean llegoAlController) {
    }

    @Test
    void dejaPasarHastaElMaximoYLuegoResponde429() throws Exception {
        for (int i = 0; i < MAXIMO; i++) {
            assertTrue(enviar("POST", "/api/auth/login", "1.1.1.1").llegoAlController());
        }

        Resultado bloqueado = enviar("POST", "/api/auth/login", "1.1.1.1");

        assertEquals(429, bloqueado.respuesta().getStatus());
        assertEquals(false, bloqueado.llegoAlController());
        assertNotNull(bloqueado.respuesta().getHeader("Retry-After"));
        assertEquals("application/problem+json", bloqueado.respuesta().getContentType().split(";")[0]);
        assertTrue(bloqueado.respuesta().getContentAsString().contains("\"status\":429"));
    }

    @Test
    void otraIpNoComparteElLimite() throws Exception {
        for (int i = 0; i <= MAXIMO; i++) {
            enviar("POST", "/api/auth/login", "1.1.1.1");
        }

        assertTrue(enviar("POST", "/api/auth/login", "2.2.2.2").llegoAlController());
    }

    @Test
    void lasRutasNoVigiladasYLosGetNoSeLimitan() throws Exception {
        for (int i = 0; i <= MAXIMO; i++) {
            enviar("POST", "/api/auth/login", "1.1.1.1");
        }

        Resultado otraRuta = enviar("POST", "/api/citas", "1.1.1.1");
        Resultado get = enviar("GET", "/api/auth/login", "1.1.1.1");

        assertTrue(otraRuta.llegoAlController());
        assertTrue(get.llegoAlController());
        assertNull(otraRuta.respuesta().getHeader("Retry-After"));
    }
}
