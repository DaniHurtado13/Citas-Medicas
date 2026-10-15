package com.citas.medicas.config;

import com.citas.medicas.repository.UsuarioRepository;
import com.citas.medicas.security.JwtFilter;
import com.citas.medicas.security.JwtService;
import com.citas.medicas.security.LimitadorDeIntentos;
import com.citas.medicas.security.LimiteDePeticionesFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UsuarioRepository usuarioRepository,
            @Qualifier("limitadorLoginPorIp") LimitadorDeIntentos limitadorLogin,
            @Qualifier("limitadorRegistroPorIp") LimitadorDeIntentos limitadorRegistro,
            @Qualifier("limitadorReenvioPorIp") LimitadorDeIntentos limitadorReenvio,
            @Qualifier("limitadorRecuperacionPorIp") LimitadorDeIntentos limitadorRecuperacion) throws Exception {

        LimiteDePeticionesFilter limiteDePeticiones = new LimiteDePeticionesFilter(Map.of(
                "/api/auth/login", limitadorLogin,
                "/api/auth/demo", limitadorLogin,
                "/api/auth/registro", limitadorRegistro,
                "/api/auth/reenviar-verificacion", limitadorReenvio,
                "/api/auth/olvide-password", limitadorRecuperacion));

        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/login", "/api/auth/demo", "/api/auth/registro",
                                "/api/auth/verificar-email", "/api/auth/reenviar-verificacion",
                                "/api/auth/olvide-password", "/api/auth/restablecer-password").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                // El límite por IP va primero: una IP bloqueada no llega ni a validar el token
                .addFilterBefore(limiteDePeticiones, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtFilter(jwtService, usuarioRepository), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
