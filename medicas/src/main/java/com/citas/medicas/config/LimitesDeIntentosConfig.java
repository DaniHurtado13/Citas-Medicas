package com.citas.medicas.config;

import com.citas.medicas.security.LimitadorDeIntentos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class LimitesDeIntentosConfig {

    private final Clock reloj = Clock.systemUTC();

    @Bean
    public LimitadorDeIntentos limitadorLoginPorIp(
            @Value("${citas.seguridad.login.max-por-ip}") int maximo,
            @Value("${citas.seguridad.login.ventana-por-ip}") Duration ventana) {
        return new LimitadorDeIntentos(maximo, ventana, reloj);
    }

    @Bean
    public LimitadorDeIntentos limitadorRegistroPorIp(
            @Value("${citas.seguridad.registro.max-por-ip}") int maximo,
            @Value("${citas.seguridad.registro.ventana-por-ip}") Duration ventana) {
        return new LimitadorDeIntentos(maximo, ventana, reloj);
    }

    @Bean
    public LimitadorDeIntentos limitadorReenvioPorIp(
            @Value("${citas.seguridad.reenvio.max-por-ip}") int maximo,
            @Value("${citas.seguridad.reenvio.ventana-por-ip}") Duration ventana) {
        return new LimitadorDeIntentos(maximo, ventana, reloj);
    }

    @Bean
    public LimitadorDeIntentos limitadorRecuperacionPorIp(
            @Value("${citas.seguridad.recuperacion.max-por-ip}") int maximo,
            @Value("${citas.seguridad.recuperacion.ventana-por-ip}") Duration ventana) {
        return new LimitadorDeIntentos(maximo, ventana, reloj);
    }

    @Bean
    public LimitadorDeIntentos limitadorFallosPorCuenta(
            @Value("${citas.seguridad.login.max-fallos-por-cuenta}") int maximo,
            @Value("${citas.seguridad.login.ventana-por-cuenta}") Duration ventana) {
        return new LimitadorDeIntentos(maximo, ventana, reloj);
    }
}
