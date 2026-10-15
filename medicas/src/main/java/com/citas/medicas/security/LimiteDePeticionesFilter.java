package com.citas.medicas.security;

import com.citas.medicas.exception.DemasiadosIntentosException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Limita cuántas peticiones puede hacer una misma IP a las rutas públicas sensibles (login y entrada de demo, registro y las que envían correos: reenvío de verificación y olvido de contraseña).
 */
public class LimiteDePeticionesFilter extends OncePerRequestFilter {

    private final Map<String, LimitadorDeIntentos> limitadoresPorRuta;

    public LimiteDePeticionesFilter(Map<String, LimitadorDeIntentos> limitadoresPorRuta) {
        this.limitadoresPorRuta = limitadoresPorRuta;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !limitadoresPorRuta.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String ruta = request.getRequestURI();
        LimitadorDeIntentos limitador = limitadoresPorRuta.get(ruta);

        String ip = request.getRemoteAddr();

        if (!limitador.intentar(ip)) {
            responderDemasiadasPeticiones(response, ruta, new DemasiadosIntentosException(
                    "Demasiadas solicitudes desde tu conexión.", limitador.tiempoRestante(ip)));
            return;
        }

        filterChain.doFilter(request, response);
    }


    private void responderDemasiadasPeticiones(HttpServletResponse response, String ruta,
                                               DemasiadosIntentosException ex) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosDeEspera()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"%s","instance":"%s"}"""
                .formatted(ex.getMessage(), ruta));
    }
}
