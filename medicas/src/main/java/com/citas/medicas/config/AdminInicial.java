package com.citas.medicas.config;

import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.repository.UsuarioRepository;
import com.citas.medicas.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AdminInicial implements ApplicationRunner {

    private static final int MIN_CARACTERES = 8;
    private static final int MAX_BYTES_BCRYPT = 72;

    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;
    private final String email;
    private final String password;
    private final TipoDocumento tipoDocumento;
    private final String numeroDocumento;

    public AdminInicial(UsuarioRepository usuarioRepository,
                        UsuarioService usuarioService,
                        @Value("${citas.admin-inicial.email}") String email,
                        @Value("${citas.admin-inicial.password}") String password,
                        @Value("${citas.admin-inicial.tipo-documento}") TipoDocumento tipoDocumento,
                        @Value("${citas.admin-inicial.numero-documento}") String numeroDocumento) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioService = usuarioService;
        this.email = Usuario.normalizarEmail(email);
        this.password = password;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)) {
            return;
        }
        if (email.isEmpty() || password.isEmpty() || numeroDocumento.isEmpty()) {
            log.warn("No existe ningún administrador. Define ADMIN_EMAIL, ADMIN_PASSWORD y ADMIN_DOCUMENTO para crear el primero.");
            return;
        }
        if (password.length() < MIN_CARACTERES
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_BCRYPT) {
            throw new IllegalStateException("ADMIN_PASSWORD debe tener al menos 8 caracteres y como máximo 72 bytes");
        }

        usuarioService.crearUsuario(tipoDocumento, numeroDocumento, email, password, Rol.ADMIN);
        log.info("Administrador inicial creado: {}. Quita ADMIN_PASSWORD del entorno y cambia la contraseña.", email);
    }
}
