package com.citas.medicas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${citas.zona-horaria}") ZoneId zonaHoraria) {
        return Clock.system(zonaHoraria);
    }
}
