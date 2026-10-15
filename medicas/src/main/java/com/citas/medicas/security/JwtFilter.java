package com.citas.medicas.security;

import com.citas.medicas.entity.Usuario;
import com.citas.medicas.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    public JwtFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String encabezado = request.getHeader("Authorization");

        if(encabezado == null || !encabezado.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = encabezado.substring(7);
        Claims claims;

        try{
            claims = jwtService.validarYLeer(token);
        } catch (JwtException e) {
            filterChain.doFilter(request, response);
            return;
        }

        UUID usuarioId = UUID.fromString(claims.getSubject());
        String rol = claims.get("rol", String.class);
        Integer version = claims.get("ver", Integer.class);

        // La firma prueba que el token es auténtico; la base, que sigue vigente
        boolean vigente = version != null && usuarioRepository.findById(usuarioId)
                .filter(Usuario::getActivo)
                .filter(usuario -> usuario.getVersionSesion().equals(version))
                .isPresent();
        if (!vigente) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                usuarioId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);

        filterChain.doFilter(request, response);
    }
}
